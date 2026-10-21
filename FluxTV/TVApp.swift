import SwiftUI

@main
struct FluxTVApp: App {
    @State private var account = AccountStore()
    @State private var catalog = Catalog()
    @State private var library = Library()
    @State private var epg = EPG()
    @State private var player = TVPlayer()

    var body: some Scene {
        WindowGroup {
            TVRootView()
                .environment(account)
                .environment(catalog)
                .environment(library)
                .environment(epg)
                .environment(player)
                .tint(FluxTint.blue)
                .preferredColorScheme(.dark)
                .task { player.library = library }
        }
    }
}

enum TVTab: String, Hashable { case home, live, guide, movies, series, search, settings }

struct TVRootView: View {
    @Environment(AccountStore.self) private var account
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @Environment(TVPlayer.self) private var player
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage(FavoritesStyle.sharedKey) private var sharesFavorites = true
    @AppStorage(GuideOptions.shiftedKey) private var showsShifted = false
    @AppStorage(GuideOptions.prefersFreeKey) private var prefersFree = true
    @AppStorage(ChannelNumbering.storageKey) private var numbering = ChannelNumbering.default
    @Environment(EPG.self) private var epg
    @State private var tab = TVTab.home
    @State private var openGuide: [String: [Programme]] = [:]

    /// Words alone, as in Apple's TV app: with icons, seven tabs
    /// overflowed the bar.
    private var tabs: some View {
        // A playlist without films or series (the free channels) shows none.
        let isPanel = account.client?.isPanel ?? true
        return TabView(selection: $tab) {
            Tab(value: TVTab.home) { TVHomeView() } label: { Text("Accueil") }
            Tab(value: TVTab.live) { TVChannelsView() } label: { Text("Direct") }
            Tab(value: TVTab.guide) { TVGuideView() } label: { Text("Guide") }
            if isPanel || !catalog.movies.isEmpty {
                Tab(value: TVTab.movies) { TVMoviesView() } label: { Text("Films") }
            }
            if isPanel || !catalog.series.isEmpty {
                Tab(value: TVTab.series) { TVSeriesView() } label: { Text("Séries") }
            }
            Tab(value: TVTab.settings) { TVSettingsView() } label: { Text("Réglages") }
            Tab(value: TVTab.search, role: .search) { TVSearchView() }
        }
    }

    var body: some View {
        Group {
            if let client = account.client {
                tabs
                .fullScreenCover(isPresented: Bindable(player).showsLive, onDismiss: { if player.channel != nil { player.closeLive() } }) {
                    TVLiveScreen()
                }
                .fullScreenCover(item: Bindable(player).video, onDismiss: { player.closeVideo() }) { _ in
                    SystemPlayer(player: player.player).ignoresSafeArea()
                }
                .task(id: client.credentials) {
                    await catalog.start(client)
                    await account.refreshInfo()
                }
                .onChange(of: account.activeID) { player.closeLive() }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active, let updated = catalog.updated, Date().timeIntervalSince(updated) > 6 * 3600 {
                        Task { await catalog.refresh(client) }
                    }
                }
            } else {
                TVSignInView()
            }
        }
        .onChange(of: [account.activeID?.uuidString, String(sharesFavorites)], initial: true) {
            library.use(account: account.activeID, sharesFavorites: sharesFavorites)
        }
        .onChange(of: showsShifted) { catalog.optionsChanged() }
        .onChange(of: prefersFree) { catalog.optionsChanged() }
        .onChange(of: numbering) { catalog.optionsChanged() }
        .task { openGuide = await OpenGuide.load() }
        .onChange(of: [catalog.guide.groups.count, openGuide.count]) {
            epg.useOpenGuide(openGuide, for: catalog.guide.groups)
        }
    }
}

/// The first screen, and adding an account later: a provider's server and
/// login, checked before they're kept.
struct TVSignInView: View {
    var onDone: (() -> Void)?

