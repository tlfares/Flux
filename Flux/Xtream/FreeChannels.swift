import Foundation

/// The channels anyone can watch without a subscription, from the
/// broadcasters' own players: in France the free TNT channels France TV,
/// Arte, BFM, RMC and L'Équipe stream, news and the France 3 regions, the
/// American and British news and free channels, then Pluto TV's line-up
/// for where the device is. The list comes with the app (FreeChannels.json); streams
/// whose address changes are looked up when they start (`StreamResolver`).
nonisolated enum FreeChannels {
    private struct File: Decodable {
        struct Item: Decodable {
            var name: String
            var category: String
            var url: String
            var logo: String?
        }

        var version: Int
        var categories: [Category]
        var channels: [Item]

        private enum Keys: String, CodingKey { case version, categories, channels }
        private struct Cat: Decodable { var id: String; var name: String; var country: String? }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: Keys.self)
            version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 0
            categories = try c.decode([Cat].self, forKey: .categories).map {
                // Named with their country, as panels do: "|US| News".
                Category(id: $0.id, name: "|\($0.country ?? "FR")| " + FreeChannels.named(Category(id: $0.id, name: $0.name)))
            }
            channels = try c.decode([Item].self, forKey: .channels)
        }

        /// A list worth using: read whole, and not emptied by mistake.
        static func read(_ data: Data) -> File? {
            guard let file = try? JSONDecoder().decode(File.self, from: data), file.channels.count >= 10 else { return nil }
            return file
        }
    }

    /// The same file, hosted: streams that move are fixed there, without a
    /// new version of the app. Asked for at most every few hours; the copy
    /// that came with the app stands in until one arrives, and whenever it's
    /// older or unreadable.
    static let remote = URL(string: "https://gist.githubusercontent.com/tlfares/b08fcc5378a0b04d14d0f15dfea7cf77/raw/FreeChannels2.json")!
    private static let remoteMaxAge: TimeInterval = 6 * 3600
    private static var remoteFile: URL { URL.cachesDirectory.appending(path: "free-channels.json") }

    private static func bundled() -> File? {
        Bundle.main.url(forResource: "FreeChannels", withExtension: "json").flatMap { try? Data(contentsOf: $0) }.flatMap(File.read)
    }

    /// The hosted list when it's at least as recent as the bundled one.
    private static func current() async -> File? {
        let age = (try? remoteFile.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate).map { Date().timeIntervalSince($0) }
        if age.map({ $0 > remoteMaxAge }) ?? true {
            var request = URLRequest(url: remote, timeoutInterval: 8)
            request.cachePolicy = .reloadIgnoringLocalCacheData
            if let (data, response) = try? await StreamResolver.session.data(for: request),
               (response as? HTTPURLResponse)?.statusCode == 200, File.read(data) != nil {
                try? data.write(to: remoteFile, options: .atomic)
            }
        }
        let local = bundled()
        guard let data = try? Data(contentsOf: remoteFile), let hosted = File.read(data),
              hosted.version >= (local?.version ?? 0) else { return local }
        return hosted
    }

    /// The listed channels alone, to stand in for a provider's.
    static func listed() async -> [LiveChannel] {
        (await current())?.channels.map {
            LiveChannel(id: Playlist.stableID($0.url), name: $0.name, icon: $0.logo, categoryID: $0.category, url: $0.url)
        } ?? []
    }

    /// Every country's channels, then Pluto TV's when it answers: which
    /// ones show is chosen when they're shown (`shown`), so changing it
    /// needs nothing loaded again.
    static func playlist() async -> Playlist {
        var playlist = Playlist()
        async let listed = current()
        async let pluto = try? PlutoTV.shared.lineup()
        if let file = await listed {
            playlist.liveCategories = file.categories
            playlist.live = file.channels.map {
                LiveChannel(id: Playlist.stableID($0.url), name: $0.name, icon: $0.logo, categoryID: $0.category, url: $0.url)
            }
        }
        if let pluto = await pluto {
            playlist.liveCategories += pluto.categories
            playlist.live += pluto.channels
        }
        return playlist
    }

    /// The free account's channels as the setting has them: one country's
    /// alone, or every country's with the device's first and a flag on each
    /// category.
    static func shown(_ section: CatalogSection<LiveChannel>, scope: FreeScope) -> CatalogSection<LiveChannel> {
        let tagged = section.categories.map { (category: $0, country: ChannelName.splitCountry(Substring($0.name)).country) }
        if let only = scope.country, tagged.contains(where: { $0.country == only }) {
            let kept = tagged.filter { $0.country == only }.map(\.category)
            let ids = Set(kept.map(\.id))
            return CatalogSection(categories: kept, items: section.items.filter { $0.categoryID.map(ids.contains) ?? false }, categoryOf: \.categoryID)
        }
        let home = FreeScope.detected.country ?? "US"
        let ordered = tagged.filter { $0.country == home } + tagged.filter { $0.country != home }
        var result = section
        result.categories = ordered.map { item in
            guard let flag = FreeScope.flag(item.country) else { return item.category }
            let rest = ChannelName.splitCountry(Substring(item.category.name)).rest.drop { $0 == " " }
            return Category(id: item.category.id, name: "|\(item.country)| \(flag) \(rest)")
        }
        return result
    }

    /// The list's own categories in the device's language; new ones keep
    /// the list's name.
    fileprivate static func named(_ category: Category) -> String {
        switch category.id {
        case "info": String(localized: "Info")
        case "regions": String(localized: "France 3 Régions")
        case "bfm": String(localized: "BFM Régions")
        case "local": String(localized: "Chaînes locales")
        case "music": String(localized: "Musique")
        case "fast": String(localized: "Films & séries")
        case "sport": String(localized: "Sport")
        case "outremer": String(localized: "Outre-mer")
        case "jeunesse": String(localized: "Jeunesse")
        default: category.name
        }
    }
}

