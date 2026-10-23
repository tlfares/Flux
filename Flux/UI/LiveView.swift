import SwiftUI

/// The channels a category chip stands for, favorites pinned or apart.
@MainActor
struct LiveListing {
    let selection: String
    let catalog: Catalog
    let library: Library
    let style: FavoritesStyle

    var favorites: [ChannelGroup] {
        library.favorites(.live).compactMap { catalog.guide.group($0.key) }
    }

    var groups: Pinned<ChannelGroup> {
        let guide = catalog.guide
        switch selection {
        case CategoryBar.favorites where style == .separate:
            return Pinned(favorites, favorites: [])
        case CategoryBar.recent:
            return Pinned(library.recentChannels.compactMap { guide.group($0) }, favorites: [])
        case CategoryBar.all, CategoryBar.favorites:
            return Pinned(guide.groups, favorites: style == .pinned ? favorites : [])
        default:
            return Pinned(guide.groups(in: selection), favorites: style == .pinned ? favorites : [])
        }
    }
}

struct LiveView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @Environment(AccountStore.self) private var account
    @Environment(PlayerCenter.self) private var player
    @AppStorage("live.category") private var selection = CategoryBar.all
    @AppStorage(FavoritesStyle.storageKey) private var style = FavoritesStyle.separate
    @AppStorage(LiveLayout.storageKey) private var layout = LiveLayout.list

    private var columns: [GridItem] {
        layout == .cards ? [GridItem(.adaptive(minimum: 160), spacing: 12, alignment: .top)] : [GridItem(.adaptive(minimum: 300), spacing: 12)]
    }

    var body: some View {
        let groups = LiveListing(selection: selection, catalog: catalog, library: library, style: style).groups
        ScrollView {
            // The chips open the list, under the title, and scroll away
            // with it: pinned beside the list, the title slid under them.
            LazyVStack(spacing: 0) {
                if !catalog.live.isEmpty {
                    CategoryBar(categories: catalog.live.categories, kind: .live, selection: $selection, showsRecent: true, showsFavorites: style == .separate)
                }
                if catalog.live.isEmpty {
                    CatalogState(empty: String(localized: "Aucune chaîne")).containerRelativeFrame(.vertical)
                } else if catalog.guide.isEmpty {
                    ProgressView().controlSize(.large).containerRelativeFrame(.vertical) { h, _ in h - CategoryBar.height }
                } else if groups.isEmpty {
                    LiveEmptySelection(selection: selection).containerRelativeFrame(.vertical) { h, _ in h - CategoryBar.height }
                } else {
                    LazyVGrid(columns: columns, spacing: layout == .cards ? 16 : 8) {
                        if !groups.top.isEmpty {
                            Section {
                                rows(groups.top)
                            } header: {
                                ListHeader(title: "Favoris", systemImage: "star.fill")
                            }
                            Section {
                                rows(groups.rest)
                            } header: {
                                ListHeader(title: "Chaînes", systemImage: nil).padding(.top, 8)
                            }
                        } else {
                            rows(groups.rest)
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
        .navigationTitle("Direct")
    }

    private func rows(_ groups: [ChannelGroup]) -> some View {
        ForEach(groups) { group in
            let play: (ChannelVariant?) -> Void = { variant in
                if let client = account.client { player.watch(group, variant: variant, client: client) }
            }
            if layout == .cards {
                ChannelCard(group: group, isPlaying: player.liveGroup?.id == group.id, play: play)
            } else {
                ChannelRow(group: group, isPlaying: player.liveGroup?.id == group.id, play: play)
            }
        }
    }
}

/// How the Direct tab lays channels out: rows, or tiles as on the TV.
enum LiveLayout: String, CaseIterable {
    case list
    case cards

    var title: String {
        switch self {
        case .list: String(localized: "Liste")
        case .cards: String(localized: "Cartes")
        }
    }

    var symbol: String {
        switch self {
        case .list: "list.bullet"
        case .cards: "square.grid.2x2"
        }
    }

    static let storageKey = "live.layout"
}

/// A channel as the TV app shows it: what's on as a still (or the logo's
/// colors), the logo and number over it, the title under it.
struct ChannelCard: View {
    let group: ChannelGroup
    var isPlaying = false
    let play: (ChannelVariant?) -> Void

    @Environment(EPG.self) private var epg
    @Environment(Library.self) private var library
    @Environment(AccountStore.self) private var account

    var body: some View {
        let favorite = Favorite.live(group)
        let now = epg.now(epg.source(group))
        Button { play(nil) } label: {
            VStack(alignment: .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .top) {
                        RemoteImage(url: group.icon, size: 96, contentMode: .fit) {
                            Text(group.name.prefix(3).uppercased()).font(.headline.weight(.heavy)).foregroundStyle(.white.opacity(0.7))
                        }
                        .frame(width: 52, height: 30)
                        .shadow(color: .black.opacity(0.5), radius: 4)
                        Spacer()
                        if let number = group.number {
                            Text("\(number)")
                                .font(.subheadline.weight(.bold).monospacedDigit())
                                .foregroundStyle(.white.opacity(0.9))
                                .shadow(color: .black.opacity(0.6), radius: 4)
                        }
                    }
                    Spacer(minLength: 0)
                    HStack(spacing: 6) {
                        if isPlaying {
                            Image(systemName: "waveform")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.tint)
                                .symbolEffect(.variableColor.iterative, options: .repeating)
                        }
                        if library.isFavorite(favorite) {
                            Image(systemName: "star.fill").font(.caption2).foregroundStyle(.tint)
                        }
                    }
                    if let now {
                        ProgressView(timerInterval: now.start...now.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                            .progressViewStyle(.linear)
                            .tint(.accentColor)
                            .padding(.top, 6)
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .topLeading)
                .aspectRatio(16 / 9, contentMode: .fit)
                .background { CardBackdrop(group: group, image: now?.image) }
                .clipShape(.rect(cornerRadius: 16))
                .overlay {
                    RoundedRectangle(cornerRadius: 16).strokeBorder(isPlaying ? AnyShapeStyle(.tint) : AnyShapeStyle(.white.opacity(0.08)), lineWidth: isPlaying ? 2 : 1)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(now?.title ?? group.name)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Text(now.map { "\(group.name) · \($0.timeRange)" } ?? "En direct")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 2)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .contextMenu {
            FavoriteButton(favorite: favorite)
            if group.hasChoice {
                Section("Regarder en") {
                    ForEach(group.variants) { variant in
                        Button(variant.quality.label) { play(variant) }
                    }
                }
            }
        }
        .task(id: group.id) {
            guard let client = account.client else { return }
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            await epg.load(group, client: client)
        }
    }
}

/// The programme's still, else the logo's colors blurred.
private struct CardBackdrop: View {
    let group: ChannelGroup
    let image: String?

    var body: some View {
        LinearGradient(colors: [Color(white: 0.17), Color(white: 0.08)], startPoint: .topLeading, endPoint: .bottomTrailing)
            .overlay {
                RemoteImage(url: group.icon, size: 96, contentMode: .fill) { Color.clear }
                    .scaleEffect(1.6)
                    .blur(radius: 30)
                    .opacity(0.45)
            }
            .overlay {
                if let image {
                    RemoteImage(url: image, size: 400, contentMode: .fill) { Color.clear }
                        .overlay {
                            LinearGradient(colors: [.black.opacity(0.55), .clear, .black.opacity(0.6)], startPoint: .top, endPoint: .bottom)
                        }
                }
            }
            .clipped()
    }
}

struct ListHeader: View {
    let title: String
    let systemImage: String?

    var body: some View {
        HStack(spacing: 6) {
            if let systemImage { Image(systemName: systemImage).foregroundStyle(.tint) }
            Text(title)
        }
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
    }
}

struct LiveEmptySelection: View {
    let selection: String

    var body: some View {
        switch selection {
        case CategoryBar.favorites:
            ContentUnavailableView("Aucun favori", systemImage: "star", description: Text("Maintiens une chaîne pour l’ajouter."))
        case CategoryBar.recent:
            ContentUnavailableView("Rien de récent", systemImage: "clock", description: Text("Les chaînes regardées apparaîtront ici."))
        default:
            ContentUnavailableView("Catégorie vide", systemImage: "tray")
        }
    }
}

/// A channel: its logo, what's on, and the quality it comes in. A tap
/// plays the preferred stream; the menu picks another.
struct ChannelRow: View {
    let group: ChannelGroup
    var isPlaying = false
    let play: (ChannelVariant?) -> Void

    @Environment(EPG.self) private var epg
    @Environment(Library.self) private var library
    @Environment(AccountStore.self) private var account
    @AppStorage("showsEPG") private var showsEPG = true

    var body: some View {
        let favorite = Favorite.live(group)
        let epgID = epg.source(group)
        Button { play(nil) } label: {
            HStack(spacing: 12) {
                if let number = group.number {
                    Text("\(number)")
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                        .foregroundStyle(.secondary)
                        .frame(minWidth: 28, alignment: .center)
                        .padding(.leading, 2)
                        .padding(.trailing, -4)
                }
                ChannelLogo(group: group)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(group.name)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        if library.isFavorite(favorite) {
                            Image(systemName: "star.fill").font(.caption2).foregroundStyle(.tint)
                        }
                    }
                    if showsEPG, let now = epg.now(epgID) {
                        Text(now.title)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        ProgressView(timerInterval: now.start...now.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                            .progressViewStyle(.linear)
                            .tint(.accentColor)
                            .scaleEffect(y: 0.5, anchor: .top)
                            .padding(.trailing, 8)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if isPlaying {
                    Image(systemName: "waveform")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.tint)
                        .symbolEffect(.variableColor.iterative, options: .repeating)
                } else {
                    QualityBadge(group: group)
                }
            }
            .padding(8)
            .padding(.trailing, 6)
            .frame(minHeight: 60)
            .contentShape(.rect(cornerRadius: 18))
        }
        .buttonStyle(.plain)
        .glassEffect(isPlaying ? .regular.tint(.accentColor.opacity(0.25)).interactive() : .regular.interactive(), in: .rect(cornerRadius: 18))
        .contextMenu {
            FavoriteButton(favorite: favorite)
            if group.hasChoice {
                Section("Regarder en") {
                    ForEach(group.variants) { variant in
                        Button(variant.quality.label) { play(variant) }
                    }
                }
            }
            if !group.shifted.isEmpty {
                Section("En différé") {
                    ForEach(group.delays.dropFirst(), id: \.self) { delay in
                        Button("+\(delay) h", systemImage: "clock.arrow.circlepath") {
                            play(group.variants(delay: delay).first)
                        }
                    }
                }
            }
        } preview: {
            // Drawn outside the view tree: it gets what it shows, not the environment.
            ChannelPreview(group: group, now: epg.now(epgID), next: epg.next(epgID))
        }
        .task(id: showsEPG ? group.id : nil) {
            guard showsEPG, let client = account.client else { return }
            // Not for rows flung past.
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            await epg.load(group, client: client)
        }
    }
}

struct ChannelLogo: View {
    let group: ChannelGroup
    var width: CGFloat = 46
    var height: CGFloat = 30

    var body: some View {
        RemoteImage(url: group.icon, size: 96, contentMode: .fit) {
            Text(group.name.prefix(2).uppercased())
                .font(.headline)
                .foregroundStyle(.secondary)
        }
        .frame(width: width, height: height)
        .padding(7)
        .background(.white.opacity(0.06), in: .rect(cornerRadius: 11))
    }
}

/// The best quality a channel comes in, and how many more.
struct QualityBadge: View {
    let group: ChannelGroup

    var body: some View {
        let best = group.variants[0].quality
        if best != .untagged || group.hasChoice || !group.shifted.isEmpty || group.baseDelay > 0 {
            HStack(spacing: 3) {
                if group.baseDelay > 0 {
                    Text("+\(group.baseDelay) h")
                } else if !group.shifted.isEmpty {
                    Image(systemName: "clock.arrow.circlepath").foregroundStyle(.tint)
                }
                if best != .untagged || group.hasChoice {
                    Text(best == .untagged ? String(localized: "Auto") : best.label)
                }
                if group.variants.count > 1 {
                    Text("+\(group.variants.count - 1)").foregroundStyle(.tertiary)
                }
            }
            .font(.caption2.weight(.semibold).monospacedDigit())
            .foregroundStyle(.secondary)
            .padding(.horizontal, 7)
            .padding(.vertical, 4)
            .background(.white.opacity(0.07), in: .capsule)
        }
    }
}

private struct ChannelPreview: View {
    let group: ChannelGroup
    let now: Programme?
    let next: Programme?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                RemoteImage(url: group.icon, size: 96, contentMode: .fit) { Color.clear }
                    .frame(width: 64, height: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(group.name).font(.headline)
                    if group.hasChoice {
                        Text(group.variants.map(\.quality.label).joined(separator: " · "))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            if let now {
                programme(String(localized: "Maintenant"), now)
            }
            if let next {
                programme(String(localized: "Ensuite"), next)
            }
        }
        .padding(20)
        .frame(width: 320, alignment: .leading)
    }

    private func programme(_ label: String, _ p: Programme) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(label) · \(p.start.formatted(date: .omitted, time: .shortened))")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tint)
            Text(p.title).font(.subheadline)
        }
    }
}
