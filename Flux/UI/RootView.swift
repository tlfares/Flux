import SwiftUI

struct RootView: View {
    @Environment(AccountStore.self) private var account
    @Environment(Catalog.self) private var catalog
    @Environment(PlayerCenter.self) private var player
    @Environment(Library.self) private var library
    @Environment(\.scenePhase) private var scenePhase
    @SceneStorage("tab") private var tab = RootTab.live
    @AppStorage(FavoritesStyle.sharedKey) private var sharesFavorites = true
    @AppStorage(GuideOptions.shiftedKey) private var showsShifted = false
    @AppStorage(GuideOptions.prefersFreeKey) private var prefersFree = true
    @AppStorage(FreeScope.storageKey) private var freeScope = FreeScope.detected
    @AppStorage(ChannelNumbering.storageKey) private var numbering = ChannelNumbering.default
    @AppStorage(GuideOptions.tabKey) private var showsGuideTab = false
    @AppStorage(GuideOptions.moviesTabKey) private var showsMoviesTab = true
    @AppStorage(GuideOptions.seriesTabKey) private var showsSeriesTab = true
    @Environment(EPG.self) private var epg
    @State private var openGuide: [String: [Programme]] = [:]

    /// Off unless asked for, and never empty: the free American channels
    /// have no guide.
    private var showsGuide: Bool {
        showsGuideTab && epg.hasGuide(catalog.guide.groups, isPanel: account.client?.isPanel ?? true)
    }

    var body: some View {
        Group {
            if let client = account.client {
                TabView(selection: $tab) {
                    Tab("Direct", systemImage: "dot.radiowaves.left.and.right", value: RootTab.live) {
                        NavigationStack { LiveView() }
                    }
                    if showsGuide {
                        Tab("Guide", systemImage: "calendar.day.timeline.left", value: RootTab.guide) {
                            NavigationStack { GuideView() }
                        }
                    }
                    // A playlist without films or series (the free channels) shows none.
                    if showsMoviesTab, client.isPanel || !catalog.movies.isEmpty {
                        Tab("Films", systemImage: "film", value: RootTab.movies) {
                            NavigationStack { MoviesView() }
                        }
                    }
                    if showsSeriesTab, client.isPanel || !catalog.series.isEmpty {
                        Tab("Séries", systemImage: "tv", value: RootTab.series) {
                            NavigationStack { SeriesListView() }
                        }
                    }
                    Tab("Réglages", systemImage: "gearshape", value: RootTab.settings) {
                        NavigationStack { SettingsView() }
                    }
                    Tab(value: RootTab.search, role: .search) {
                        NavigationStack { SearchView() }
                    }
                }
                .tabViewStyle(.sidebarAdaptable)
                // Closed with the chevron on the picture only: a swipe down
                // would fight the channel list's own scrolling.
                .fullScreenCover(isPresented: Bindable(player).showsLive, onDismiss: player.liveScreenClosed) {
                    // Handed over again: on the Mac, the cover doesn't
                    // inherit them and the screen crashed on opening.
                    LiveWatchView()
                        .environment(account)
                        .environment(catalog)
                        .environment(library)
                        .environment(epg)
                        .environment(player)
                }
                .tabBarMinimizeBehavior(.onScrollDown)
                .onChange(of: showsGuide, initial: true) { if !showsGuide, tab == .guide { tab = .live } }
                .onChange(of: [showsMoviesTab, showsSeriesTab], initial: true) {
                    if (!showsMoviesTab && tab == .movies) || (!showsSeriesTab && tab == .series) { tab = .live }
                }
                .task(id: client.credentials) {
                    await catalog.start(client)
                    await account.refreshInfo()
                }
                .onChange(of: account.activeID) {
                    // Another account's channels: what played was this one's.
                    player.stop()
                }
                .onChange(of: scenePhase) { _, phase in
                    // Back after a long while: the catalogue may have moved.
                    if phase == .active, let updated = catalog.updated, Date().timeIntervalSince(updated) > 6 * 3600 {
                        Task { await catalog.refresh(client) }
                    }
                }
            } else {
                SignInView()
                    .transition(.opacity)
            }
        }
        .background(Color.screenBackground.ignoresSafeArea())
        .animation(Motion.smooth, value: account.credentials == nil)
        .onChange(of: [account.activeID?.uuidString, String(sharesFavorites)], initial: true) {
            library.use(account: account.activeID, sharesFavorites: sharesFavorites)
        }
        .onChange(of: account.credentials, initial: true) {
            CategoryLayouts.shared.account = account.credentials.map(Catalog.accountKey) ?? ""
        }
        .onChange(of: showsShifted) { catalog.optionsChanged() }
        .onChange(of: prefersFree) { catalog.optionsChanged() }
        .onChange(of: numbering) { catalog.optionsChanged() }
        .onChange(of: freeScope) { catalog.optionsChanged() }
        .task { openGuide = await OpenGuide.load() }
        .onChange(of: [catalog.guide.groups.count, openGuide.count]) {
            epg.useOpenGuide(openGuide, for: catalog.guide.groups)
        }
    }
}

enum RootTab: String, Hashable { case live, guide, movies, series, settings, search }

/// While the catalogue first loads, or when it couldn't.
struct CatalogState: View {
    @Environment(Catalog.self) private var catalog
    @Environment(AccountStore.self) private var account
    let empty: String

    var body: some View {
        if catalog.isLoading || catalog.isStarting {
            VStack(spacing: 14) {
                ProgressView().controlSize(.large)
                Text("Chargement du catalogue…").font(.subheadline).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error = catalog.error {
            ContentUnavailableView {
                Label("Impossible de charger", systemImage: "wifi.exclamationmark")
            } description: {
                Text(error)
            } actions: {
                Button("Réessayer") {
                    if let client = account.client { Task { await catalog.refresh(client) } }
                }
                .buttonStyle(.glassProminent)
            }
        } else {
            ContentUnavailableView {
                Label(empty, systemImage: "tray")
            } description: {
                Text("Le fournisseur n’a rien envoyé cette fois.")
            } actions: {
                Button("Réessayer") {
                    if let client = account.client { Task { await catalog.refresh(client) } }
                }
                .buttonStyle(.glass)
            }
        }
    }
}