/// Which free channels show: one country's, or every country's.
nonisolated enum FreeScope: String, CaseIterable, Sendable {
    case fr
    case us
    case uk
    case all

    static let storageKey = "free.scope"

    var title: String {
        switch self {
        case .fr: String(localized: "France")
        case .us: "US"
        case .uk: "UK"
        case .all: String(localized: "Tous")
        }
    }

    /// Above the title in the setting.
    var emoji: String {
        switch self {
        case .fr: "🇫🇷"
        case .us: "🇺🇸"
        case .uk: "🇬🇧"
        case .all: "🌍"
        }
    }

    /// The tag the list puts on this country's categories.
    var country: String? {
        switch self {
        case .fr: "FR"
        case .us: "US"
        case .uk: "UK"
        case .all: nil
        }
    }

    /// The device's country when the list has it, the United States' otherwise.
    static var detected: FreeScope {
        switch Locale.current.region?.identifier.uppercased() {
        case "FR": .fr
        case "GB": .uk
        default: .us
        }
    }

    /// "🇺🇸" for "US".
    static func flag(_ country: String) -> String? {
        let code = country == "UK" ? "GB" : country
        guard code.count == 2, code.allSatisfy({ $0.isASCII && $0.isLetter }) else { return nil }
        return String(String.UnicodeScalarView(code.uppercased().unicodeScalars.compactMap { Unicode.Scalar($0.value + 0x1F1A5) }))
    }
}

