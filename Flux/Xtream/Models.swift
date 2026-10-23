import Foundation

/// Where an account's channels come from.
nonisolated enum SourceKind: String, Codable, Hashable, Sendable {
    /// An Xtream Codes panel: a server and a login.
    case xtream
    /// An M3U playlist, at the address in `server`.
    case playlist
    /// The channels anyone can watch for free, built into the app.
    case free
}

/// What a provider gives: a server and a login. Kept whole in the Keychain.
nonisolated struct Credentials: Codable, Hashable, Sendable {
    var server: String
    var username: String
    var password: String
    /// Missing on accounts from before playlists: those are Xtream.
    var kind: SourceKind?

    init(server: String, username: String, password: String, kind: SourceKind? = nil) {
        self.server = server
        self.username = username
        self.password = password
        self.kind = kind
    }

    var source: SourceKind { kind ?? .xtream }

    /// The free channels: nothing to type.
    static let free = Credentials(server: "flux://free", username: "", password: "", kind: .free)

    /// An M3U link from an Xtream panel (`get.php?username=…&password=…`)
    /// is better used as the panel itself: films, series and the guide come
    /// with it.
    static func playlist(_ link: String) -> Credentials {
        let trimmed = link.trimmingCharacters(in: .whitespacesAndNewlines)
        if let url = URLComponents(string: trimmed), url.path.hasSuffix("/get.php"),
           let user = url.queryItems?.first(where: { $0.name == "username" })?.value?.nonEmpty,
           let pass = url.queryItems?.first(where: { $0.name == "password" })?.value?.nonEmpty,
           let scheme = url.scheme, let host = url.host {
            let port = url.port.map { ":\($0)" } ?? ""
            let path = url.path.dropLast("/get.php".count)
            return Credentials(server: "\(scheme)://\(host)\(port)\(path)", username: user, password: pass)
        }
        return Credentials(server: trimmed, username: "", password: "", kind: .playlist)
    }

    /// `http://host:port`, whatever was typed: a missing scheme, a trailing
    /// slash or a pasted `player_api.php` link. A playlist's whole address.
    var baseURL: String {
        var s = server.trimmingCharacters(in: .whitespacesAndNewlines)
        guard source == .xtream else {
            if source == .playlist, !s.contains("://") { s = "http://" + s }
            return s
        }
        if let range = s.range(of: "/player_api.php") { s = String(s[..<range.lowerBound]) }
        while s.hasSuffix("/") { s.removeLast() }
        if !s.lowercased().hasPrefix("http://") && !s.lowercased().hasPrefix("https://") { s = "http://" + s }
        return s
    }
}

// Providers send ids and numbers as strings or ints, sometimes null, from
// one row to the next: every field is read leniently.
nonisolated extension KeyedDecodingContainer {
    func string(_ key: Key) -> String? {
        if let s = try? decodeIfPresent(String.self, forKey: key) { return s }
        if let i = try? decodeIfPresent(Int.self, forKey: key) { return String(i) }
        if let d = try? decodeIfPresent(Double.self, forKey: key) { return String(d) }
        return nil
    }

    func int(_ key: Key) -> Int? {
        if let i = try? decodeIfPresent(Int.self, forKey: key) { return i }
        if let s = string(key) { return Int(s) ?? Double(s).map { Int($0) } }
        return nil
    }

    func double(_ key: Key) -> Double? {
        if let d = try? decodeIfPresent(Double.self, forKey: key) { return d }
        return string(key).flatMap(Double.init)
    }

    func strings(_ key: Key) -> [String] {
        if let a = try? decodeIfPresent([String].self, forKey: key) { return a }
        if let s = string(key), !s.isEmpty { return [s] }
        return []
    }
}

nonisolated extension String {
    /// Empty and whitespace-only strings as nil.
    var nonEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}

nonisolated enum MediaKind: String, Codable, Sendable, CaseIterable {
    case live, movie, series
}

nonisolated struct Category: Codable, Hashable, Sendable, Identifiable {
    var id: String
    var name: String

    enum CodingKeys: String, CodingKey { case id = "category_id", name = "category_name" }

    init(id: String, name: String) { self.id = id; self.name = name }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.string(.id) ?? ""
        name = c.string(.name)?.nonEmpty ?? "—"
    }
}

nonisolated struct LiveChannel: Codable, Hashable, Sendable, Identifiable {
    var id: Int
    var name: String
    var number: Int?
    var icon: String?
    var categoryID: String?
    var epgID: String?
    /// Where it plays from, for playlists and the free channels; Xtream
    /// streams play from the panel. Either a web address or what
    /// `StreamResolver` turns into one ("francetv:…").
    var url: String?

    enum CodingKeys: String, CodingKey {
        case id = "stream_id", name, number = "num", icon = "stream_icon", categoryID = "category_id", epgID = "epg_channel_id", url = "flux_url"
    }

    init(id: Int, name: String, number: Int? = nil, icon: String? = nil, categoryID: String? = nil, epgID: String? = nil, url: String? = nil) {
        self.id = id
        self.name = name
        self.number = number
        self.icon = icon
        self.categoryID = categoryID
        self.epgID = epgID
        self.url = url
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        guard let id = c.int(.id) else { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "no id")) }
        self.id = id
        name = c.string(.name)?.nonEmpty ?? String(localized: "Chaîne \(id)")
        number = c.int(.number)
        icon = c.string(.icon)?.nonEmpty
        categoryID = c.string(.categoryID)
        epgID = c.string(.epgID)?.nonEmpty
        url = c.string(.url)?.nonEmpty
    }
}

