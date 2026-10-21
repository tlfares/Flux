import SwiftUI

/// Where a poster leads.
enum TVRoute: Hashable {
    case movie(Movie)
    case series(Series)

    @MainActor @ViewBuilder var destination: some View {
        switch self {
        case .movie(let movie): TVMovieDetail(movie: movie)
        case .series(let series): TVSeriesDetail(series: series)
        }
    }
}

/// A star that fills, for favorites.
struct TVFavoriteButton: View {
    @Environment(Library.self) private var library
    let favorite: Favorite

    var body: some View {
        let on = library.isFavorite(favorite)
        Button {
            library.toggleFavorite(favorite)
        } label: {
            Label(on ? "Retirer des favoris" : "Ajouter aux favoris", systemImage: on ? "star.fill" : "star")
        }
    }
}

/// Films or series: the categories down the left, the posters on the right.
struct TVPosterBrowser<Item: Identifiable & Hashable>: View {
    let empty: String
    let categories: [Category]
    let isEmpty: Bool
    let items: [Item]
    @Binding var selection: String
    let title: (Item) -> String
    let image: (Item) -> String?
    var progress: (Item) -> Double? = { _ in nil }
    let favorite: (Item) -> Favorite
    let route: (Item) -> TVRoute

    @State private var path: [TVRoute] = []
    private let columns = [GridItem(.adaptive(minimum: 200, maximum: 230), spacing: 36, alignment: .top)]

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if isEmpty {
                    TVCatalogState(empty: empty)
                } else {
                    HStack(alignment: .top, spacing: 30) {
                        TVCategoryColumn(entries: TVCategory.entries(categories, recent: false), selection: $selection)
                        ScrollView {
                            if items.isEmpty {
                                ContentUnavailableView(selection == TVCategory.favorites ? "Aucun favori" : "Rien ici", systemImage: "film",
                                                       description: selection == TVCategory.favorites ? Text("Maintiens le clic sur une affiche pour l’ajouter.") : nil)
                                    .padding(.top, 120)
                            }
                            LazyVGrid(columns: columns, spacing: 50) {
                                ForEach(items) { item in
                                    TVPosterButton(title: title(item), image: image(item), progress: progress(item), width: 200) {
                                        path.append(route(item))
                                    }
                                    .contextMenu { TVFavoriteButton(favorite: favorite(item)) }
                                }
                            }
                            .padding(.vertical, 30)
                            .padding(.trailing, 60)
                        }
                        .scrollIndicators(.hidden)
                        .scrollClipDisabled()
                        .focusSection()
                    }
                }
            }
            .navigationDestination(for: TVRoute.self) { $0.destination }
        }
    }
}

struct TVMoviesView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @AppStorage("tv.movies.category") private var selection = TVCategory.all

    var body: some View {
        let movies: [Movie] = switch selection {
        case TVCategory.favorites: library.favorites(.movie).compactMap { catalog.moviesByKey[$0.key] }
        case TVCategory.all: catalog.movies.items
        default: catalog.movies.items(in: selection)
        }
        TVPosterBrowser(empty: "Aucun film", categories: catalog.movies.categories, isEmpty: catalog.movies.isEmpty, items: movies,
                        selection: $selection, title: { DisplayName.clean($0.name) }, image: \.poster,
                        progress: { library.progress[Library.key(.movie, $0.id)]?.fraction },
                        favorite: { .movie($0) }, route: { .movie($0) })
    }
}

struct TVSeriesView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @AppStorage("tv.series.category") private var selection = TVCategory.all

    var body: some View {
        let series: [Series] = switch selection {
        case TVCategory.favorites: library.favorites(.series).compactMap { catalog.seriesByKey[$0.key] }
        case TVCategory.all: catalog.series.items
        default: catalog.series.items(in: selection)
        }
        TVPosterBrowser(empty: "Aucune série", categories: catalog.series.categories, isEmpty: catalog.series.isEmpty, items: series,
                        selection: $selection, title: { DisplayName.clean($0.name) }, image: \.cover,
                        favorite: { .series($0) }, route: { .series($0) })
    }
}

/// A film or a series' page: its picture across the screen, the text and
/// buttons over its left side.
private struct TVDetailPage<Actions: View, Below: View>: View {
    let title: String
    let backdrop: String?
    let poster: String?
    let facts: [String]
    let plot: String?
    @ViewBuilder let actions: Actions
    @ViewBuilder let below: Below

