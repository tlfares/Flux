import SwiftUI

struct SearchView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(AccountStore.self) private var account
    @Environment(PlayerCenter.self) private var player
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var query = ""
    @State private var results = SearchIndex.Results()
    @State private var isSearching = false
    @FocusState private var fieldFocused: Bool

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 24) {
                if !results.live.isEmpty {
                    section("Chaînes") {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 12)], spacing: 10) {
                            ForEach(results.live) { group in
                                ChannelRow(group: group, isPlaying: player.liveGroup?.id == group.id) { variant in
                                    if let client = account.client { player.watch(group, variant: variant, client: client) }
                                }
                            }
                        }
                    }
                }
                if !results.movies.isEmpty {
                    section("Films") {
                        posters(results.movies) { PosterCard(title: DisplayName.clean($0.name), image: $0.poster) }
                    }
                }
                if !results.series.isEmpty {
                    section("Séries") {
                        posters(results.series) { PosterCard(title: DisplayName.clean($0.name), image: $0.cover) }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .overlay {
            if query.isEmpty {
                ContentUnavailableView("Rechercher", systemImage: "magnifyingglass", description: Text("Chaînes, films et séries."))
            } else if results.isEmpty {
                ContentUnavailableView.search(text: query)
            }
        }
        .background(Color.screenBackground.ignoresSafeArea())
        .navigationTitle("Rechercher")
        .searchable(text: $query, isPresented: $isSearching, prompt: "Chaînes, films, séries")
        // Opening the tab is asking to search: the field and the keyboard
        // come up at once, unless a search is already on screen.
        .searchFocused($fieldFocused)
        .task {
            guard query.isEmpty else { return }
            // Once the tab has settled: earlier, the field isn't there to take it.
            try? await Task.sleep(for: .milliseconds(80))
            isSearching = true
            fieldFocused = true
        }
        .navigationDestination(for: Movie.self) { MovieDetailView(movie: $0) }
        .navigationDestination(for: Series.self) { SeriesDetailView(series: $0) }
        .task(id: query) {
            // Typing is never held up: the search starts once the keys pause.
            guard !query.isEmpty else { results = .init(); return }
            try? await Task.sleep(for: .milliseconds(180))
            guard !Task.isCancelled else { return }
            let found = await catalog.searchIndex.search(query)
            guard !Task.isCancelled else { return }
            results = found
        }
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.title3.bold())
            content()
        }
    }

    private func posters<Item: Identifiable & Hashable, Card: View>(_ items: [Item], card: @escaping (Item) -> Card) -> some View {
        ScrollView(.horizontal) {
            LazyHStack(alignment: .top, spacing: 12) {
                ForEach(items) { item in
                    NavigationLink(value: item) { card(item) }
                        .buttonStyle(.plain)
                        .frame(width: sizeClass == .compact ? 112 : 150)
                }
            }
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
    }
}