/// Turns the free channels' lookups into an address AVPlayer plays:
/// `francetv:<id>` (a signed address, valid for a few hours),
/// `dailymotion:<id>` and `pluto:<id>`. Anything else is an address already.
nonisolated enum StreamResolver {
    static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.httpAdditionalHeaders = ["User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"]
        return URLSession(configuration: config)
    }()

    @concurrent
    static func resolve(_ link: String) async -> URL? {
        guard let colon = link.firstIndex(of: ":") else { return URL(string: link) }
        let id = String(link[link.index(after: colon)...])
        switch link[..<colon] {
        case "francetv": return await franceTV(id)
        case "dailymotion": return await dailymotion(id)
        case "pluto": return await PlutoTV.shared.streamURL(id)
        default: return URL(string: link)
        }
    }

    private static func json(_ url: URL) async -> [String: Any]? {
        guard let (data, response) = try? await session.data(from: url),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    /// The player's own two steps: the stream's address, then a signature
    /// for it.
    private static func franceTV(_ id: String) async -> URL? {
        var components = URLComponents(string: "https://k7.ftven.fr/videos/\(id)")!
        components.queryItems = [
            URLQueryItem(name: "country_code", value: "FR"), URLQueryItem(name: "domain", value: "www.france.tv"),
            URLQueryItem(name: "device_type", value: "desktop"), URLQueryItem(name: "browser", value: "safari"),
            URLQueryItem(name: "os", value: "macos"), URLQueryItem(name: "diffusion_mode", value: "tunnel_first"),
            URLQueryItem(name: "offline", value: "false"),
        ]
        guard let url = components.url, let video = await json(url)?["video"] as? [String: Any],
              let stream = video["url"] as? String else { return nil }
        let token = (video["token"] as? [String: Any])?["akamai"] as? String ?? video["token"] as? String
        guard let token, var signing = URLComponents(string: token) else { return URL(string: stream) }
        signing.queryItems = (signing.queryItems ?? []) + [URLQueryItem(name: "url", value: stream)]
        // "+" in the address must reach the server encoded.
        signing.percentEncodedQuery = signing.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        guard let signURL = signing.url, let signed = await json(signURL)?["url"] as? String else { return URL(string: stream) }
        return URL(string: signed)
    }

    /// Dailymotion's metadata has raw control characters JSONSerialization
    /// refuses: the address is read straight out of it.
    private static func dailymotion(_ id: String) async -> URL? {
        let embedder = "https://www.dailymotion.com/video/\(id)".addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        guard let url = URL(string: "https://www.dailymotion.com/player/metadata/video/\(id)?embedder=\(embedder)&locale=fr&app=com.dailymotion.neon&client_type=webapp"),
              let (data, _) = try? await session.data(from: url) else { return nil }
        let text = String(decoding: data, as: UTF8.self)
        guard let auto = text.range(of: "\"auto\""),
              let match = text[auto.upperBound...].firstMatch(of: /"url"\s*:\s*"([^"]+)"/) else { return nil }
        return URL(string: String(match.1).replacingOccurrences(of: "\\/", with: "/"))
    }
}