nonisolated struct Movie: Codable, Hashable, Sendable, Identifiable {
    var id: Int
    var name: String
    var poster: String?
    var categoryID: String?
    var rating: Double?
    var container: String
    var added: Int?
    /// Where it plays from, for playlists.
    var url: String?

    enum CodingKeys: String, CodingKey {
        case id = "stream_id", name, poster = "stream_icon", categoryID = "category_id", rating = "rating", container = "container_extension", added, url = "flux_url"
    }

    init(id: Int, name: String, poster: String?, categoryID: String?, container: String, url: String?) {
        self.id = id
        self.name = name
        self.poster = poster
        self.categoryID = categoryID
        self.container = container
        self.url = url
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        guard let id = c.int(.id) else { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "no id")) }
        self.id = id
        name = c.string(.name)?.nonEmpty ?? String(localized: "Film \(id)")
        poster = c.string(.poster)?.nonEmpty
        categoryID = c.string(.categoryID)
        rating = c.double(.rating).flatMap { $0 > 0 ? $0 : nil }
        container = c.string(.container)?.nonEmpty ?? "mp4"
        added = c.int(.added)
        url = c.string(.url)?.nonEmpty
    }
}

nonisolated struct Series: Codable, Hashable, Sendable, Identifiable {
    var id: Int
    var name: String
    var cover: String?
    var categoryID: String?
    var rating: Double?
    var year: String?

    enum CodingKeys: String, CodingKey {
        case id = "series_id", name, cover, categoryID = "category_id", rating, year = "releaseDate"
    }

    init(id: Int, name: String, cover: String?, categoryID: String?) {
        self.id = id
        self.name = name
        self.cover = cover
        self.categoryID = categoryID
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        guard let id = c.int(.id) else { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "no id")) }
        self.id = id
        name = c.string(.name)?.nonEmpty ?? String(localized: "Série \(id)")
        cover = c.string(.cover)?.nonEmpty
        categoryID = c.string(.categoryID)
        rating = c.double(.rating).flatMap { $0 > 0 ? $0 : nil }
        year = c.string(.year)?.nonEmpty.map { String($0.prefix(4)) }
    }
}

/// A list that keeps the rows it can read: one malformed entry among
/// 30 000 shouldn't empty the whole section.
nonisolated struct Lenient<Element: Decodable & Sendable>: Decodable, Sendable {
    var items: [Element]

    private struct Skip: Decodable {}

    init(from decoder: Decoder) throws {
        var items: [Element] = []
        guard var c = try? decoder.unkeyedContainer() else { self.items = []; return }
        items.reserveCapacity(c.count ?? 0)
        while !c.isAtEnd {
            if let item = try? c.decode(Element.self) { items.append(item) } else { _ = try? c.decode(Skip.self) }
        }
        self.items = items
    }
}

// MARK: - Account

nonisolated struct AccountInfo: Decodable, Sendable {
    var authenticated: Bool
    var status: String?
    var expiration: Date?
    var maxConnections: Int?
    var activeConnections: Int?
    var isTrial: Bool

    /// A playlist or the free channels: nothing expires.
    init() {
        authenticated = true
        isTrial = false
    }

    enum Root: String, CodingKey { case user = "user_info" }
    enum Keys: String, CodingKey {
        case auth, status, exp = "exp_date", max = "max_connections", active = "active_cons", trial = "is_trial"
    }

    init(from decoder: Decoder) throws {
        let root = try decoder.container(keyedBy: Root.self)
        let c = try root.nestedContainer(keyedBy: Keys.self, forKey: .user)
        authenticated = c.int(.auth) == 1
        status = c.string(.status)
        expiration = c.double(.exp).map { Date(timeIntervalSince1970: $0) }
        maxConnections = c.int(.max)
        activeConnections = c.int(.active)
        isTrial = c.int(.trial) == 1
    }
}

// MARK: - Details

nonisolated struct MovieDetails: Decodable, Sendable {
    var plot: String?
    var genre: String?
    var cast: String?
    var director: String?
    var duration: String?
    var year: String?
    var backdrop: String?
    var poster: String?

    init() {}

    enum Root: String, CodingKey { case info }
    enum Keys: String, CodingKey {
        case plot, description, genre, cast, actors, director, duration, releasedate, release_date, year, backdrop_path, movie_image, cover_big
    }

    init(from decoder: Decoder) throws {
        let root = try decoder.container(keyedBy: Root.self)
        guard let c = try? root.nestedContainer(keyedBy: Keys.self, forKey: .info) else { return }
        plot = (c.string(.plot) ?? c.string(.description))?.nonEmpty
        genre = c.string(.genre)?.nonEmpty
        cast = (c.string(.cast) ?? c.string(.actors))?.nonEmpty
        director = c.string(.director)?.nonEmpty
        duration = c.string(.duration)?.nonEmpty
        year = (c.string(.releasedate) ?? c.string(.release_date) ?? c.string(.year))?.nonEmpty.map { String($0.prefix(4)) }
        backdrop = c.strings(.backdrop_path).first?.nonEmpty
        poster = (c.string(.movie_image) ?? c.string(.cover_big))?.nonEmpty
    }
}

