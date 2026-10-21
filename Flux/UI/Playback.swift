import SwiftUI

/// Builds what the player needs from a catalogue entry.
extension PlayerCenter {
    func play(_ movie: Movie, client: XtreamClient, library: Library, fromStart: Bool = false) {
        guard let url = client.movieURL(movie) else { return }
        let key = Library.key(.movie, movie.id)
        play(Playable(id: key, url: url, title: movie.name, artwork: movie.poster, isLive: false,
                      resumeAt: fromStart ? nil : library.resumePosition(key)))
    }

    func play(_ episode: Episode, of series: Series, client: XtreamClient, library: Library, fromStart: Bool = false) {
        guard let url = client.episodeURL(episode) else { return }
        let key = Library.key(.series, episode.id)
        play(Playable(id: key, url: url, title: episode.title, subtitle: "\(series.name) · S\(episode.season) E\(episode.number)",
                      artwork: episode.image ?? series.cover, isLive: false,
                      resumeAt: fromStart ? nil : library.resumePosition(key)))
    }
}

/// A star that fills, for favorites.
struct FavoriteButton: View {
    @Environment(Library.self) private var library
    let favorite: Favorite

    var body: some View {
        let on = library.isFavorite(favorite)
        Button {
            withAnimation(Motion.snappy) { library.toggleFavorite(favorite) }
        } label: {
            Label(on ? "Retirer des favoris" : "Ajouter aux favoris", systemImage: on ? "star.fill" : "star")
                .contentTransition(.symbolEffect(.replace))
        }
        .sensoryFeedback(.impact(weight: .light), trigger: on)
    }
}

/// A list split around favorites, when they're pinned at the top.
struct Pinned<Item: Identifiable> {
    var top: [Item] = []
    var rest: [Item] = []

    /// `favorites` are the user's, in their order; only those in `items`
    /// go to the top.
    init(_ items: [Item], favorites: [Item]) {
        guard !favorites.isEmpty else { rest = items; return }
        let present = Set(items.map(\.id))
        top = favorites.filter { present.contains($0.id) }
        let pinned = Set(top.map(\.id))
        rest = pinned.isEmpty ? items : items.filter { !pinned.contains($0.id) }
    }

    var isEmpty: Bool { top.isEmpty && rest.isEmpty }
}