/// Pluto TV, free and supported by ads: its French channels, their guide,
/// and a session to play them with, renewed when Pluto says so.
actor PlutoTV {
    static let shared = PlutoTV()

    private struct Session {
        var token: String
        var stitcher: String
        var params: String
        var expires: Date
        /// Where Pluto places the device: its line-up is that country's.
        var region: String
    }

    private var session: Session?
    private let clientID = UUID().uuidString.lowercased()

    private func current() async throws -> Session {
        if let session, session.expires > Date() { return session }
        var components = URLComponents(string: "https://boot.pluto.tv/v4/start")!
        components.queryItems = [
            "appName": "web", "appVersion": "8.0.0", "deviceVersion": "122.0.0", "deviceModel": "web",
            "deviceMake": "chrome", "deviceType": "web", "clientID": clientID, "clientModelNumber": "1.0.0",
            "serverSideAds": "false",
        ].map { URLQueryItem(name: $0.key, value: $0.value) }
        let (data, _) = try await StreamResolver.session.data(from: components.url!)
        guard let boot = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let token = boot["sessionToken"] as? String,
              let stitcher = (boot["servers"] as? [String: Any])?["stitcher"] as? String,
              let params = boot["stitcherParams"] as? String else { throw URLError(.badServerResponse) }
        let refresh = (boot["refreshInSec"] as? Double) ?? 3600
        let region = ((boot["session"] as? [String: Any])?["activeRegion"] as? String)?.uppercased() ?? "FR"
        let session = Session(token: token, stitcher: stitcher, params: params, expires: Date().addingTimeInterval(min(refresh, 6 * 3600) - 60),
                              region: region == "GB" ? "UK" : region)
        self.session = session
        return session
    }

    private func get(_ path: String) async throws -> Any {
        let session = try await current()
        var request = URLRequest(url: URL(string: "https://service-channels.clusters.pluto.tv" + path)!)
        request.setValue("Bearer \(session.token)", forHTTPHeaderField: "Authorization")
        let (data, _) = try await StreamResolver.session.data(for: request)
        return try JSONSerialization.jsonObject(with: data)
    }

    /// The channels, each in the first category Pluto lists it under.
    func lineup() async throws -> (categories: [Category], channels: [LiveChannel]) {
        let channels = ((try await get("/v2/guide/channels?channelIds=&offset=0&limit=1000&sort=number%3Aasc")) as? [String: Any])?["data"] as? [[String: Any]] ?? []
        let groups = ((try? await get("/v2/guide/categories")) as? [String: Any])?["data"] as? [[String: Any]] ?? []
        // Its categories carry the country of its line-up.
        let tag = "|\(try await current().region)| "
        var categoryOf: [String: String] = [:]
        var categories: [Category] = []
        for group in groups {
            guard let id = group["id"] as? String, let name = group["name"] as? String,
                  let ids = group["channelIDs"] as? [String] else { continue }
            // "Nouveau sur Pluto TV", "Halloween sur Pluto TV": channels
            // found again in their own category.
            if name.localizedCaseInsensitiveContains("pluto") { continue }
            var used = false
            for channel in ids where categoryOf[channel] == nil {
                categoryOf[channel] = "pluto|" + id
                used = true
            }
            if used { categories.append(Category(id: "pluto|" + id, name: tag + "Pluto TV · " + name)) }
        }
        let fallback = Category(id: "pluto", name: tag + "Pluto TV")
        var usesFallback = false
        let list: [LiveChannel] = channels.compactMap { channel in
            guard let id = channel["id"] as? String, let name = channel["name"] as? String else { return nil }
            let images = channel["images"] as? [[String: Any]] ?? []
            let logo = (images.first { $0["type"] as? String == "colorLogoPNG" } ?? images.first)?["url"] as? String
            let category = categoryOf[id] ?? { usesFallback = true; return fallback.id }()
            return LiveChannel(id: Playlist.stableID("pluto:" + id), name: name, icon: logo, categoryID: category, url: "pluto:" + id)
        }
        return (usesFallback ? categories + [fallback] : categories, list)
    }

    func streamURL(_ channel: String) async -> URL? {
        guard let session = try? await current() else { return nil }
        return URL(string: "\(session.stitcher)/v2/stitch/hls/channel/\(channel)/master.m3u8?\(session.params)&jwt=\(session.token)&masterJWTPassthrough=true")
    }

    /// What's on now and next, from Pluto's guide.
    func programmes(_ channel: String) async -> [Programme] {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let start = formatter.string(from: Date().addingTimeInterval(-3600))
        guard let root = (try? await get("/v2/guide/timelines?start=\(start)&channelIds=\(channel)&duration=480")) as? [String: Any],
              let timelines = (root["data"] as? [[String: Any]])?.first?["timelines"] as? [[String: Any]] else { return [] }
        return timelines.compactMap { item in
            guard let title = item["title"] as? String,
                  let start = (item["start"] as? String).flatMap(formatter.date),
                  let end = (item["stop"] as? String).flatMap(formatter.date) else { return nil }
            let episode = item["episode"] as? [String: Any]
            let image = (episode?["thumbnail"] as? [String: Any])?["path"] as? String
            return Programme(title: title, summary: (episode?["description"] as? String)?.nonEmpty, start: start, end: end, image: image)
        }
    }
}
