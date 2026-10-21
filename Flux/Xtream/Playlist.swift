import Foundation

/// An M3U playlist read into what the screens show: live channels, films
/// and series, each under its `group-title`. Panels' own M3U links put
/// films under /movie/ and episodes under /series/; elsewhere a file
/// extension marks a film, and "S01 E02" in its name an episode.
nonisolated struct Playlist: Sendable {
    var live: [LiveChannel] = []
    var liveCategories: [Category] = []
    var movies: [Movie] = []
    var movieCategories: [Category] = []
    var series: [Series] = []
    var seriesCategories: [Category] = []
    /// Series id → season → its episodes, in order.
    var episodes: [Int: [Int: [Episode]]] = [:]
    /// The XML TV guide the header points to (`url-tvg`).
    var guideURL: String?

    var isEmpty: Bool { live.isEmpty && movies.isEmpty && series.isEmpty }

    func categories(_ kind: MediaKind) -> [Category] {
        switch kind {
        case .live: liveCategories
        case .movie: movieCategories
        case .series: seriesCategories
        }
    }

    /// An id that stays the same from one download to the next: from the
    /// address, which is what identifies a stream in a playlist.
    static func stableID(_ text: String) -> Int {
        var hash: UInt64 = 1469598103934665603
        for byte in text.utf8 { hash = (hash ^ UInt64(byte)) &* 1099511628211 }
        return Int(hash & 0x3FFF_FFFF_FFFF_FFFF)
    }

    private static let videoExtensions: Set<String> = ["mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "mpg", "mpeg"]

    // MARK: Reading

    struct Entry {
        var name: String
        var url: String
        var attributes: [String: String]
        var group: String?
    }

    static func parse(_ data: Data) -> Playlist {
        let text = String(data: data, encoding: .utf8) ?? String(decoding: data, as: UTF8.self)
        var entries: [Entry] = []
        var guide: String?
        var pending: (name: String, attributes: [String: String])?
        var pendingGroup: String?

        for raw in text.split(whereSeparator: \.isNewline) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            if line.hasPrefix("#EXTM3U") {
                let attributes = Self.attributes(Substring(line.dropFirst(7)))
                guide = (attributes["url-tvg"] ?? attributes["x-tvg-url"])?.split(separator: ",").first.map { $0.trimmingCharacters(in: .whitespaces) }
            } else if line.hasPrefix("#EXTINF:") {
                let (attributes, name) = Self.info(Substring(line.dropFirst(8)))
                pending = (name, attributes)
            } else if line.hasPrefix("#EXTGRP:") {
                pendingGroup = String(line.dropFirst(8)).nonEmpty
            } else if line.hasPrefix("#") {
                continue
            } else {
                let name = pending?.name.nonEmpty ?? pending?.attributes["tvg-name"]?.nonEmpty
                    ?? URL(string: line)?.deletingPathExtension().lastPathComponent.nonEmpty ?? line
                let attributes = pending?.attributes ?? [:]
                entries.append(Entry(name: name, url: line, attributes: attributes, group: attributes["group-title"]?.nonEmpty ?? pendingGroup))
                pending = nil
                pendingGroup = nil
            }
        }
        var playlist = build(entries)
        playlist.guideURL = guide
        return playlist
    }

    /// `-1 tvg-id="TF1.fr" group-title="TNT, HD",TF1`: the attributes, and
    /// the name after the first comma outside quotes.
    private static func info(_ s: Substring) -> ([String: String], String) {
        var inQuotes = false
        var comma: Substring.Index?
        for i in s.indices {
            let c = s[i]
            if c == "\"" { inQuotes.toggle() } else if c == ",", !inQuotes { comma = i; break }
        }
        guard let comma else { return (attributes(s), "") }
        return (attributes(s[..<comma]), s[s.index(after: comma)...].trimmingCharacters(in: .whitespaces))
    }

    private static func attributes(_ s: Substring) -> [String: String] {
        var result: [String: String] = [:]
        var i = s.startIndex
        while i < s.endIndex {
            guard let eq = s[i...].firstIndex(of: "=") else { break }
            let key = s[i..<eq].split(separator: " ").last.map { String($0).lowercased() } ?? ""
            var j = s.index(after: eq)
            guard j < s.endIndex else { break }
            let value: Substring
            if s[j] == "\"" {
                j = s.index(after: j)
                let close = s[j...].firstIndex(of: "\"") ?? s.endIndex
                value = s[j..<close]
                i = close < s.endIndex ? s.index(after: close) : close
            } else {
                let end = s[j...].firstIndex(of: " ") ?? s.endIndex
                value = s[j..<end]
                i = end
            }
            if !key.isEmpty { result[key] = String(value) }
        }
        return result
    }

    // MARK: Sorting into sections

    private enum Kind { case live, movie, episode }

    private static func kind(of entry: Entry) -> Kind {
        let url = entry.url.lowercased()
        let path = URL(string: entry.url)?.path.lowercased() ?? url
        if path.contains("/movie/") { return .movie }
        if path.contains("/series/") { return .episode }
        let ext = (path as NSString).pathExtension
        if videoExtensions.contains(ext) { return episodeNumbers(entry.name) == nil ? .movie : .episode }
        return .live
    }

    /// "Lupin S01 E03", "Lupin.S1E3", "Lupin - S01E03 - Titre": the show,
    /// the season, the episode and what follows.
    private static func episodeNumbers(_ name: String) -> (show: String, season: Int, episode: Int, rest: String)? {
        guard let match = name.firstMatch(of: /(?i)^(.*?)[\s._\-]*S(\d{1,2})[\s._\-]*E(\d{1,4})(.*)$/),
              let season = Int(match.2), let episode = Int(match.3) else { return nil }
        let show = match.1.trimmingCharacters(in: CharacterSet(charactersIn: " .-_|"))
        guard !show.isEmpty else { return nil }
        return (show, season, episode, match.4.trimmingCharacters(in: CharacterSet(charactersIn: " .-_|")))
    }

    /// "6ter" with `tvg-id="6ter.fr"` or `tvg-country="FR"` is the French
    /// one: tagged so, it gets the French numbers, logo and guide.
    private static func countryTagged(_ entry: Entry) -> String {
        guard ChannelName.splitCountry(Substring(entry.name)).country.isEmpty else { return entry.name }
        let fromID = entry.attributes["tvg-id"]?.split(separator: "@").first?.split(separator: ".").last.map(String.init)
        let country = entry.attributes["tvg-country"]?.split(separator: ";").first.map(String.init) ?? fromID
        guard let country, country.count == 2, country.allSatisfy(\.isLetter) else { return entry.name }
        return "|\(country.uppercased())| " + entry.name
    }

    private static func build(_ entries: [Entry]) -> Playlist {
        var playlist = Playlist()
        var usedIDs: Set<Int> = []
        func uniqueID(_ seed: String) -> Int {
            var id = stableID(seed)
            while !usedIDs.insert(id).inserted { id &+= 1 }
            return id
        }
        var categories: [String: [String]] = ["live": [], "movie": [], "series": []]
        var known: Set<String> = []
        func category(_ section: String, _ group: String?) -> String {
            let name = group ?? "Sans catégorie"
            let id = section + "|" + name
            if known.insert(id).inserted { categories[section, default: []].append(name) }
            return id
        }
        var seriesByKey: [String: Int] = [:]

        for entry in entries {
            let logo = entry.attributes["tvg-logo"]?.nonEmpty
            switch kind(of: entry) {
            case .live:
                playlist.live.append(LiveChannel(
                    id: uniqueID(entry.url), name: countryTagged(entry), number: entry.attributes["tvg-chno"].flatMap { Int($0) },
                    icon: logo, categoryID: category("live", entry.group), epgID: entry.attributes["tvg-id"]?.nonEmpty, url: entry.url))
            case .movie:
                let ext = (URL(string: entry.url)?.pathExtension).flatMap(\.nonEmpty) ?? "mp4"
                playlist.movies.append(Movie(id: uniqueID(entry.url), name: entry.name, poster: logo,
                                             categoryID: category("movie", entry.group), container: ext, url: entry.url))
            case .episode:
                let numbers = episodeNumbers(entry.name)
                let show = numbers?.show ?? entry.name
                let key = SearchIndex.fold(show) + "|" + (entry.group ?? "")
                let seriesID: Int
                if let existing = seriesByKey[key] {
                    seriesID = existing
                } else {
                    seriesID = uniqueID("series|" + key)
                    seriesByKey[key] = seriesID
                    playlist.series.append(Series(id: seriesID, name: show, cover: logo, categoryID: category("series", entry.group)))
                }
                let season = numbers?.season ?? 1
                let number = numbers?.episode ?? (playlist.episodes[seriesID]?[season]?.count ?? 0) + 1
                let ext = (URL(string: entry.url)?.pathExtension).flatMap(\.nonEmpty) ?? "mp4"
                let episode = Episode(id: String(uniqueID(entry.url)), number: number, season: season,
                                      title: numbers?.rest.nonEmpty ?? "Épisode \(number)", container: ext, image: logo, url: entry.url)
                playlist.episodes[seriesID, default: [:]][season, default: []].append(episode)
            }
        }
        for (id, seasons) in playlist.episodes {
            playlist.episodes[id] = seasons.mapValues { $0.sorted { $0.number < $1.number } }
        }
        func list(_ section: String) -> [Category] {
            (categories[section] ?? []).map { Category(id: section + "|" + $0, name: $0) }
        }
        playlist.liveCategories = list("live")
        playlist.movieCategories = list("movie")
        playlist.seriesCategories = list("series")
        return playlist
    }
}

