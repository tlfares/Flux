import SwiftUI

struct SignInView: View {
    @Environment(AccountStore.self) private var account
    @State private var draft = AccountDraft()
    @State private var isWorking = false
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 28) {
                VStack(spacing: 10) {
                    FluxLogo(size: 88)
                        .scaleEffect(isWorking ? 0.9 : 1)
                        .animation(.bouncy, value: isWorking)
                    Text("Flux")
                        .font(.system(size: 44, weight: .bold))
                    Text("Ajoute ton abonnement IPTV, ou regarde les chaînes gratuites.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 48)

                FreeChannelsButton()

                ImportButton()

                AccountFields(draft: $draft, submit: signIn)

                if let error {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }

                Button(action: signIn) {
                    ZStack {
                        Text("Se connecter").opacity(isWorking ? 0 : 1)
                        if isWorking { ProgressView() }
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                }
                .buttonStyle(.glassProminent)
                .controlSize(.large)
                .disabled(!draft.isComplete || isWorking)

                Text("Tes identifiants restent sur cet appareil, dans le trousseau.")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 20)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
            .animation(Motion.snappy, value: error)
        }
        .scrollDismissesKeyboard(.interactively)
        .scrollIndicators(.hidden)
        .background(Color.screenBackground.ignoresSafeArea())
        .sensoryFeedback(.error, trigger: error) { _, new in new != nil }
    }

    private func signIn() {
        guard draft.isComplete, !isWorking else { return }
        isWorking = true
        error = nil
        Task {
            do {
                try await account.add(draft.account)
            } catch {
                self.error = error.readableMessage
            }
            isWorking = false
        }
    }
}

/// The free channels, with nothing to type: for trying the app, or for
/// those without a subscription.
struct FreeChannelsButton: View {
    var onDone: (() -> Void)?
    @Environment(AccountStore.self) private var account

    var body: some View {
        Button {
            account.addFree()
            onDone?()
        } label: {
            HStack(spacing: 14) {
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.title2)
                    .foregroundStyle(.tint)
                    .frame(width: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Regarder les chaînes gratuites").font(.headline).foregroundStyle(.primary)
                    Text("TNT, info, France 3 régions et Pluto TV. Sans compte.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").font(.footnote.weight(.bold)).foregroundStyle(.tertiary)
            }
            .padding(.vertical, 6)
            .contentShape(.rect)
        }
        .buttonStyle(.glass)
        .controlSize(.large)
    }
}

/// An account being typed in: new, or a copy of one being changed.
struct AccountDraft: Equatable {
    var id = UUID()
    var name = ""
    var kind = SourceKind.xtream
    var server = ""
    var username = ""
    var password = ""

    init() {}

    init(_ account: Account) {
        id = account.id
        name = account.name
        kind = account.credentials.source
        server = account.credentials.server
        username = account.credentials.username
        password = account.credentials.password
    }

    var isComplete: Bool {
        switch kind {
        case .xtream: !server.isEmpty && !username.isEmpty && !password.isEmpty
        case .playlist: server.nonEmpty != nil
        case .free: true
        }
    }

    var account: Account {
        let credentials = switch kind {
        case .xtream: Credentials(server: server, username: username.trimmingCharacters(in: .whitespaces), password: password)
        case .playlist: Credentials.playlist(server)
        case .free: Credentials.free
        }
        return Account(id: id, name: name.trimmingCharacters(in: .whitespacesAndNewlines), credentials: credentials)
    }
}

/// Name, server, user and password, one under the other; a playlist's
/// link alone.
struct AccountFields: View {
    @Binding var draft: AccountDraft
    let submit: () -> Void
    @FocusState private var focus: Field?

    private enum Field { case name, server, username, password }

    var body: some View {
        VStack(spacing: 14) {
            if draft.kind != .free {
                Picker("Type", selection: $draft.kind) {
                    Text("Xtream Codes").tag(SourceKind.xtream)
                    Text("Lien M3U").tag(SourceKind.playlist)
                }
                .pickerStyle(.segmented)
            }
            GlassCard {
                VStack(spacing: 0) {
                    field("Nom", systemImage: "tag") {
                        TextField("Nom", text: $draft.name, prompt: Text("Facultatif, ex. « Maison »"))
                            .textInputAutocapitalization(.sentences)
                            .focused($focus, equals: .name)
                            .submitLabel(draft.kind == .free ? .done : .next)
                            .onSubmit { focus = draft.kind == .free ? nil : .server }
                    }
                    switch draft.kind {
                    case .xtream: panelFields
                    case .playlist: playlistField
                    case .free: EmptyView()
                    }
                }
                .autocorrectionDisabled()
            }
        }
        .animation(Motion.snappy, value: draft.kind)
    }

