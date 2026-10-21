import SwiftUI

struct SettingsView: View {
    @Environment(AccountStore.self) private var account
    @Environment(Catalog.self) private var catalog
    @AppStorage(AppAppearance.storageKey) private var appearance = AppAppearance.dark.rawValue
    @Environment(Library.self) private var library
    @AppStorage("showsEPG") private var showsEPG = true
    @AppStorage(QualityPreference.storageKey) private var quality = QualityPreference.best
    @AppStorage(FavoritesStyle.storageKey) private var favoritesStyle = FavoritesStyle.separate
    @AppStorage(FavoritesStyle.sharedKey) private var sharesFavorites = true
    @AppStorage(GuideOptions.shiftedKey) private var showsShifted = false
    @AppStorage(GuideOptions.prefersFreeKey) private var prefersFree = true
    @AppStorage(ChannelNumbering.storageKey) private var numbering = ChannelNumbering.default
    @AppStorage(LiveLayout.storageKey) private var liveLayout = LiveLayout.list
    // Which sections are open, kept between visits. State as in Screenloss:
    // through AppStorage, the cards below jumped instead of sliding along.
    @State private var playbackOpen = UserDefaults.standard.bool(forKey: "settings.open.playback")
    @State private var displayOpen = UserDefaults.standard.bool(forKey: "settings.open.display")
    @State private var favoritesOpen = UserDefaults.standard.bool(forKey: "settings.open.favorites")
    @State private var dataOpen = UserDefaults.standard.bool(forKey: "settings.open.data")
    @State private var managesFavorites = false
    @State private var managesAccounts = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                accountCard

                CollapsibleGlassCard(title: "Lecture", systemImage: "play.rectangle", isExpanded: $playbackOpen) {
                    VStack(alignment: .leading, spacing: 14) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Qualité par défaut").font(.subheadline).foregroundStyle(.secondary)
                            GlassSegmentedPicker("Qualité par défaut", options: QualityPreference.allCases, selection: $quality,
                                                 title: \.title, symbol: \.symbol)
                            Text("Quand une chaîne existe en plusieurs qualités. Celle choisie à la main pour une chaîne reste la sienne.")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        Divider()
                        HStack {
                            Text("Numérotation").font(.subheadline)
                            Spacer()
                            Picker("Numérotation", selection: $numbering) {
                                ForEach(ChannelNumbering.allCases, id: \.self) { Text($0.title).tag($0) }
                            }
                            .pickerStyle(.menu)
                            .buttonStyle(.glass)
                        }
                        Text(numbering.subtitle + (numbering == .provider ? "" : " Les autres chaînes suivent."))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        Divider()
                        Toggle("Chaînes en différé", isOn: $showsShifted)
                            .font(.subheadline)
                        Text("Les versions décalées (« TF1 +4 », diffusée 4 h plus tard) se choisissent depuis leur chaîne au lieu d’être masquées.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        if account.credentials?.source != .free {
                            Divider()
                            Toggle("Flux gratuits en priorité", isOn: $prefersFree)
                                .font(.subheadline)
                            Text("France 2, Arte, BFM TV… se lisent depuis le flux officiel de la chaîne, plus stable, sans occuper un écran de ton abonnement. Celui de l’abonnement prend le relais s’il ne répond pas.")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                CollapsibleGlassCard(title: "Affichage", systemImage: "paintpalette", isExpanded: $displayOpen) {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Mode").font(.subheadline).foregroundStyle(.secondary)
                        GlassSegmentedPicker(
                            "Apparence",
                            options: AppAppearance.allCases,
                            selection: Binding(
                                get: { AppAppearance(rawValue: appearance) ?? .dark },
                                set: { appearance = $0.rawValue }
                            ),
                            title: \.title,
                            symbol: \.symbol,
                            commitsWhenSettled: true
                        )
                        Divider()
                        Text("Chaînes").font(.subheadline).foregroundStyle(.secondary)
                        GlassSegmentedPicker("Affichage des chaînes", options: LiveLayout.allCases, selection: $liveLayout,
                                             title: \.title, symbol: \.symbol)
                        Text(liveLayout == .list
                             ? "Une ligne par chaîne, la plus compacte."
                             : "Des cartes avec l’image du programme, comme sur l’Apple TV.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        Divider()
                        Toggle("Programme en cours", isOn: $showsEPG)
                            .font(.subheadline)
                        Text("Affiche l’émission diffusée sous chaque chaîne, quand le fournisseur a un guide.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                CollapsibleGlassCard(title: "Favoris", systemImage: "star", isExpanded: $favoritesOpen) {
                    VStack(alignment: .leading, spacing: 14) {
                        GlassSegmentedPicker("Affichage des favoris", options: FavoritesStyle.allCases, selection: $favoritesStyle,
                                             title: \.title, symbol: \.symbol)
                        Text(favoritesStyle == .separate
                             ? "Une catégorie Favoris en tête des catégories."
                             : "En haut de chaque liste où ils se trouvent.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        Divider()
                        Toggle("Communs à tous les comptes", isOn: $sharesFavorites)
                            .font(.subheadline)
                        Text(sharesFavorites
                             ? "Une seule liste, retrouvée par nom chez chaque fournisseur."
                             : "Chaque compte a sa propre liste.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        Button {
                            managesFavorites = true
                        } label: {
                            Label("Organiser les favoris", systemImage: "list.bullet")
                                .font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 4)
                        }
                        .buttonStyle(.glass)
                        .disabled(library.favorites.isEmpty)
                    }
                }

                CollapsibleGlassCard(title: "Catalogue", systemImage: "square.stack", isExpanded: $dataOpen) {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(catalogSummary)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .contentTransition(.numericText())
                        Button {
                            if let client = account.client { Task { await catalog.refresh(client) } }
                        } label: {
                            HStack {
                                if catalog.isLoading { ProgressView() } else { Image(systemName: "arrow.clockwise") }
                                Text("Actualiser")
                            }
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 4)
                        }
                        .buttonStyle(.glass)
                        .disabled(catalog.isLoading)
                    }
                }

                Text(Self.versionText)
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 12)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .background(Color.screenBackground.ignoresSafeArea())
        .navigationTitle("Réglages")
        .onChange(of: playbackOpen) { UserDefaults.standard.set($1, forKey: "settings.open.playback") }
        .onChange(of: displayOpen) { UserDefaults.standard.set($1, forKey: "settings.open.display") }
        .onChange(of: favoritesOpen) { UserDefaults.standard.set($1, forKey: "settings.open.favorites") }
        .onChange(of: dataOpen) { UserDefaults.standard.set($1, forKey: "settings.open.data") }
        // Handed over again: on the Mac, sheets don't inherit them.
        .sheet(isPresented: $managesFavorites) { FavoritesEditor().environment(library) }
        .sheet(isPresented: $managesAccounts) {
            AccountsView().environment(account).environment(catalog).environment(library)
        }
    }