    @Environment(AccountStore.self) private var account
    @Environment(Library.self) private var library
    @Environment(Catalog.self) private var catalog
    @State private var name = ""
    @State private var server = ""
    @State private var username = ""
    @State private var password = ""
    @State private var kind = SourceKind.xtream
    @State private var isChecking = false
    @State private var error: String?
    @State private var pairing = PairingReceiver()

    var body: some View {
        HStack(spacing: 100) {
            VStack(alignment: .leading, spacing: 20) {
                FluxLogo(size: 120)
                Text("Flux").font(.system(size: 76, weight: .bold))
                Text("Connecte ton abonnement IPTV (Xtream Codes ou lien M3U), ou regarde les chaînes gratuites.")
                    .font(.callout)
                    .foregroundStyle(.secondary)

                Button {
                    account.addFree()
                    onDone?()
                } label: {
                    Label("Regarder les chaînes gratuites", systemImage: "antenna.radiowaves.left.and.right")
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.top, 10)

                // Quicker than the remote: the iPhone sends its account.
                VStack(alignment: .leading, spacing: 14) {
                    Label("Depuis ton iPhone", systemImage: "iphone.and.arrow.forward")
                        .font(.headline)
                    Text("Dans Flux sur l’iPhone : Réglages › Comptes › Envoyer à une TV ou un Mac, puis touche celle avec ce code.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                    Text(pairing.code)
                        .font(.system(size: 64, weight: .bold).monospacedDigit())
                        .tracking(14)
                        .foregroundStyle(FluxTint.blue)
                }
                .padding(32)
                .frame(maxWidth: .infinity, alignment: .leading)
                .glassEffect(.regular, in: .rect(cornerRadius: 32))
                .padding(.top, 30)
            }
            .frame(width: 640, alignment: .leading)

            VStack(spacing: 24) {
                Picker("Type", selection: $kind) {
                    Text("Xtream Codes").tag(SourceKind.xtream)
                    Text("Lien M3U").tag(SourceKind.playlist)
                }
                .pickerStyle(.segmented)
                if kind == .playlist {
                    TextField("Lien de la liste (https://exemple.com/liste.m3u)", text: $server)
                        .textContentType(.URL)
                        .keyboardType(.URL)
                } else {
                    TextField("Serveur (http://exemple.com:8080)", text: $server)
                        .textContentType(.URL)
                        .keyboardType(.URL)
                    TextField("Identifiant", text: $username)
                        .textContentType(.username)
                    SecureField("Mot de passe", text: $password)
                        .textContentType(.password)
                }
                TextField("Nom du compte (facultatif)", text: $name)
                if let error {
                    Text(error).font(.callout).foregroundStyle(.red).frame(maxWidth: .infinity, alignment: .leading)
                }
                Button {
                    Task { await signIn() }
                } label: {
                    HStack(spacing: 16) {
                        if isChecking { ProgressView() }
                        Text(isChecking ? "Vérification…" : "Se connecter")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(isChecking || server.nonEmpty == nil || (kind == .xtream && (username.nonEmpty == nil || password.isEmpty)))
            }
            .frame(width: 820)
        }
        .padding(80)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background {
            ZStack {
                Color.black
                RadialGradient(colors: [FluxTint.blue.opacity(0.35), .clear], center: .topLeading, startRadius: 0, endRadius: 1400)
            }
            .ignoresSafeArea()
        }
        .onAppear {
            pairing.onMessage = { message in Task { await receive(message) } }
            pairing.start()
        }
        .onDisappear { pairing.stop() }
    }

    /// From the iPhone: the account, checked against the server like a
    /// typed one (or the one already here with that login), then what goes
    /// with it merged into what's here.
    private func receive(_ message: Pairing.Message) async {
        guard !isChecking else { return }
        isChecking = true
        error = nil
        defer { isChecking = false }
        do {
            try await Pairing.receive(message, account: account, library: library, catalog: catalog)
            onDone?()
        } catch {
            self.error = error.readableMessage
        }
    }

    private func signIn() async {
        isChecking = true
        error = nil
        defer { isChecking = false }
        let credentials = kind == .playlist ? Credentials.playlist(server) :
            Credentials(server: server.trimmingCharacters(in: .whitespacesAndNewlines),
                        username: username.trimmingCharacters(in: .whitespacesAndNewlines),
                        password: password)
        do {
            try await account.add(Account(name: name.trimmingCharacters(in: .whitespacesAndNewlines), credentials: credentials))
            onDone?()
        } catch {
            self.error = error.readableMessage
        }
    }
}

struct TVSettingsView: View {
    @Environment(AccountStore.self) private var account
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @AppStorage(QualityPreference.storageKey) private var quality = QualityPreference.best
    @AppStorage(ChannelNumbering.storageKey) private var numbering = ChannelNumbering.default
    @AppStorage(GuideOptions.shiftedKey) private var showsShifted = false
    @AppStorage(GuideOptions.prefersFreeKey) private var prefersFree = true
    @State private var addsAccount = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Compte") {
                    // The list of accounts one level down: the page opens on what's used most.
                    NavigationLink {
                        Form { accountsSection }.frame(maxWidth: 1200).navigationTitle("Comptes")
                    } label: {
                        TVSettingsLabel {
                            Text("Compte")
                            Spacer()
                            Text(account.active?.title ?? "—").foregroundStyle(.secondary)
                        }
                    }
                    if let info = account.info, let expiration = info.expiration, account.client?.isPanel == true {
                        LabeledContent("Expire le", value: expiration.formatted(date: .long, time: .omitted))
                    }
                }

                Section("Lecture") {
                    Picker("Qualité", selection: $quality) {
                        ForEach(QualityPreference.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    Picker("Numérotation", selection: $numbering) {
                        ForEach(ChannelNumbering.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    // A row rather than a Toggle: its tint went white on the
                    // focused white cell.
                    Button { showsShifted.toggle() } label: {
                        TVSettingsLabel {
                            Text("Chaînes en différé (+1 h, +2 h…)")
                            Spacer()
                            Text(showsShifted ? "Oui" : "Non").foregroundStyle(.secondary)
                        }
                    }
                    if account.credentials?.source != .free {
                        Button { prefersFree.toggle() } label: {
                            TVSettingsLabel {
                                Text("Flux gratuits en priorité (France 2, Arte…)")
                                Spacer()
                                Text(prefersFree ? "Oui" : "Non").foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                Section("Catalogue") {
                    Button {
                        if let client = account.client { Task { await catalog.refresh(client) } }
                    } label: {
                        TVSettingsLabel {
                            Text("Actualiser le catalogue")
                            Spacer()
                            if catalog.isLoading { ProgressView() }
                            else if let updated = catalog.updated {
                                Text(updated.formatted(.relative(presentation: .named))).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: 1200)
            .fullScreenCover(isPresented: $addsAccount) {
                TVSignInView { addsAccount = false }
            }
        }
    }

    @ViewBuilder private var accountsSection: some View {
        Section {
            ForEach(account.accounts) { item in
                Button {
                    account.select(item.id)
                } label: {
                    TVSettingsLabel {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.title)
                            Text(item.subtitle).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if item.id == account.activeID { Image(systemName: "checkmark") }
                    }
                }
                .contextMenu {
                    Button("Supprimer le compte", systemImage: "trash", role: .destructive) {
                        catalog.forget(item.credentials)
                        library.forget(account: item.id)
                        account.remove(item.id)
                    }
                }
            }
            Button("Ajouter un compte ou recevoir depuis un autre appareil", systemImage: "plus") { addsAccount = true }
        } footer: {
            Text("Maintiens un compte pour le supprimer.")
        }
    }
}

/// A settings row's content: white, dark once the cell under it turns
/// white with focus (a tint would stay white on white).
private struct TVSettingsLabel<Content: View>: View {
    @ViewBuilder let content: Content
    @Environment(\.isFocused) private var isFocused

    var body: some View {
        HStack { content }.foregroundStyle(isFocused ? Color.black : Color.white)
    }
}
