import Foundation

/// One section of the provider (live, films or series), indexed once so
/// switching category or searching never walks the whole list on the main
/// thread.
nonisolated struct CatalogSection<Item: Identifiable & Codable & Sendable>: Codable, Sendable where Item.ID == Int {
    var categories: [Category] = []
    var items: [Item] = []
    /// Category id → indices into `items`.
    var index: [String: [Int]] = [:]

    init() {}

    init(categories: [Category], items: [Item], categoryOf: (Item) -> String?) {
        var index: [String: [Int]] = [:]
        for (i, item) in items.enumerated() {
            index[categoryOf(item) ?? "", default: []].append(i)
        }
        // Only categories with something in them, in the provider's order.
        self.categories = categories.filter { index[$0.id] != nil }
        self.items = items
        self.index = index
    }

    func items(in categoryID: String?) -> [Item] {
        guard let categoryID else { return items }
        return (index[categoryID] ?? []).map { items[$0] }
    }

    var isEmpty: Bool { items.isEmpty }
}

nonisolated struct CatalogSnapshot: Codable, Sendable {
    var live = CatalogSection<LiveChannel>()
    var movies = CatalogSection<Movie>()
    var series = CatalogSection<Series>()
    var date = Date.distantPast
    var account = ""
    /// A section failed to come: it's tried again at the next start.
    var isPartial: Bool?
}

/// Everything the provider offers, from the disk cache at once, then from
/// the server when it's older than a few hours.
@MainActor
@Observable
final class Catalog {
    private(set) var live = CatalogSection<LiveChannel>()
    private(set) var movies = CatalogSection<Movie>()
    private(set) var series = CatalogSection<Series>()
    private(set) var updated: Date?
    private(set) var isLoading = false
    /// While the disk cache is read, before anything is known.
    private(set) var isStarting = false
    private(set) var error: String?
    /// Channels with their streams put together.
    private(set) var guide = LiveGuide()
    /// Lowercased, accent-folded names, built once per refresh for search.
    private(set) var searchIndex = SearchIndex()
    /// Favorite key → film or series, to find favorites again.
    private(set) var moviesByKey: [String: Movie] = [:]
    private(set) var seriesByKey: [String: Series] = [:]
    /// Until the guide is built from what was just loaded.
    private(set) var isIndexing = false

    private var loadedAccount: String?
    private var snapshot = CatalogSnapshot()
    private var options = GuideOptions()
    /// The free channels' streams, for a subscription's channels that have
    /// one; none for the free channels' own account.
    private var freeChannels: [LiveChannel] = []
    private var usesFreeChannels = false

    private static let maxAge: TimeInterval = 6 * 3600

    /// One file per account: switching back to one is instant.
    nonisolated private static func cacheURL(_ account: String) -> URL {
        var hash: UInt64 = 1469598103934665603
        for byte in account.utf8 { hash = (hash ^ UInt64(byte)) &* 1099511628211 }
        return URL.cachesDirectory.appending(path: "catalog-\(String(hash, radix: 36)).json")
    }

    nonisolated static func accountKey(_ credentials: Credentials) -> String {
        credentials.baseURL + "|" + credentials.username
    }

    var isEmpty: Bool { live.isEmpty && movies.isEmpty && series.isEmpty }

    func start(_ client: XtreamClient) async {
        let account = Self.accountKey(client.credentials)
        guard loadedAccount != account else { return }
        loadedAccount = account
        usesFreeChannels = client.credentials.source != .free
        if usesFreeChannels, freeChannels.isEmpty {
            Task {
                freeChannels = await FreeChannels.listed()
                if usesFreeChannels, options.prefersFree, !freeChannels.isEmpty { apply(snapshot) }
            }
        }
        error = nil
        updateLoading()
        isStarting = true
        defer { if loadedAccount == account { isStarting = false } }
        // The one cache of earlier versions becomes this account's.
        let legacy = URL.cachesDirectory.appending(path: "catalog.json")
        if FileManager.default.fileExists(atPath: legacy.path()) {
            try? FileManager.default.moveItem(at: legacy, to: Self.cacheURL(account))
        }
        apply(CatalogSnapshot())
        let cached = await Self.readCache(account)
        guard loadedAccount == account else { return }
        if let cached, cached.account == account {
            apply(cached)
            // A playlist may have no films or series at all.
            let complete = cached.isPartial != true && (!client.isPanel || (!cached.live.isEmpty && !cached.movies.isEmpty && !cached.series.isEmpty))
            if Date().timeIntervalSince(cached.date) < Self.maxAge, complete { return }
        }
        await refresh(client)
    }

