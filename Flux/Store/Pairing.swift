import Foundation
import Network
#if canImport(UIKit)
import UIKit
#endif

/// An account sent from one device to another over the local network (any
/// of iPhone, iPad, Mac, Apple TV and the Android apps, either way):
/// typing a password with a remote is a chore. The Apple TV shows a code
/// and advertises it; the iPhone lists the Apple TVs with their code, and
/// the account goes only to the one picked, which checks the code.
nonisolated enum Pairing {
    static let service = "_fluxpair._tcp"

    /// The account, and what goes with it: favorites, where films and
    /// episodes stopped, recent channels, and the settings that shape the
    /// lists.
    struct Message: Codable, Sendable {
        var code: String
        var account: Account
        var library: Library.Snapshot?
        var settings: [String: String]?
    }

    /// The settings taken along, by their UserDefaults key.
    @MainActor static let settingKeys = [
        QualityPreference.storageKey, ChannelNumbering.storageKey, GuideOptions.shiftedKey, GuideOptions.prefersFreeKey,
        FavoritesStyle.storageKey, FavoritesStyle.sharedKey,
    ]

    @MainActor static func currentSettings() -> [String: String] {
        var settings: [String: String] = [:]
        for key in settingKeys {
            if let value = UserDefaults.standard.object(forKey: key) { settings[key] = "\(value)" }
        }
        return settings
    }

    @MainActor static func apply(_ settings: [String: String]) {
        for (key, value) in settings where settingKeys.contains(key) {
            switch key {
            case GuideOptions.shiftedKey, GuideOptions.prefersFreeKey, FavoritesStyle.sharedKey: UserDefaults.standard.set(value == "1" || value == "true", forKey: key)
            default: UserDefaults.standard.set(value, forKey: key)
            }
        }
    }
}

extension Pairing {
    /// What this device is called to the iPhone looking for it.
    @MainActor static var deviceKind: String {
        #if os(tvOS)
        "Apple TV"
        #elseif targetEnvironment(macCatalyst)
        "Mac"
        #else
        UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "iPhone"
        #endif
    }

    /// A symbol for a device by its kind, as the list of devices shows it.
    static func symbol(_ kind: String) -> String {
        switch kind {
        case "Mac": "macbook"
        case "Apple TV": "appletv.fill"
        case "iPad": "ipad"
        case "iPhone": "iphone"
        case "Android TV": "tv"
        default: "smartphone"
        }
    }

    /// An account received: checked against the server like a typed one
    /// (or the one already here with that login), then what goes with it
    /// merged into what's here.
    @MainActor
    static func receive(_ message: Message, account: AccountStore, library: Library, catalog: Catalog) async throws {
        if let existing = account.accounts.first(where: { $0.credentials == message.account.credentials }) {
            account.select(existing.id)
        } else {
            try await account.add(Account(name: message.account.name, credentials: message.account.credentials))
        }
        if let settings = message.settings { apply(settings) }
        let shares = UserDefaults.standard.object(forKey: FavoritesStyle.sharedKey) as? Bool ?? true
        library.use(account: account.activeID, sharesFavorites: shares)
        if let snapshot = message.library { library.merge(snapshot) }
        catalog.optionsChanged()
    }
}

/// The Apple TV's (or Mac's) side: waits for an account while the sign-in
/// screen is up.
@MainActor
@Observable
final class PairingReceiver {
    let code = String(format: "%04d", Int.random(in: 0...9999))
    private var listener: NWListener?
    /// What arrived with the right code.
    var onMessage: ((Pairing.Message) -> Void)?

    func start() {
        guard listener == nil, let listener = try? NWListener(using: .tcp) else { return }
        listener.service = NWListener.Service(name: "\(Pairing.deviceKind) · \(code)", type: Pairing.service,
                                               txtRecord: NWTXTRecord(["code": code, "kind": Pairing.deviceKind]))
        listener.newConnectionHandler = { [weak self] connection in
            Task { @MainActor in self?.receive(connection) }
        }
        listener.start(queue: .main)
        self.listener = listener
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }

    private func receive(_ connection: NWConnection) {
        connection.start(queue: .main)
        connection.receiveMessage { [weak self] data, _, _, _ in
            let message = data.flatMap { try? JSONDecoder().decode(Pairing.Message.self, from: $0) }
            Task { @MainActor in
                connection.cancel()
                guard let self, let message, message.code == self.code else { return }
                self.onMessage?(message)
            }
        }
    }
}

/// The iPhone's side: the Apple TVs waiting for an account, nearby.
@MainActor
@Observable
final class PairingSender {
    struct Screen: Identifiable, Hashable {
        var id: String { name }
        let name: String
        let code: String
        /// "Apple TV" or "Mac".
        let kind: String
        let endpoint: NWEndpoint
    }

    private(set) var screens: [Screen] = []
    /// The code of the Apple TV the account last went to.
    private(set) var sent: String?
    /// The code being sent to, and the one that couldn't be reached.
    private(set) var sending: String?
    private(set) var failed: String?
    private var browser: NWBrowser?

    func start() {
        guard browser == nil else { return }
        let browser = NWBrowser(for: .bonjourWithTXTRecord(type: Pairing.service, domain: nil), using: .tcp)
        browser.browseResultsChangedHandler = { [weak self] results, _ in
            let screens = results.compactMap { result -> Screen? in
                guard case .service(let name, _, _, _) = result.endpoint,
                      case .bonjour(let txt) = result.metadata, let code = txt["code"] else { return nil }
                return Screen(name: name, code: code, kind: txt["kind"] ?? "Apple TV", endpoint: result.endpoint)
            }
            Task { @MainActor in self?.screens = screens.sorted { $0.name < $1.name } }
        }
        browser.start(queue: .main)
        self.browser = browser
    }

    func stop() {
        browser?.cancel()
        browser = nil
    }

    func send(_ account: Account, library: Library.Snapshot, to screen: Screen) {
        let message = Pairing.Message(code: screen.code, account: account, library: library, settings: Pairing.currentSettings())
        guard let data = try? JSONEncoder().encode(message) else { return }
        let connection = NWConnection(to: screen.endpoint, using: .tcp)
        sending = screen.code
        failed = nil
        var done = false
        let finish: @MainActor (Bool) -> Void = { [weak self] ok in
            guard !done else { return }
            done = true
            connection.cancel()
            self?.sending = nil
            if ok { self?.sent = screen.code } else { self?.failed = screen.code }
        }
        connection.stateUpdateHandler = { state in
            switch state {
            case .failed, .waiting: Task { @MainActor in finish(false) }
            default: break
            }
        }
        connection.start(queue: .main)
        connection.send(content: data, contentContext: .finalMessage, isComplete: true, completion: .contentProcessed { error in
            Task { @MainActor in finish(error == nil) }
        })
        // An unreachable device would otherwise keep the send waiting forever.
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(8))
            finish(false)
        }
    }
}
