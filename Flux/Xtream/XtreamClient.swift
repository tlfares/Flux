import Foundation

nonisolated enum XtreamError: LocalizedError {
    case badServer, unauthorized, http(Int)

    var errorDescription: String? {
        switch self {
        case .badServer: String(localized: "L’adresse du serveur n’est pas valide.")
        case .unauthorized: String(localized: "Identifiants refusés par le serveur.")
        case .http(let code): String(localized: "Le serveur a répondu \(code).")
        }
    }
}

nonisolated extension Error {
    /// In French, and about the server rather than about iOS.
    var readableMessage: String {
        if let xtream = self as? XtreamError { return xtream.errorDescription ?? "" }
        guard let url = self as? URLError else {
            return self is DecodingError ? String(localized: "Le serveur n’a pas répondu comme un serveur Xtream.") : localizedDescription
        }
        switch url.code {
        case .notConnectedToInternet, .networkConnectionLost: return String(localized: "Pas de connexion internet.")
        case .timedOut: return String(localized: "Le serveur met trop de temps à répondre.")
        case .cannotFindHost, .dnsLookupFailed: return String(localized: "Serveur introuvable. Vérifie l’adresse.")
        case .cannotConnectToHost: return String(localized: "Le serveur refuse la connexion. Vérifie le port.")
        case .secureConnectionFailed, .serverCertificateUntrusted, .serverCertificateHasBadDate: return String(localized: "Connexion sécurisée impossible. Essaie en http://.")
        default: return String(localized: "Erreur réseau (\(url.code.rawValue)).")
        }
    }
}