    func refresh(_ client: XtreamClient) async {
        let account = Self.accountKey(client.credentials)
        guard !refreshing.contains(account) else { return }
        refreshing.insert(account)
        updateLoading()
        error = nil
        defer {
            refreshing.remove(account)
            updateLoading()
        }
        // Live first, then films, then series: each shows up as soon as it
        // comes, so a slow list never holds the others back. A section that
        // doesn't come keeps what was there, if it's this account's.
        var snapshot = CatalogSnapshot()
        if account == loadedAccount { snapshot = self.snapshot }
        snapshot.account = account
        snapshot.isPartial = true
        var gotAny = false
        var failed = false

        func publish() {
            snapshot.date = Date()
            // Switched to another account meanwhile: kept for when it's back.
            if account == loadedAccount { apply(snapshot) }
            let saved = snapshot
            Task.detached(priority: .utility) { Self.writeCache(saved, account: account) }
        }

        let (lc, mc, sc) = await Self.categories(client)
        if let section = await Self.liveSection(client, lc) {
            snapshot.live = section
            gotAny = true
            publish()
        } else { failed = true }
        if let section = await Self.movieSection(client, mc) {
            snapshot.movies = section
            gotAny = true
            publish()
        } else { failed = true }
        if let section = await Self.seriesSection(client, sc) {
            snapshot.series = section
            gotAny = true
            publish()
        } else { failed = true }

        if gotAny {
            if !failed {
                snapshot.isPartial = nil
                publish()
            }
        } else {
            // Nothing at all: say why.
            do {
                _ = try await client.account()
                throw XtreamError.http(520)
            } catch {
                if isEmpty, account == loadedAccount { self.error = error.readableMessage }
            }
        }
    }

    private var refreshing: Set<String> = []

    private func updateLoading() {
        isLoading = loadedAccount.map(refreshing.contains) ?? false
    }

    /// Forgets an account's catalogue, on disk too.
    func forget(_ credentials: Credentials) {
        let account = Self.accountKey(credentials)
        if loadedAccount == account {
            loadedAccount = nil
            apply(CatalogSnapshot())
        }
        try? FileManager.default.removeItem(at: Self.cacheURL(account))
    }

    /// The settings that shape the guide changed: built again from what's there.
    func optionsChanged() {
        guard GuideOptions.current != options else { return }
        apply(snapshot)
    }

    private func apply(_ snapshot: CatalogSnapshot) {
        self.snapshot = snapshot
        options = GuideOptions.current
        let options = options
        live = snapshot.live
        movies = snapshot.movies
        series = snapshot.series
        updated = snapshot.date == .distantPast ? nil : snapshot.date
        let snap = snapshot
        let free = usesFreeChannels && options.prefersFree ? freeChannels : []
        isIndexing = !snapshot.live.isEmpty || !snapshot.movies.isEmpty || !snapshot.series.isEmpty
        indexing?.cancel()
        indexing = Task {
            let built = await Self.index(snap, options: options, free: free)
            guard !Task.isCancelled else { return }
            guide = built.guide
            searchIndex = built.search
            moviesByKey = built.movies
            seriesByKey = built.series
            isIndexing = false
        }
    }

    private var indexing: Task<Void, Never>?

    private struct Indexes: Sendable {
        var guide: LiveGuide
        var search: SearchIndex
        var movies: [String: Movie]
        var series: [String: Series]
    }