    private var playlistField: some View {
        Group {
            Divider().padding(.vertical, 12)
            field("Lien de la liste", systemImage: "list.bullet.rectangle") {
                TextField("Lien", text: $draft.server, prompt: Text("https://exemple.com/liste.m3u"), axis: .vertical)
                    .lineLimit(1...3)
                    .keyboardType(.URL)
                    .textContentType(.URL)
                    .textInputAutocapitalization(.never)
                    .focused($focus, equals: .server)
                    .submitLabel(.go)
                    .onSubmit {
                        focus = nil
                        submit()
                    }
            }
        }
    }

    @ViewBuilder private var panelFields: some View {
        Divider().padding(.vertical, 12)
        field("Serveur", systemImage: "server.rack") {
            TextField("Serveur", text: $draft.server, prompt: Text("exemple.com:8080"))
                .keyboardType(.URL)
                .textContentType(.URL)
                .textInputAutocapitalization(.never)
                .focused($focus, equals: .server)
                .submitLabel(.next)
                .onSubmit { focus = .username }
        }
        Divider().padding(.vertical, 12)
        field("Utilisateur", systemImage: "person") {
            TextField("Nom d’utilisateur", text: $draft.username)
                .textContentType(.username)
                .textInputAutocapitalization(.never)
                .focused($focus, equals: .username)
                .submitLabel(.next)
                .onSubmit { focus = .password }
        }
        Divider().padding(.vertical, 12)
        field("Mot de passe", systemImage: "key") {
            SecureField("Mot de passe", text: $draft.password)
                .textContentType(.password)
                .focused($focus, equals: .password)
                .submitLabel(.go)
                .onSubmit {
                    focus = nil
                    submit()
                }
        }
    }

    private func field<Content: View>(_ title: LocalizedStringKey, systemImage: String, @ViewBuilder content: () -> Content) -> some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .foregroundStyle(.tint)
                .frame(width: 24)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.caption).foregroundStyle(.secondary)
                content()
            }
        }
    }
}

/// Takes the account from another device nearby, rather than typing it all
/// in again: opens a code that device picks.
struct ImportButton: View {
    var onDone: (() -> Void)?
    @State private var isOpen = false
    @Environment(AccountStore.self) private var account
    @Environment(Library.self) private var library
    @Environment(Catalog.self) private var catalog

    var body: some View {
        Button {
            isOpen = true
        } label: {
            Label("Importer depuis un autre appareil", systemImage: "arrow.down.circle")
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
        }
        .buttonStyle(.glass)
        .controlSize(.large)
        .fullScreenCover(isPresented: $isOpen) {
            PairingCard(close: { isOpen = false }) { isOpen = false; onDone?() }
                .presentationBackground(.clear)
                // Handed over again: on the Mac, covers don't inherit them.
                .environment(account)
                .environment(library)
                .environment(catalog)
        }
        // A popup fades in over the page instead of sliding up.
        .transaction { $0.disablesAnimations = true }
    }
}

/// A code another device picks to send its account here.
struct PairingCard: View {
    var close: () -> Void
    var onDone: (() -> Void)?

    @Environment(AccountStore.self) private var account
    @Environment(Library.self) private var library
    @Environment(Catalog.self) private var catalog
    @State private var pairing = PairingReceiver()
    @State private var isWorking = false
    @State private var error: String?
    @State private var isShown = false

    var body: some View {
        ZStack {
            Color.black.opacity(isShown ? 0.45 : 0)
                .ignoresSafeArea()
                .onTapGesture(perform: dismiss)
            if isShown {
                VStack(spacing: 14) {
                    Image(systemName: "arrow.down.circle.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(FluxTint.horizontal)
                    Text("Importer un compte").font(.title3.weight(.bold))
                    Text("Sur l’appareil qui a le compte : Réglages › Comptes › Envoyer à un appareil, puis touche « \(Pairing.deviceKind) » avec ce code.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    Text(pairing.code)
                        .font(.system(size: 44, weight: .bold).monospacedDigit())
                        .tracking(12)
                        .foregroundStyle(FluxTint.horizontal)
                    if isWorking { ProgressView() }
                    if let error {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                    Text("Les deux appareils doivent être sur le même Wi-Fi.")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                    Button(action: dismiss) {
                        Text("Annuler").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 4)
                    }
                    .buttonStyle(.glass)
                    .controlSize(.large)
                }
                .padding(24)
                .frame(maxWidth: 340)
                .glassEffect(.regular, in: .rect(cornerRadius: 32))
                .padding(24)
                .transition(.scale(scale: 0.85).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.35, bounce: 0.25), value: isShown)
        .onAppear {
            isShown = true
            pairing.onMessage = { message in Task { await receive(message) } }
            pairing.start()
        }
        .onDisappear { pairing.stop() }
    }

    private func dismiss() {
        isShown = false
        Task {
            try? await Task.sleep(for: .milliseconds(250))
            close()
        }
    }

    private func receive(_ message: Pairing.Message) async {
        guard !isWorking else { return }
        isWorking = true
        error = nil
        defer { isWorking = false }
        do {
            try await Pairing.receive(message, account: account, library: library, catalog: catalog)
            onDone?()
        } catch {
            self.error = error.readableMessage
        }
    }
}