/// The Xtream Codes `player_api.php`, and where each stream plays from.
/// Requests and decoding run off the main actor: a VOD catalogue can be
/// tens of megabytes of JSON.
nonisolated struct XtreamClient: Sendable {
    let credentials: Credentials

    static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.httpMaximumConnectionsPerHost = 6
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        // Many panels reject requests without a player-like agent.
        config.httpAdditionalHeaders = ["User-Agent": "Flux/1.0 (iOS)"]
        return URLSession(configuration: config)
    }()

    private func apiURL(_ action: String?, _ extra: [URLQueryItem] = []) throws -> URL {
        guard var components = URLComponents(string: credentials.baseURL + "/player_api.php") else { throw XtreamError.badServer }
        var items = [URLQueryItem(name: "username", value: credentials.username), URLQueryItem(name: "password", value: credentials.password)]
        if let action { items.append(URLQueryItem(name: "action", value: action)) }
        components.queryItems = items + extra
        guard let url = components.url, url.host() != nil else { throw XtreamError.badServer }
        return url
    }

    @concurrent
    private func fetch<T: Decodable>(_ type: T.Type, _ action: String?, _ extra: [URLQueryItem] = []) async throws -> T {
        let url = try apiURL(action, extra)
        var attempt = 0
        while true {
            do {
                let (data, response) = try await Self.session.data(from: url)
                if let http = response as? HTTPURLResponse {
                    if http.statusCode == 401 || http.statusCode == 403 { throw XtreamError.unauthorized }
                    guard (200..<300).contains(http.statusCode) else { throw XtreamError.http(http.statusCode) }
                }
                return try JSONDecoder().decode(T.self, from: data)
            } catch {
                // Panels behind Cloudflare answer 5xx to bursts, and drop
                // connections: a moment later the same request goes through.
                attempt += 1
                guard attempt < 3, Self.isTransient(error), !Task.isCancelled else { throw error }
                try await Task.sleep(for: .milliseconds(600 * attempt))
            }
        }
    }

    private static func isTransient(_ error: Error) -> Bool {
        if case XtreamError.http(let code) = error { return code >= 500 || code == 429 }
        guard let url = error as? URLError else { return false }
        return [.timedOut, .networkConnectionLost, .cannotConnectToHost, .badServerResponse].contains(url.code)
    }

    /// A playlist or the free channels rather than a panel: the same calls,
    /// answered from the playlist.
    var isPanel: Bool { credentials.source == .xtream }

    private func playlist(maxAge: TimeInterval = 120) async throws -> Playlist {
        try await PlaylistStore.shared.playlist(credentials, maxAge: maxAge)
    }

    func account() async throws -> AccountInfo {
        guard isPanel else {
            _ = try await playlist(maxAge: 0)
            return AccountInfo()
        }
        let info = try await fetch(AccountInfo.self, nil)
        guard info.authenticated else { throw XtreamError.unauthorized }
        return info
    }

    func categories(_ kind: MediaKind) async throws -> [Category] {
        guard isPanel else { return try await playlist().categories(kind) }
        let action = switch kind {
        case .live: "get_live_categories"
        case .movie: "get_vod_categories"
        case .series: "get_series_categories"
        }
        return try await fetch(Lenient<Category>.self, action).items
    }

    func channels() async throws -> [LiveChannel] {
        guard isPanel else { return try await playlist().live }
        return try await fetch(Lenient<LiveChannel>.self, "get_live_streams").items
    }

    func movies() async throws -> [Movie] {
        guard isPanel else { return try await playlist().movies }
        return try await fetch(Lenient<Movie>.self, "get_vod_streams").items
    }

    func series() async throws -> [Series] {
        guard isPanel else { return try await playlist().series }
        return try await fetch(Lenient<Series>.self, "get_series").items
    }

    func movieDetails(_ id: Int) async throws -> MovieDetails {
        guard isPanel else { return MovieDetails() }
        return try await fetch(MovieDetails.self, "get_vod_info", [URLQueryItem(name: "vod_id", value: String(id))])
    }

    func seriesDetails(_ id: Int) async throws -> SeriesDetails {
        guard isPanel else { return SeriesDetails(seasons: try await playlist(maxAge: .infinity).episodes[id] ?? [:]) }
        return try await fetch(SeriesDetails.self, "get_series_info", [URLQueryItem(name: "series_id", value: String(id))])
    }

    func shortEPG(_ channelID: Int, limit: Int = 2) async throws -> [Programme] {
        guard isPanel else {
            // Pluto TV has its own guide; the TNT's comes from the open guide.
            guard let link = await PlaylistStore.shared.channel(channelID, of: credentials)?.url, link.hasPrefix("pluto:") else { return [] }
            return await PlutoTV.shared.programmes(String(link.dropFirst(6)))
        }
        struct Listing: Decodable { var epg_listings: Lenient<Programme>? }
        let listing = try await fetch(Listing.self, "get_short_epg", [
            URLQueryItem(name: "stream_id", value: String(channelID)),
            URLQueryItem(name: "limit", value: String(limit)),
        ])
        return listing.epg_listings?.items ?? []
    }

    // MARK: Streams

    private func streamURL(_ path: String, _ file: String) -> URL? {
        let allowed = CharacterSet.urlPathAllowed.subtracting(CharacterSet(charactersIn: "/?#"))
        let user = credentials.username.addingPercentEncoding(withAllowedCharacters: allowed) ?? credentials.username
        let pass = credentials.password.addingPercentEncoding(withAllowedCharacters: allowed) ?? credentials.password
        return URL(string: "\(credentials.baseURL)/\(path)/\(user)/\(pass)/\(file)")
    }

    /// HLS: AVPlayer can't play the raw MPEG-TS most panels default to.
    /// A playlist's own address, looked up first for the free channels.
    func liveURL(_ channel: LiveChannel) async -> URL? {
        if let link = channel.url { return Self.hls(await StreamResolver.resolve(link)) }
        return streamURL("live", "\(channel.id).m3u8")
    }

    /// A panel's stream in a playlist, `/live/user/pass/1.ts` or
    /// `/user/pass/1`, asked for as HLS like the panel's own.
    private static func hls(_ url: URL?) -> URL? {
        guard let url, var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return url }
        let parts = components.path.split(separator: "/")
        if parts.count == 4, parts[0] == "live", parts[3].hasSuffix(".ts") {
            components.path = "/" + parts.dropLast().joined(separator: "/") + "/" + parts[3].dropLast(3) + ".m3u8"
        } else if parts.count == 3, parts[2].allSatisfy(\.isNumber) {
            components.path = "/live/" + parts.joined(separator: "/") + ".m3u8"
        } else {
            return url
        }
        return components.url ?? url
    }

    func movieURL(_ movie: Movie) -> URL? {
        if let url = movie.url { return URL(string: url) }
        return streamURL("movie", "\(movie.id).\(movie.container)")
    }

    func episodeURL(_ episode: Episode) -> URL? {
        if let url = episode.url { return URL(string: url) }
        return streamURL("series", "\(episode.id).\(episode.container)")
    }
}