    var body: some View {
        ZStack(alignment: .topLeading) {
            RemoteImage(url: backdrop ?? poster, size: 1920) { Color(white: 0.08) }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .blur(radius: backdrop == nil ? 40 : 0)
                .clipped()
                .ignoresSafeArea()
            // Dark under the text whatever the picture: light posters washed it out.
            LinearGradient(stops: [.init(color: .black.opacity(0.95), location: 0), .init(color: .black.opacity(0.9), location: 0.55),
                                   .init(color: .black.opacity(0.35), location: 0.8), .init(color: .clear, location: 1)],
                           startPoint: .leading, endPoint: .trailing)
                .ignoresSafeArea()
            LinearGradient(colors: [.black.opacity(0.5), .clear, .clear, .black.opacity(0.95)], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    Text(title)
                        .font(.system(size: 64, weight: .bold))
                        .lineLimit(2)
                    if !facts.isEmpty {
                        Text(facts.joined(separator: "  ·  ")).font(.callout).foregroundStyle(.secondary)
                    }
                    if let plot {
                        Text(plot).font(.callout).foregroundStyle(.secondary).lineLimit(5)
                            .frame(maxWidth: 1000, alignment: .leading)
                    }
                    HStack(spacing: 24) { actions }
                        .padding(.top, 10)
                        .focusSection()
                    below
                }
                .padding(.horizontal, 80)
                .padding(.top, 120)
                .padding(.bottom, 80)
            }
            .scrollClipDisabled()
        }
    }
}

struct TVMovieDetail: View {
    let movie: Movie

    @Environment(AccountStore.self) private var account
    @Environment(Library.self) private var library
    @Environment(TVPlayer.self) private var player
    @State private var details: MovieDetails?

    var body: some View {
        let key = Library.key(.movie, movie.id)
        let resumable = library.progress[key]?.isResumable == true
        TVDetailPage(title: DisplayName.clean(movie.name), backdrop: details?.backdrop, poster: details?.poster ?? movie.poster,
                     facts: facts, plot: details?.plot) {
            Button {
                if let client = account.client { player.play(movie, client: client) }
            } label: {
                Label(resumable ? "Reprendre" : "Lecture", systemImage: "play.fill").padding(.horizontal, 20)
            }
            .buttonStyle(.borderedProminent)
            if resumable {
                Button {
                    if let client = account.client { player.play(movie, client: client, fromStart: true) }
                } label: {
                    Label("Depuis le début", systemImage: "arrow.counterclockwise")
                }
            }
            TVFavoriteButton(favorite: .movie(movie))
        } below: {
            VStack(alignment: .leading, spacing: 14) {
                if let director = details?.director { credit("Réalisation", director) }
                if let cast = details?.cast { credit("Avec", cast) }
            }
            .padding(.top, 20)
        }
        .task {
            guard details == nil, let client = account.client else { return }
            details = (try? await client.movieDetails(movie.id)) ?? MovieDetails()
        }
    }

    private var facts: [String] {
        var f: [String] = []
        if let year = details?.year { f.append(year) }
        if let duration = details?.duration { f.append(duration) }
        if let genre = details?.genre { f.append(genre) }
        if let rating = movie.rating { f.append("★ " + rating.formatted(.number.precision(.fractionLength(1)))) }
        return f
    }

    private func credit(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 16) {
            Text(label).font(.caption.weight(.semibold)).foregroundStyle(.tertiary).frame(width: 180, alignment: .leading)
            Text(value).font(.caption).foregroundStyle(.secondary).lineLimit(2)
        }
        .frame(maxWidth: 1100, alignment: .leading)
    }
}

struct TVSeriesDetail: View {
    let series: Series

    @Environment(AccountStore.self) private var account
    @Environment(Library.self) private var library
    @Environment(TVPlayer.self) private var player
    @State private var details: SeriesDetails?
    @State private var failed = false
    @State private var season: Int?

    private var seasons: [Int] { details?.seasons.keys.sorted() ?? [] }

    var body: some View {
        TVDetailPage(title: DisplayName.clean(series.name), backdrop: details?.backdrop, poster: series.cover, facts: facts, plot: details?.plot) {
            if let next = nextEpisode {
                Button {
                    if let client = account.client { player.play(next, of: series, client: client) }
                } label: {
                    let started = library.progress[Library.key(.series, next.id)] != nil
                    Label(started ? "Reprendre S\(next.season) E\(next.number)" : "Lecture S\(next.season) E\(next.number)", systemImage: "play.fill")
                        .padding(.horizontal, 20)
                }
                .buttonStyle(.borderedProminent)
            }
            TVFavoriteButton(favorite: .series(series))
        } below: {
            if details == nil && !failed {
                ProgressView().padding(.top, 40)
            } else if seasons.isEmpty {
                Text("Aucun épisode.").foregroundStyle(.secondary).padding(.top, 40)
            } else {
                VStack(alignment: .leading, spacing: 20) {
                    if seasons.count > 1 {
                        ScrollView(.horizontal) {
                            HStack(spacing: 16) {
                                ForEach(seasons, id: \.self) { s in
                                    Button { season = s } label: {
                                        Text("Saison \(s)").font(.callout.weight(.semibold)).padding(.horizontal, 26).padding(.vertical, 12)
                                    }
                                    .buttonStyle(TVRowStyle(cornerRadius: 30, isCurrent: s == (season ?? seasons[0])))
                                }
                            }
                            .padding(.vertical, 12)
                        }
                        .scrollClipDisabled()
                        .focusSection()
                    }
                    ScrollView(.horizontal) {
                        LazyHStack(alignment: .top, spacing: 40) {
                            ForEach(details?.seasons[season ?? seasons[0]] ?? []) { episode in
                                episodeCard(episode)
                            }
                        }
                        .padding(.vertical, 24)
                    }
                    .scrollClipDisabled()
                    .focusSection()
                }
                .padding(.top, 30)
            }
        }
        .task {
            guard details == nil, let client = account.client else { return }
            do {
                let loaded = try await client.seriesDetails(series.id)
                details = loaded
                season = nextEpisode?.season ?? loaded.seasons.keys.min()
            } catch {
                failed = true
            }
        }
    }

