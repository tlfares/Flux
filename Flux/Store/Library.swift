import Foundation

/// Something kept in favorites, by what it is rather than by the
/// provider's id: "TF1" stays a favorite after a refresh renumbers the
/// streams, or on another provider.
nonisolated struct Favorite: Codable, Hashable, Sendable, Identifiable {
    var kind: MediaKind
    var key: String
    var name: String
    var image: String?

    var id: String { "\(kind.rawValue):\(key)" }

    static func live(_ group: ChannelGroup) -> Favorite {
        Favorite(kind: .live, key: group.id, name: group.name, image: group.icon)
    }

    static func movie(_ movie: Movie) -> Favorite {
        Favorite(kind: .movie, key: nameKey(movie.name), name: DisplayName.clean(movie.name), image: movie.poster)
    }

    static func series(_ series: Series) -> Favorite {
        Favorite(kind: .series, key: nameKey(series.name), name: DisplayName.clean(series.name), image: series.cover)
    }

    /// Folded and without the country tag.
    static func nameKey(_ name: String) -> String {
        SearchIndex.fold(DisplayName.clean(name))
    }
}

/// How favorites show in the lists.
enum FavoritesStyle: String, CaseIterable {
    /// A "Favoris" category of their own.
    case separate
    /// At the top of every list they belong to.
    case pinned

    var title: String {
        switch self {
        case .separate: "Liste à part"
        case .pinned: "Épinglés"
        }
    }

    var symbol: String {
        switch self {
        case .separate: "star.square.on.square"
        case .pinned: "pin"
        }
    }

    static let storageKey = "favorites.style"
    /// One list for every account, or one each.
    static let sharedKey = "favorites.keep"
}

/// Favorites, what was last watched and where each film stopped. Small,
/// so UserDefaults is enough.
@MainActor
@Observable
final class Library {
    /// In the order they were added.
    private(set) var favorites: [Favorite]
    private var favoriteIDs: Set<String>
    /// Seconds watched and total, by stream key.
    private(set) var progress: [String: Progress]
    /// Channel group keys, most recent first.
    private(set) var recentChannels: [String]
    /// Channel group key → the quality last picked for it.
    private(set) var qualityChoices: [String: String]

    nonisolated struct Progress: Codable, Sendable {
        var position: Double
        var duration: Double
        var date: Date
        var fraction: Double { duration > 0 ? min(1, position / duration) : 0 }
        /// Started and not finished: what "Reprendre" offers.
        var isResumable: Bool { position > 30 && fraction < 0.95 }
    }

    private let defaults = UserDefaults.standard
    private var account: UUID?
    private var sharesFavorites = true

    init() {
        favorites = []
        favoriteIDs = []
        progress = [:]
        recentChannels = []
        qualityChoices = (defaults.dictionary(forKey: "qualityChoices") as? [String: String]) ?? [:]
        // Kept by stream id before favorites went by name.
        for key in ["favorites", "recentChannels"] { defaults.removeObject(forKey: key) }
    }

    private var favoritesKey: String {
        sharesFavorites ? "favorites.v2" : "favorites.v2.\(account?.uuidString ?? "")"
    }

    private var progressKey: String { "progress.\(account?.uuidString ?? "")" }
    private var recentKey: String { "recentLive.\(account?.uuidString ?? "")" }

    /// Loads what belongs to the account in use. Favorites are one list for
    /// every account when shared, else one each.
    func use(account: UUID?, sharesFavorites: Bool) {
        guard account != self.account || sharesFavorites != self.sharesFavorites else { return }
        self.account = account
        self.sharesFavorites = sharesFavorites
        guard account != nil else {
            favorites = []; favoriteIDs = []; progress = [:]; recentChannels = []
            return
        }
        // From before accounts: the first one to load takes them.
        for (old, new) in [("progress", progressKey), ("recentLive", recentKey)] {
            guard let value = defaults.object(forKey: old) else { continue }
            if defaults.object(forKey: new) == nil { defaults.set(value, forKey: new) }
            defaults.removeObject(forKey: old)
        }
        // An account's own list starts as the shared one.
        if !sharesFavorites, defaults.object(forKey: favoritesKey) == nil, let shared = defaults.data(forKey: "favorites.v2") {
            defaults.set(shared, forKey: favoritesKey)
        }
        let favorites = Self.decode([Favorite].self, favoritesKey) ?? []
        self.favorites = favorites
        favoriteIDs = Set(favorites.map(\.id))
        progress = Self.decode([String: Progress].self, progressKey) ?? [:]
        recentChannels = defaults.stringArray(forKey: recentKey) ?? []
    }

