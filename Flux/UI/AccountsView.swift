import SwiftUI

/// The accounts: the one in use, switching, adding, changing a login.
struct AccountsView: View {
    @Environment(AccountStore.self) private var account
    @Environment(Library.self) private var library
    @Environment(\.dismiss) private var dismiss
    @State private var path: [AccountRoute] = []
    @State private var pairing = PairingSender()

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    ForEach(account.accounts) { item in
                        HStack(spacing: 12) {
                            Button {
                                account.select(item.id)
                                dismiss()
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: item.id == account.activeID ? "checkmark.circle.fill" : "circle")
                                        .font(.title3)
                                        .foregroundStyle(item.id == account.activeID ? AnyShapeStyle(.tint) : AnyShapeStyle(.tertiary))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.title).font(.body.weight(.semibold)).foregroundStyle(.primary)
                                        Text(item.subtitle)
                                            .font(.footnote)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                }
                                .contentShape(.rect)
                            }
                            .buttonStyle(.plain)

                            Button {
                                path.append(.edit(item.id))
                            } label: {
                                Image(systemName: "pencil")
                                    .font(.body.weight(.semibold))
                                    .frame(width: 36, height: 36)
                            }
                            .buttonStyle(.plain)
                            .glassEffect(.regular.interactive(), in: .circle)
                            .accessibilityLabel("Modifier \(item.title)")
                        }
                    }
                } footer: {
                    Text("Touche un compte pour l’utiliser. Chacun garde son catalogue : revenir à un compte est immédiat.")
                }

                if let active = account.active {
                    Section {
                        if pairing.screens.isEmpty {
                            HStack(spacing: 12) {
                                ProgressView()
                                Text("Recherche des appareils à proximité…").foregroundStyle(.secondary)
                            }
                        }
                        ForEach(pairing.screens) { screen in
                            Button {
                                pairing.send(active, library: library.snapshot(), to: screen)
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: Pairing.symbol(screen.kind)).font(.title3).foregroundStyle(.tint)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(screen.kind).font(.body.weight(.semibold)).foregroundStyle(.primary)
                                        Text("Code \(screen.code)").font(.footnote.monospacedDigit()).foregroundStyle(.secondary)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    if pairing.sending == screen.code {
                                        ProgressView()
                                    } else if pairing.failed == screen.code {
                                        Label("Injoignable", systemImage: "exclamationmark.triangle.fill")
                                            .font(.footnote.weight(.semibold))
                                            .foregroundStyle(.orange)
                                    } else if pairing.sent == screen.code {
                                        Label("Envoyé", systemImage: "checkmark.circle.fill")
                                            .font(.footnote.weight(.semibold))
                                            .foregroundStyle(.tint)
                                    }
                                }
                                .contentShape(.rect)
                            }
                            .buttonStyle(.plain)
                        }
                    } header: {
                        Text("Envoyer à un appareil")
                    } footer: {
                        Text("Sur l’autre appareil (iPhone, iPad, Mac, Android, Apple TV, Android TV), touche « Importer depuis un autre appareil » à la connexion, puis touche ici celui dont le code s’affiche à l’écran pour y envoyer « \(active.title) », avec tes favoris, tes reprises, tes chaînes récentes et tes réglages.")
                    }
                }
            }
            .onAppear { pairing.start() }
            .onDisappear { pairing.stop() }
            .navigationTitle("Comptes")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(role: .close) { dismiss() }
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button("Ajouter", systemImage: "plus") { path.append(.add) }
                }
            }
            .navigationDestination(for: AccountRoute.self) { route in
                switch route {
                case .add: AccountEditView(original: nil)
                case .edit(let id): AccountEditView(original: account.accounts.first { $0.id == id })
                }
            }
        }
    }
}

enum AccountRoute: Hashable {
    case add
    case edit(UUID)
}

/// A new account, or one whose name or login changes. A login is checked
/// against the server before it's kept.
struct AccountEditView: View {
    let original: Account?

    @Environment(AccountStore.self) private var account
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @Environment(\.dismiss) private var dismiss
    @State private var draft = AccountDraft()
    @State private var isWorking = false
    @State private var error: String?
    @State private var confirmsRemoval = false

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if original == nil {
                    if !account.accounts.contains(where: { $0.credentials.source == .free }) {
                        FreeChannelsButton { dismiss() }
                    }
                    ImportButton { dismiss() }
                }
                AccountFields(draft: $draft, submit: save)

                if let error {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Button(action: save) {
                    ZStack {
                        Text(original == nil ? "Ajouter" : "Enregistrer").opacity(isWorking ? 0 : 1)
                        if isWorking { ProgressView() }
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                }
                .buttonStyle(.glassProminent)
                .disabled(!draft.isComplete || isWorking || (original.map { draft == AccountDraft($0) } ?? false))

                if let original {
                    Button(role: .destructive) {
                        confirmsRemoval = true
                    } label: {
                        Label("Supprimer ce compte", systemImage: "trash")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.glass)
                    .tint(.red)
                    .confirmationDialog("Supprimer « \(original.title) » ?", isPresented: $confirmsRemoval, titleVisibility: .visible) {
                        Button("Supprimer", role: .destructive) { remove(original) }
                    } message: {
                        Text("Ses identifiants et son catalogue en cache seront effacés de cet appareil.")
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .animation(Motion.snappy, value: error)
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle(original == nil ? String(localized: "Nouveau compte") : original!.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { if let original { draft = AccountDraft(original) } }
        .sensoryFeedback(.error, trigger: error) { _, new in new != nil }
    }

    private func save() {
        guard draft.isComplete, !isWorking else { return }
        isWorking = true
        error = nil
        Task {
            do {
                if original == nil {
                    try await account.add(draft.account)
                } else if let original {
                    try await account.update(draft.account)
                    // Another server or user: the old catalogue is no one's.
                    if Catalog.accountKey(original.credentials) != Catalog.accountKey(draft.account.credentials) {
                        catalog.forget(original.credentials)
                    }
                }
                dismiss()
            } catch {
                self.error = error.readableMessage
            }
            isWorking = false
        }
    }

    private func remove(_ item: Account) {
        catalog.forget(item.credentials)
        library.forget(account: item.id)
        account.remove(item.id)
        dismiss()
    }
}
