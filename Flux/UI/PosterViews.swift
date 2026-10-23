import SwiftUI

/// A 2:3 poster with its title under it.
struct PosterCard: View {
    let title: String
    let image: String?
    var rating: Double?
    var progress: Double?
    var isFavorite = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Color.clear
                .aspectRatio(2 / 3, contentMode: .fit)
                .overlay {
                    RemoteImage(url: image, size: 220) {
                        ZStack {
                            Rectangle().fill(.white.opacity(0.06))
                            Image(systemName: "film").font(.title2).foregroundStyle(.tertiary)
                        }
                    }
                }
                .clipShape(.rect(cornerRadius: 14))
                .overlay(alignment: .topTrailing) {
                    if isFavorite {
                        Image(systemName: "star.fill")
                            .font(.caption2)
                            .padding(6)
                            .glassEffect(.regular, in: .circle)
                            .foregroundStyle(.tint)
                            .padding(6)
                    }
                }
                .overlay(alignment: .bottom) {
                    if let progress, progress > 0 {
                        ProgressView(value: progress)
                            .tint(.accentColor)
                            .padding(8)
                    }
                }
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(.white.opacity(0.08)))
            Text(title)
                .font(.caption.weight(.medium))
                .lineLimit(2, reservesSpace: true)
        }
        .contentShape(.rect)
    }
}

/// The columns of a poster grid: three across an iPhone held upright,
/// more as the width grows.
enum PosterGrid {
    static func columns(compact: Bool) -> [GridItem] {
        [GridItem(.adaptive(minimum: compact ? 104 : 150, maximum: compact ? 170 : 210), spacing: 12, alignment: .top)]
    }
}

struct MoviesView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @AppStorage("movies.category") private var selection = CategoryBar.all
    @AppStorage(FavoritesStyle.storageKey) private var style = FavoritesStyle.separate

    private var movies: Pinned<Movie> {
        let section = catalog.movies
        let favorites = library.favorites(.movie).compactMap { catalog.moviesByKey[$0.key] }
        switch selection {
        case CategoryBar.favorites where style == .separate:
            return Pinned(favorites, favorites: [])
        case CategoryBar.recent:
            return Pinned(section.items
                .compactMap { m in library.progress[Library.key(.movie, m.id)].map { (m, $0.date) } }
                .sorted { $0.1 > $1.1 }
                .map(\.0), favorites: [])
        case CategoryBar.all, CategoryBar.favorites:
            return Pinned(section.items, favorites: style == .pinned ? favorites : [])
        default:
            return Pinned(section.items(in: selection), favorites: style == .pinned ? favorites : [])
        }
    }

    var body: some View {
        PosterBrowser(
            title: String(localized: "Films"), empty: String(localized: "Aucun film"), symbol: "film",
            categories: catalog.movies.categories, kind: .movie, isEmpty: catalog.movies.isEmpty,
            items: movies, selection: $selection, showsRecent: true
        ) { movie in
            PosterCard(title: DisplayName.clean(movie.name), image: movie.poster,
                       progress: library.progress[Library.key(.movie, movie.id)]?.fraction,
                       isFavorite: library.isFavorite(.movie(movie)))
                .contextMenu { FavoriteButton(favorite: .movie(movie)) }
        }
        .navigationDestination(for: Movie.self) { MovieDetailView(movie: $0) }
    }
}

struct SeriesListView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @AppStorage("series.category") private var selection = CategoryBar.all
    @AppStorage(FavoritesStyle.storageKey) private var style = FavoritesStyle.separate

    private var series: Pinned<Series> {
        let section = catalog.series
        let favorites = library.favorites(.series).compactMap { catalog.seriesByKey[$0.key] }
        switch selection {
        case CategoryBar.favorites where style == .separate:
            return Pinned(favorites, favorites: [])
        case CategoryBar.all, CategoryBar.favorites, CategoryBar.recent:
            return Pinned(section.items, favorites: style == .pinned ? favorites : [])
        default:
            return Pinned(section.items(in: selection), favorites: style == .pinned ? favorites : [])
        }
    }

    var body: some View {
        PosterBrowser(
            title: String(localized: "Séries"), empty: String(localized: "Aucune série"), symbol: "tv",
            categories: catalog.series.categories, kind: .series, isEmpty: catalog.series.isEmpty,
            items: series, selection: $selection, showsRecent: false
        ) { show in
            PosterCard(title: DisplayName.clean(show.name), image: show.cover,
                       isFavorite: library.isFavorite(.series(show)))
                .contextMenu { FavoriteButton(favorite: .series(show)) }
        }
        .navigationDestination(for: Series.self) { SeriesDetailView(series: $0) }
    }
}

/// A poster grid under category chips, favorites apart or pinned first.
private struct PosterBrowser<Item: Identifiable & Hashable, Card: View>: View {
    let title: String
    let empty: String
    let symbol: String
    let categories: [Category]
    let kind: MediaKind
    let isEmpty: Bool
    let items: Pinned<Item>
    @Binding var selection: String
    let showsRecent: Bool
    @ViewBuilder let card: (Item) -> Card

    @Environment(Catalog.self) private var catalog
    @Environment(AccountStore.self) private var account
    @Environment(\.horizontalSizeClass) private var sizeClass
    @AppStorage(FavoritesStyle.storageKey) private var style = FavoritesStyle.separate

    var body: some View {
        ScrollView {
            // The chips open the list, under the title, and scroll away with it.
            LazyVStack(spacing: 0) {
                if !isEmpty {
                    CategoryBar(categories: categories, kind: kind, selection: $selection, showsRecent: showsRecent, showsFavorites: style == .separate)
                }
                if isEmpty {
                    CatalogState(empty: empty).containerRelativeFrame(.vertical)
                } else if items.isEmpty {
                    ContentUnavailableView(selection == CategoryBar.favorites ? "Aucun favori" : "Rien ici", systemImage: selection == CategoryBar.favorites ? "star" : symbol,
                                           description: selection == CategoryBar.favorites ? Text("Maintiens une affiche pour l’ajouter.") : nil)
                        .containerRelativeFrame(.vertical) { h, _ in h - CategoryBar.height }
                } else {
                    LazyVGrid(columns: PosterGrid.columns(compact: sizeClass == .compact), spacing: 16) {
                        if !items.top.isEmpty {
                            Section {
                                cards(items.top)
                            } header: {
                                ListHeader(title: "Favoris", systemImage: "star.fill")
                            }
                            Section {
                                cards(items.rest)
                            } header: {
                                ListHeader(title: title, systemImage: nil).padding(.top, 8)
                            }
                        } else {
                            cards(items.rest)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 4)
                    .padding(.bottom, 24)
                }
            }
        }
        .scrollIndicators(.hidden)
        .refreshable { if let client = account.client { await catalog.refresh(client) } }
        .background(Color.screenBackground.ignoresSafeArea())
        .navigationTitle(title)
    }

    private func cards(_ items: [Item]) -> some View {
        ForEach(items) { item in
            NavigationLink(value: item) { card(item) }
                .buttonStyle(.plain)
        }
    }
}