    /// An account removed: what was its alone goes with it.
    func forget(account id: UUID) {
        for key in ["favorites.v2.\(id.uuidString)", "progress.\(id.uuidString)", "recentLive.\(id.uuidString)"] {
            defaults.removeObject(forKey: key)
        }
    }

    /// The key progress is saved under: streams, which belong to one provider.
    static func key(_ kind: MediaKind, _ id: some CustomStringConvertible) -> String { "\(kind.rawValue):\(id)" }

    // MARK: Favorites

    func isFavorite(_ favorite: Favorite) -> Bool { favoriteIDs.contains(favorite.id) }
    func isFavorite(_ kind: MediaKind, key: String) -> Bool { favoriteIDs.contains("\(kind.rawValue):\(key)") }

    func favorites(_ kind: MediaKind) -> [Favorite] { favorites.filter { $0.kind == kind } }

    func toggleFavorite(_ favorite: Favorite) {
        if favoriteIDs.contains(favorite.id) {
            favorites.removeAll { $0.id == favorite.id }
            favoriteIDs.remove(favorite.id)
        } else {
            favorites.append(favorite)
            favoriteIDs.insert(favorite.id)
        }
        saveFavorites()
    }

    func moveFavorites(_ kind: MediaKind, from source: IndexSet, to destination: Int) {
        var ofKind = favorites(kind)
        ofKind.move(fromOffsets: source, toOffset: destination)
        favorites = favorites.filter { $0.kind != kind } + ofKind
        saveFavorites()
    }

    func removeFavorites(_ kind: MediaKind, at offsets: IndexSet) {
        let gone = Set(offsets.map { favorites(kind)[$0].id })
        favorites.removeAll { gone.contains($0.id) }
        favoriteIDs.subtract(gone)
        saveFavorites()
    }

    private func saveFavorites() {
        if let data = try? JSONEncoder().encode(favorites) { defaults.set(data, forKey: favoritesKey) }
    }

    // MARK: Progress

    func saveProgress(_ key: String, position: Double, duration: Double) {
        guard duration.isFinite, duration > 0, position.isFinite else { return }
        progress[key] = Progress(position: position, duration: duration, date: Date())
        // The 200 most recent are plenty.
        if progress.count > 200 {
            for (k, _) in progress.sorted(by: { $0.value.date > $1.value.date }).dropFirst(200) { progress[k] = nil }
        }
        if let data = try? JSONEncoder().encode(progress) { defaults.set(data, forKey: progressKey) }
    }

    func resumePosition(_ key: String) -> Double? {
        guard let p = progress[key], p.isResumable else { return nil }
        return p.position
    }

    // MARK: Live

    func played(_ group: ChannelGroup) {
        recentChannels.removeAll { $0 == group.id }
        recentChannels.insert(group.id, at: 0)
        recentChannels = Array(recentChannels.prefix(30))
        defaults.set(recentChannels, forKey: recentKey)
    }

    func rememberQuality(_ label: String?, for group: ChannelGroup) {
        guard qualityChoices[group.id] != label else { return }
        qualityChoices[group.id] = label
        defaults.set(qualityChoices, forKey: "qualityChoices")
    }

    // MARK: Pairing

    /// What another device takes along when the account is sent to it.
    nonisolated struct Snapshot: Codable, Sendable {
        var favorites: [Favorite]
        var progress: [String: Progress]
        var recentChannels: [String]
        var qualityChoices: [String: String]
    }

    func snapshot() -> Snapshot {
        Snapshot(favorites: favorites, progress: progress, recentChannels: recentChannels, qualityChoices: qualityChoices)
    }

    /// Adds what came from another device to what's here, for the account
    /// in use: favorites missing here go at the end, each film keeps the
    /// position watched last, recents come first.
    func merge(_ other: Snapshot) {
        for favorite in other.favorites where !favoriteIDs.contains(favorite.id) {
            favorites.append(favorite)
            favoriteIDs.insert(favorite.id)
        }
        saveFavorites()

        for (key, p) in other.progress where progress[key].map({ p.date > $0.date }) ?? true { progress[key] = p }
        if let data = try? JSONEncoder().encode(progress) { defaults.set(data, forKey: progressKey) }

        var recents = other.recentChannels
        for key in recentChannels where !recents.contains(key) { recents.append(key) }
        recentChannels = Array(recents.prefix(30))
        defaults.set(recentChannels, forKey: recentKey)

        qualityChoices.merge(other.qualityChoices) { _, theirs in theirs }
        defaults.set(qualityChoices, forKey: "qualityChoices")
    }

    private static func decode<T: Decodable>(_ type: T.Type, _ key: String) -> T? {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(T.self, from: $0) }
    }
}