    @concurrent
    private static func index(_ snapshot: CatalogSnapshot, options: GuideOptions, free: [LiveChannel]) async -> Indexes {
        let guide = LiveGuide.build(snapshot.live, options: options, free: free)
        var movies: [String: Movie] = [:]
        for m in snapshot.movies.items { let k = Favorite.nameKey(m.name); if movies[k] == nil { movies[k] = m } }
        var series: [String: Series] = [:]
        for s in snapshot.series.items { let k = Favorite.nameKey(s.name); if series[k] == nil { series[k] = s } }
        return Indexes(guide: guide, search: SearchIndex.build(snapshot, guide: guide), movies: movies, series: series)
    }

    @concurrent
    private static func categories(_ client: XtreamClient) async -> ([Category]?, [Category]?, [Category]?) {
        // The categories are small and go together.
        async let live = client.categories(.live)
        async let movie = client.categories(.movie)
        async let series = client.categories(.series)
        return (try? await live, try? await movie, try? await series)
    }

    // The three big lists go one after the other, which panels behind
    // Cloudflare take better than a burst. An empty list is a failure too:
    // a refused request can come back as "[]" rather than an error. Not
    // from a playlist, which often has live channels only.

    @concurrent
    private static func liveSection(_ client: XtreamClient, _ categories: [Category]?) async -> CatalogSection<LiveChannel>? {
        guard let items = try? await client.channels(), !items.isEmpty || !client.isPanel else { return nil }
        return CatalogSection(categories: categories ?? [], items: items, categoryOf: \.categoryID)
    }

    @concurrent
    private static func movieSection(_ client: XtreamClient, _ categories: [Category]?) async -> CatalogSection<Movie>? {
        guard let items = try? await client.movies(), !items.isEmpty || !client.isPanel else { return nil }
        return CatalogSection(categories: categories ?? [], items: items, categoryOf: \.categoryID)
    }

    @concurrent
    private static func seriesSection(_ client: XtreamClient, _ categories: [Category]?) async -> CatalogSection<Series>? {
        guard let items = try? await client.series(), !items.isEmpty || !client.isPanel else { return nil }
        return CatalogSection(categories: categories ?? [], items: items, categoryOf: \.categoryID)
    }

    @concurrent
    private static func readCache(_ account: String) async -> CatalogSnapshot? {
        guard let data = try? Data(contentsOf: cacheURL(account), options: .mappedIfSafe) else { return nil }
        return try? JSONDecoder().decode(CatalogSnapshot.self, from: data)
    }

    nonisolated private static func writeCache(_ snapshot: CatalogSnapshot, account: String) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        try? data.write(to: cacheURL(account), options: .atomic)
    }
}

/// Names folded for matching ("Télé" finds "tele"), searched off the main
/// thread.
nonisolated struct SearchIndex: Sendable {
    struct Results: Sendable {
        var live: [ChannelGroup] = []
        var movies: [Movie] = []
        var series: [Series] = []
        var isEmpty: Bool { live.isEmpty && movies.isEmpty && series.isEmpty }
    }

    private var live: [(String, ChannelGroup)] = []
    private var movies: [(String, Movie)] = []
    private var series: [(String, Series)] = []

    static func fold(_ s: String) -> String {
        s.folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: nil)
    }

    static func build(_ snapshot: CatalogSnapshot, guide: LiveGuide) -> SearchIndex {
        var index = SearchIndex()
        index.live = guide.groups.map { (fold($0.name), $0) }
        index.movies = snapshot.movies.items.map { (fold($0.name), $0) }
        index.series = snapshot.series.items.map { (fold($0.name), $0) }
        return index
    }

    @concurrent
    func search(_ query: String, limit: Int = 60) async -> Results {
        let words = Self.fold(query).split(separator: " ").map(String.init)
        guard !words.isEmpty else { return Results() }
        func matches(_ name: String) -> Bool { words.allSatisfy { name.contains($0) } }
        var r = Results()
        for (name, item) in live where matches(name) { r.live.append(item); if r.live.count >= limit { break } }
        if Task.isCancelled { return r }
        for (name, item) in movies where matches(name) { r.movies.append(item); if r.movies.count >= limit { break } }
        if Task.isCancelled { return r }
        for (name, item) in series where matches(name) { r.series.append(item); if r.series.count >= limit { break } }
        return r
    }
}