    private var accountCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.system(size: 38))
                        .foregroundStyle(.tint)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(account.active?.title ?? "—").font(.headline)
                        Text(account.active?.subtitle ?? "")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer()
                    if let status = account.info?.status {
                        Text(status == "Active" ? "Actif" : status)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(status == "Active" ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                            .padding(.horizontal, 11)
                            .padding(.vertical, 7)
                            .glassEffect(.regular, in: .capsule)
                    }
                }
                Button {
                    managesAccounts = true
                } label: {
                    Label(account.accounts.count > 1 ? "Changer de compte" : "Comptes", systemImage: "person.2")
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.glass)
                if let info = account.info, account.client?.isPanel == true {
                    Divider()
                    HStack {
                        figure(info.expiration.map { $0.formatted(date: .abbreviated, time: .omitted) } ?? "Illimité", "Expire")
                        figure("\(info.activeConnections ?? 0) / \(info.maxConnections ?? 1)", "Connexions")
                    }
                }
            }
        }
    }

    private func figure(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.title3.weight(.semibold).monospacedDigit())
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var catalogCounts: String {
        "\((catalog.guide.isEmpty ? catalog.live.items.count : catalog.guide.groups.count).formatted()) chaînes · \(catalog.movies.items.count.formatted()) films · \(catalog.series.items.count.formatted()) séries"
    }

    private var catalogSummary: String {
        let counts = "\((catalog.guide.isEmpty ? catalog.live.items.count : catalog.guide.groups.count).formatted()) chaînes · \(catalog.movies.items.count.formatted()) films · \(catalog.series.items.count.formatted()) séries"
        guard let updated = catalog.updated else { return counts }
        return counts + "\nMis à jour " + updated.formatted(.relative(presentation: .named))
    }

    private static var versionText: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String ?? "1"
        return "Flux \(version) (\(build))"
    }
}

/// Reorders and removes favorites, kind by kind.
private struct FavoritesEditor: View {
    @Environment(Library.self) private var library
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                section(.live, "Chaînes")
                section(.movie, "Films")
                section(.series, "Séries")
            }
            .environment(\.editMode, .constant(.active))
            .navigationTitle("Favoris")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(role: .confirm) { dismiss() }
                }
            }
        }
    }

    @ViewBuilder
    private func section(_ kind: MediaKind, _ title: String) -> some View {
        let items = library.favorites(kind)
        if !items.isEmpty {
            Section(title) {
                ForEach(items) { favorite in
                    HStack(spacing: 12) {
                        RemoteImage(url: favorite.image, size: 80, contentMode: kind == .live ? .fit : .fill) {
                            Rectangle().fill(.white.opacity(0.06))
                        }
                        .frame(width: kind == .live ? 44 : 30, height: kind == .live ? 30 : 44)
                        .clipShape(.rect(cornerRadius: 6))
                        Text(favorite.name).lineLimit(1)
                    }
                }
                .onMove { library.moveFavorites(kind, from: $0, to: $1) }
                .onDelete { library.removeFavorites(kind, at: $0) }
            }
        }
    }
}
