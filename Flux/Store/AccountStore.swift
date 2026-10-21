import Foundation
import Security

/// A provider subscription, under a name the user gives it.
nonisolated struct Account: Codable, Hashable, Sendable, Identifiable {
    var id = UUID()
    var name: String
    var credentials: Credentials

    /// The name, else the server's host.
    var title: String { name.nonEmpty ?? (credentials.source == .free ? "Chaînes gratuites" : host) }
    var host: String { URL(string: credentials.baseURL)?.host() ?? credentials.server }

    /// What it is, under its name: the login, or the kind of list.
    var subtitle: String {
        switch credentials.source {
        case .xtream: "\(credentials.username) · \(host)"
        case .playlist: "Liste M3U · \(host)"
        case .free: "TNT, info et Pluto TV, sans abonnement"
        }
    }

    static let free = Account(name: "Chaînes gratuites", credentials: .free)
}

/// The accounts, and the one in use. Logins live in the Keychain, never in
/// UserDefaults; only which account is in use does.
@MainActor
@Observable
final class AccountStore {
    private(set) var accounts: [Account]
    private(set) var activeID: UUID?
    private(set) var info: AccountInfo?

    private static let service = "com.fares.flux.account"
    private static let activeKey = "account.active"

    init() {
        var accounts = Self.read("accounts", [Account].self) ?? []
        // A single login from before accounts had names.
        if accounts.isEmpty, let legacy = Self.read("xtream", Credentials.self) {
            accounts = [Account(name: "", credentials: legacy)]
            Self.write("accounts", accounts)
            Self.write("xtream", Optional<Credentials>.none)
        }
        self.accounts = accounts
        let saved = UserDefaults.standard.string(forKey: Self.activeKey).flatMap(UUID.init)
        activeID = accounts.first { $0.id == saved }?.id ?? accounts.first?.id
    }

    var active: Account? { accounts.first { $0.id == activeID } }
    var credentials: Credentials? { active?.credentials }
    var client: XtreamClient? { credentials.map(XtreamClient.init) }

    /// The free channels, added once: nothing to check.
    func addFree() {
        if let existing = accounts.first(where: { $0.credentials.source == .free }) {
            select(existing.id)
            return
        }
        accounts.append(.free)
        save()
        info = AccountInfo()
        select(Account.free.id)
    }

    /// Checked against the server before it's kept; becomes the one in use.
    func add(_ account: Account) async throws {
        if account.credentials.source == .free { addFree(); return }
        let info = try await XtreamClient(credentials: account.credentials).account()
        accounts.append(account)
        save()
        self.info = info
        select(account.id)
    }

    /// A new name needs no check; a new login does.
    func update(_ account: Account) async throws {
        guard let i = accounts.firstIndex(where: { $0.id == account.id }) else { return }
        let loginChanged = accounts[i].credentials != account.credentials
        if loginChanged {
            let info = try await XtreamClient(credentials: account.credentials).account()
            if account.id == activeID { self.info = info }
        }
        accounts[i] = account
        save()
    }

    func select(_ id: UUID) {
        guard id != activeID || info == nil else { return }
        if id != activeID { info = nil }
        activeID = id
        UserDefaults.standard.set(id.uuidString, forKey: Self.activeKey)
    }

    func remove(_ id: UUID) {
        accounts.removeAll { $0.id == id }
        save()
        if activeID == id {
            info = nil
            activeID = nil
            if let next = accounts.first { select(next.id) } else { UserDefaults.standard.removeObject(forKey: Self.activeKey) }
        }
    }

    func refreshInfo() async {
        guard let client else { return }
        let id = activeID
        let info = try? await client.account()
        if id == activeID { self.info = info }
    }

    private func save() { Self.write("accounts", accounts) }

    // MARK: Keychain

    private static func query(_ item: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: item]
    }

    private static func read<T: Decodable>(_ item: String, _ type: T.Type) -> T? {
        var q = query(item)
        q[kSecReturnData as String] = true
        var result: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private static func write<T: Encodable>(_ item: String, _ value: T?) {
        SecItemDelete(query(item) as CFDictionary)
        guard let value, let data = try? JSONEncoder().encode(value) else { return }
        var q = query(item)
        q[kSecValueData as String] = data
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(q as CFDictionary, nil)
    }
}