    private func episodeCard(_ episode: Episode) -> some View {
        let progress = library.progress[Library.key(.series, episode.id)]
        return VStack(alignment: .leading, spacing: 12) {
            Button {
                if let client = account.client { player.play(episode, of: series, client: client) }
            } label: {
                RemoteImage(url: episode.image ?? details?.backdrop ?? series.cover, size: 600) { Color(white: 0.14) }
                    .frame(width: 420, height: 236)
                    .overlay(alignment: .bottom) {
                        if let fraction = progress?.fraction, fraction > 0 {
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(.white.opacity(0.25))
                                    Capsule().fill(FluxTint.blue).frame(width: geo.size.width * fraction)
                                }
                            }
                            .frame(height: 6)
                            .padding(14)
                        }
                    }
            }
            .buttonStyle(.card)
            Text("Épisode \(episode.number)").font(.caption.weight(.semibold))
            if let title = Self.ownTitle(episode) {
                Text(title).font(.caption).lineLimit(1)
            }
            if let plot = episode.plot {
                Text(plot).font(.caption2).foregroundStyle(.secondary).lineLimit(2)
            }
        }
        .frame(width: 420, alignment: .leading)
    }

    /// What the title says beyond the series and the episode's number,
    /// which panels often repeat ("Black Clover (2017) FR - S01E01").
    private static func ownTitle(_ episode: Episode) -> String? {
        var title = episode.title
        if let r = title.range(of: #"S\d+\s*E\d+"#, options: [.regularExpression, .caseInsensitive]) {
            title = String(title[r.upperBound...])
        }
        let trimmed = title.trimmingCharacters(in: CharacterSet(charactersIn: " -–:·."))
        return trimmed.isEmpty || trimmed.lowercased() == "épisode \(episode.number)" ? nil : trimmed
    }

    private var facts: [String] {
        var f: [String] = []
        if let year = details?.year ?? series.year { f.append(year) }
        if let genre = details?.genre { f.append(genre) }
        if seasons.count > 0 { f.append(seasons.count == 1 ? "1 saison" : "\(seasons.count) saisons") }
        return f
    }

    private var nextEpisode: Episode? {
        guard let details else { return nil }
        let all = seasons.flatMap { details.seasons[$0] ?? [] }
        var latest: (Int, Date)?
        for (i, e) in all.enumerated() {
            if let p = library.progress[Library.key(.series, e.id)], latest == nil || p.date > latest!.1 { latest = (i, p.date) }
        }
        guard let (i, _) = latest else { return all.first }
        if library.progress[Library.key(.series, all[i].id)]?.isResumable == true { return all[i] }
        return i + 1 < all.count ? all[i + 1] : all[i]
    }
}

/// Channels, films and series by name.
struct TVSearchView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(AccountStore.self) private var account
    @Environment(TVPlayer.self) private var player
    @State private var query = ""
    @State private var results = SearchIndex.Results()
    @State private var path: [TVRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 50) {
                    if !results.live.isEmpty {
                        TVShelf(title: "Chaînes") {
                            ForEach(results.live) { group in
                                TVChannelCard(group: group, width: 400) {
                                    if let client = account.client { player.watch(group, in: results.live, client: client) }
                                }
                            }
                        }
                    }
                    if !results.movies.isEmpty {
                        TVShelf(title: "Films") {
                            ForEach(results.movies) { movie in
                                TVPosterButton(title: DisplayName.clean(movie.name), image: movie.poster) { path.append(.movie(movie)) }
                            }
                        }
                    }
                    if !results.series.isEmpty {
                        TVShelf(title: "Séries") {
                            ForEach(results.series) { series in
                                TVPosterButton(title: DisplayName.clean(series.name), image: series.cover) { path.append(.series(series)) }
                            }
                        }
                    }
                    if results.isEmpty, !query.isEmpty {
                        ContentUnavailableView.search(text: query).frame(maxWidth: .infinity)
                    }
                }
            }
            .searchable(text: $query, prompt: "Chaînes, films, séries")
            .task(id: query) {
                try? await Task.sleep(for: .milliseconds(250))
                guard !Task.isCancelled else { return }
                results = await catalog.searchIndex.search(query, limit: 40)
            }
            .navigationDestination(for: TVRoute.self) { $0.destination }
        }
    }
}