nonisolated enum PlaylistError: LocalizedError {
    case empty, unreadable

    var errorDescription: String? {
        switch self {
        case .empty: "Cette liste ne contient aucune chaîne."
        case .unreadable: "Ce lien ne donne pas une liste M3U."
        }
    }
}

/// Playlists as they were last read, so the catalogue's six requests (three
/// categories, three lists) download the file once; the file itself is kept
/// on disk for opening a series after a restart.
actor PlaylistStore {
    static let shared = PlaylistStore()

    private var cache: [String: (playlist: Playlist, date: Date)] = [:]
    private var loading: [String: Task<Playlist, Error>] = [:]

    /// The playlist, downloaded again when what's here is older than
    /// `maxAge`; `.infinity` takes the copy on disk before the network.
    func playlist(_ credentials: Credentials, maxAge: TimeInterval = 120) async throws -> Playlist {
        let key = credentials.baseURL
        if let cached = cache[key], Date().timeIntervalSince(cached.date) < maxAge { return cached.playlist }
        if maxAge == .infinity, credentials.source == .playlist, let data = try? Data(contentsOf: Self.file(key)) {
            let playlist = Playlist.parse(data)
            if !playlist.isEmpty {
                cache[key] = (playlist, .distantPast)
                return playlist
            }
        }
        if let task = loading[key] { return try await task.value }
        let task = Task { try await Self.download(credentials) }
        loading[key] = task
        defer { loading[key] = nil }
        let playlist = try await task.value
        cache[key] = (playlist, Date())
        return playlist
    }

    /// The channel with this id, in the playlist as last read.
    func channel(_ id: Int, of credentials: Credentials) async -> LiveChannel? {
        let playlist = try? await playlist(credentials, maxAge: .infinity)
        return playlist?.live.first { $0.id == id }
    }

    private static func file(_ key: String) -> URL {
        URL.cachesDirectory.appending(path: "playlist-\(String(Playlist.stableID(key), radix: 36)).m3u")
    }

    @concurrent
    private static func download(_ credentials: Credentials) async throws -> Playlist {
        if credentials.source == .free { return await FreeChannels.playlist() }
        guard let url = URL(string: credentials.baseURL), url.host() != nil else { throw XtreamError.badServer }
        let (data, response) = try await XtreamClient.session.data(from: url)
        if let http = response as? HTTPURLResponse {
            if http.statusCode == 401 || http.statusCode == 403 { throw XtreamError.unauthorized }
            guard (200..<300).contains(http.statusCode) else { throw XtreamError.http(http.statusCode) }
        }
        let playlist = Playlist.parse(data)
        if playlist.isEmpty {
            let head = String(decoding: data.prefix(64), as: UTF8.self)
            throw head.contains("#EXT") ? PlaylistError.empty : PlaylistError.unreadable
        }
        try? data.write(to: file(credentials.baseURL), options: .atomic)
        return playlist
    }
}