nonisolated struct Episode: Decodable, Hashable, Sendable, Identifiable {
    var id: String
    var number: Int
    var season: Int
    var title: String
    var container: String
    var image: String?
    var plot: String?
    var duration: String?
    /// Where it plays from, for playlists.
    var url: String?

    init(id: String, number: Int, season: Int, title: String, container: String, image: String?, url: String?) {
        self.id = id
        self.number = number
        self.season = season
        self.title = title
        self.container = container
        self.image = image
        self.url = url
    }

    enum Keys: String, CodingKey { case id, episode_num, season, title, container_extension, info }
    enum Info: String, CodingKey { case movie_image, plot, duration }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        guard let id = c.string(.id) else { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "no id")) }
        self.id = id
        number = c.int(.episode_num) ?? 0
        season = c.int(.season) ?? 0
        title = c.string(.title)?.nonEmpty ?? String(localized: "Épisode \(number)")
        container = c.string(.container_extension)?.nonEmpty ?? "mp4"
        if let i = try? c.nestedContainer(keyedBy: Info.self, forKey: .info) {
            image = i.string(.movie_image)?.nonEmpty
            plot = i.string(.plot)?.nonEmpty
            duration = i.string(.duration)?.nonEmpty
        }
    }
}

nonisolated struct SeriesDetails: Decodable, Sendable {
    var plot: String?
    var genre: String?
    var cast: String?
    var year: String?
    var backdrop: String?
    /// Season number → its episodes, in order.
    var seasons: [Int: [Episode]] = [:]

    init(seasons: [Int: [Episode]]) { self.seasons = seasons }

    enum Root: String, CodingKey { case info, episodes }
    enum Keys: String, CodingKey { case plot, genre, cast, releaseDate, backdrop_path }

    init(from decoder: Decoder) throws {
        let root = try decoder.container(keyedBy: Root.self)
        if let c = try? root.nestedContainer(keyedBy: Keys.self, forKey: .info) {
            plot = c.string(.plot)?.nonEmpty
            genre = c.string(.genre)?.nonEmpty
            cast = c.string(.cast)?.nonEmpty
            year = c.string(.releaseDate)?.nonEmpty.map { String($0.prefix(4)) }
            backdrop = c.strings(.backdrop_path).first?.nonEmpty
        }
        // A dictionary keyed by season on most panels, an array of arrays on some.
        var all: [Episode] = []
        if let byKey = try? root.decode([String: Lenient<Episode>].self, forKey: .episodes) {
            for (key, list) in byKey {
                all += list.items.map { var e = $0; if e.season == 0 { e.season = Int(key) ?? 0 }; return e }
            }
        } else if let nested = try? root.decode([Lenient<Episode>].self, forKey: .episodes) {
            all = nested.flatMap(\.items)
        }
        seasons = Dictionary(grouping: all, by: \.season).mapValues { $0.sorted { $0.number < $1.number } }
    }
}

nonisolated struct Programme: Decodable, Sendable, Hashable {
    var title: String
    var summary: String?
    var start: Date
    var end: Date
    /// A still of the programme: only the open guide has them.
    var image: String?

    init(title: String, summary: String?, start: Date, end: Date, image: String?) {
        self.title = title
        self.summary = summary
        self.start = start
        self.end = end
        self.image = image
    }

    enum Keys: String, CodingKey { case title, description, start_timestamp, stop_timestamp }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        // Titles and descriptions come base64-encoded.
        func text(_ key: Keys) -> String? {
            let raw = c.string(key) ?? ""
            return (Data(base64Encoded: raw).flatMap { String(data: $0, encoding: .utf8) } ?? raw).nonEmpty
        }
        title = text(.title) ?? String(localized: "Programme")
        summary = text(.description)
        start = Date(timeIntervalSince1970: c.double(.start_timestamp) ?? 0)
        end = Date(timeIntervalSince1970: c.double(.stop_timestamp) ?? 0)
    }

    var isOn: Bool { (start...end).contains(Date()) }
    var timeRange: String { "\(start.formatted(date: .omitted, time: .shortened)) – \(end.formatted(date: .omitted, time: .shortened))" }
}

/// Names as panels write them, "|FR| TF1 ᵁᴴᴰ" or "FR: TF1", shown
/// without the country tag every row repeats.
nonisolated enum DisplayName {
    static func clean(_ name: String) -> String {
        // Also the stars some panels lead every category with.
        let rest = ChannelName.splitCountry(Substring(name)).rest.trimmingCharacters(in: CharacterSet(charactersIn: " ✪★☆●•◉▶►"))
        return rest.isEmpty ? name : rest
    }
}
