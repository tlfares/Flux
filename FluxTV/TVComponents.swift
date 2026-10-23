import SwiftUI

/// The live screen's lists and the guide's rows: white under focus, the
/// text turning dark, as tvOS draws its own lists.
struct TVRowStyle: ButtonStyle {
    var cornerRadius: CGFloat = 18
    var isCurrent = false

    func makeBody(configuration: Configuration) -> some View {
        Row(configuration: configuration, cornerRadius: cornerRadius, isCurrent: isCurrent)
    }

    private struct Row: View {
        let configuration: Configuration
        let cornerRadius: CGFloat
        let isCurrent: Bool
        @Environment(\.isFocused) private var isFocused

        var body: some View {
            configuration.label
                .foregroundStyle(isFocused ? Color.black : Color.white)
                .background(
                    isFocused ? AnyShapeStyle(Color.white) : AnyShapeStyle(isCurrent ? FluxTint.blue.opacity(0.35) : Color.white.opacity(0.06)),
                    in: .rect(cornerRadius: cornerRadius)
                )
                .scaleEffect(isFocused ? (configuration.isPressed ? 1.0 : 1.03) : 1)
                .shadow(color: .black.opacity(isFocused ? 0.4 : 0), radius: 18, y: 10)
                .animation(.easeOut(duration: 0.18), value: isFocused)
        }
    }
}

/// A channel's logo on its tile.
struct TVChannelLogo: View {
    let group: ChannelGroup
    var width: CGFloat = 120
    var height: CGFloat = 68

    var body: some View {
        RemoteImage(url: group.icon, size: max(width, height) * 1.5, contentMode: .fit) {
            Text(group.name.prefix(3).uppercased())
                .font(.system(size: height * 0.4, weight: .heavy))
                .foregroundStyle(.white.opacity(0.7))
        }
        .frame(width: width, height: height)
    }
}

/// How far into a programme, moving with the clock.
struct LiveProgress: View {
    let programme: Programme
    var height: CGFloat = 6
    var tint: Color = FluxTint.blue

    var body: some View {
        TimelineView(.periodic(from: .now, by: 20)) { context in
            let total = programme.end.timeIntervalSince(programme.start)
            let fraction = total > 0 ? min(max(context.date.timeIntervalSince(programme.start) / total, 0), 1) : 0
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.18))
                    Capsule().fill(tint).frame(width: geo.size.width * fraction)
                }
            }
            .frame(height: height)
        }
    }
}

/// A channel as Oqee shows it: its logo, what's on and how far it's got,
/// on a card that lifts under focus.
struct TVChannelCard: View {
    let group: ChannelGroup
    var width: CGFloat = 420
    var isPlaying = false
    /// Where focus state should track the card, when someone needs to.
    var focus: (binding: FocusState<String?>.Binding, id: String)?
    let action: () -> Void

    @Environment(EPG.self) private var epg
    @Environment(AccountStore.self) private var account

    var body: some View {
        let now = epg.now(epg.source(group))
        VStack(alignment: .leading, spacing: 14) {
            Button(action: action) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .top) {
                        TVChannelLogo(group: group, width: width * 0.34, height: width * 0.19)
                            .shadow(color: .black.opacity(0.5), radius: 8)
                        Spacer()
                        if let number = group.number {
                            Text("\(number)")
                                .font(.title3.weight(.bold).monospacedDigit())
                                .foregroundStyle(.white.opacity(0.9))
                                .shadow(color: .black.opacity(0.6), radius: 6)
                        }
                    }
                    Spacer(minLength: 0)
                    if isPlaying {
                        Label("En cours", systemImage: "waveform")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(FluxTint.blue)
                    }
                    if let now {
                        LiveProgress(programme: now, height: 5).padding(.top, 8)
                    }
                }
                .padding(22)
                .frame(width: width, height: width * 9 / 16, alignment: .topLeading)
                // Behind, never sizing the card: the programme's still when
                // the guide has one, as Oqee shows them, else the logo blurred.
                .background { ProgrammeBackdrop(group: group, image: now?.image) }
            }
            .buttonStyle(.card)
            .modifier(OptionalFocus(focus: focus))

            VStack(alignment: .leading, spacing: 4) {
                Text(now?.title ?? group.name)
                    .font(.headline)
                    .lineLimit(1)
                Text(now.map { "\(group.name) · \($0.timeRange)" } ?? "En direct")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(width: width, alignment: .leading)
        }
        .task(id: group.id) {
            guard let client = account.client else { return }
            await epg.load(group, client: client)
        }
    }
}

private struct OptionalFocus: ViewModifier {
    let focus: (binding: FocusState<String?>.Binding, id: String)?

    func body(content: Content) -> some View {
        if let focus { content.focused(focus.binding, equals: focus.id) } else { content }
    }
}

/// What's on, as a still darkened where text and logo sit; the channel's
/// colors when there's no still.
struct ProgrammeBackdrop: View {
    let group: ChannelGroup
    let image: String?

    var body: some View {
        ChannelGlow(group: group)
            .overlay {
                if let image {
                    RemoteImage(url: image, size: 640, contentMode: .fill) { Color.clear }
                        .overlay {
                            LinearGradient(colors: [.black.opacity(0.55), .clear, .black.opacity(0.6)], startPoint: .top, endPoint: .bottom)
                        }
                }
            }
            .clipped()
    }
}

/// The colors of a channel's logo, blurred into its tile's background.
struct ChannelGlow: View {
    let group: ChannelGroup

    var body: some View {
        LinearGradient(colors: [Color(white: 0.17), Color(white: 0.08)], startPoint: .topLeading, endPoint: .bottomTrailing)
            .overlay {
                RemoteImage(url: group.icon, size: 160, contentMode: .fill) { Color.clear }
                    .scaleEffect(1.6)
                    .blur(radius: 60)
                    .opacity(0.45)
            }
            .clipped()
    }
}

/// A poster that lifts under focus, its title under it.
struct TVPosterButton: View {
    let title: String
    let image: String?
    var progress: Double?
    var width: CGFloat = 240
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button(action: action) {
                RemoteImage(url: image, size: width * 1.5) {
                    ZStack {
                        Color(white: 0.14)
                        Image(systemName: "film").font(.largeTitle).foregroundStyle(.tertiary)
                    }
                }
                .frame(width: width, height: width * 1.5)
                .overlay(alignment: .bottom) {
                    if let progress, progress > 0 {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(.white.opacity(0.25))
                                Capsule().fill(FluxTint.blue).frame(width: geo.size.width * progress)
                            }
                        }
                        .frame(height: 6)
                        .padding(14)
                    }
                }
            }
            .buttonStyle(.card)
            Text(title)
                .font(.caption)
                .lineLimit(2, reservesSpace: true)
                .frame(width: width, alignment: .leading)
        }
    }
}

/// A titled row that scrolls sideways, as on the home screen.
struct TVShelf<Content: View>: View {
    let title: String
    var spacing: CGFloat = 40
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text(title)
                .font(.title3.weight(.semibold))
                .padding(.horizontal, 80)
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: spacing) { content }
                    .padding(.horizontal, 80)
                    .padding(.vertical, 24)
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
        }
        .focusSection()
    }
}

/// The list of categories down the left of a catalogue screen, in the
/// order chosen for them. Holding one moves or hides it; the last row
/// opens them all to reorder.
struct TVCategoryColumn: View {
    let categories: [Category]
    let kind: MediaKind
    var recent = false
    @Binding var selection: String
    @State private var isEditing = false
    private var layouts: CategoryLayouts { .shared }

    var body: some View {
        let entries = TVCategory.entries(categories, recent: recent, kind: kind)
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 8) {
                    ForEach(entries, id: \.id) { entry in
                        Button {
                            selection = entry.id
                        } label: {
                            HStack(spacing: 14) {
                                Text(DisplayName.clean(entry.name)).lineLimit(1)
                                Spacer(minLength: 0)
                                if entry.id == selection {
                                    Image(systemName: "checkmark").font(.callout.weight(.bold))
                                }
                            }
                            .font(.callout.weight(entry.id == selection ? .semibold : .regular))
                            .padding(.horizontal, 24)
                            .padding(.vertical, 16)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(TVRowStyle(cornerRadius: 16, isCurrent: entry.id == selection))
                        .contextMenu {
                            if !entry.id.hasPrefix("_") {
                                Button("Monter", systemImage: "arrow.up") { layouts.move(entry.id, by: -1, in: categories, kind) }
                                Button("Descendre", systemImage: "arrow.down") { layouts.move(entry.id, by: 1, in: categories, kind) }
                                Button("Masquer", systemImage: "eye.slash") { layouts.toggleHidden(entry.id, kind) }
                            }
                            Button("Organiser les catégories", systemImage: "list.bullet") { isEditing = true }
                        }
                        .id(entry.id)
                    }
                    if !categories.isEmpty {
                        Button {
                            isEditing = true
                        } label: {
                            Label("Organiser", systemImage: "slider.horizontal.3")
                                .font(.callout)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 16)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(TVRowStyle(cornerRadius: 16))
                        .opacity(0.8)
                        .padding(.top, 12)
                    }
                }
                .padding(.vertical, 20)
                .padding(.horizontal, 12)
            }
            .scrollIndicators(.hidden)
            .onAppear { proxy.scrollTo(selection, anchor: .center) }
        }
        .frame(width: 440)
        .focusSection()
        // A category that went away (another country, another account): everything instead.
        .onChange(of: categories.map(\.id), initial: true) { _, ids in
            if !ids.isEmpty, !selection.hasPrefix("_"), !ids.contains(selection) { selection = TVCategory.all }
        }
        .fullScreenCover(isPresented: $isEditing) {
            TVCategoryEditor(categories: categories, kind: kind)
        }
    }
}

/// Every category, those put away too: the name shows or hides one, the
/// arrows beside it move it.
private struct TVCategoryEditor: View {
    let categories: [Category]
    let kind: MediaKind
    @Environment(\.dismiss) private var dismiss
    private var layouts: CategoryLayouts { .shared }

    var body: some View {
        let list = layouts.arranged(categories, kind, includingHidden: true)
        NavigationStack {
            List {
                Section {
                    ForEach(list) { category in
                        let hidden = layouts.isHidden(category.id, kind)
                        HStack(spacing: 20) {
                            Button {
                                layouts.toggleHidden(category.id, kind)
                            } label: {
                                HStack(spacing: 20) {
                                    Image(systemName: hidden ? "eye.slash" : "eye").frame(width: 50)
                                    Text(DisplayName.clean(category.name)).lineLimit(1)
                                    Spacer(minLength: 0)
                                }
                                .opacity(hidden ? 0.5 : 1)
                            }
                            Button { layouts.move(category.id, by: -1, in: categories, kind) } label: {
                                Image(systemName: "chevron.up")
                            }
                            .accessibilityLabel("Monter")
                            Button { layouts.move(category.id, by: 1, in: categories, kind) } label: {
                                Image(systemName: "chevron.down")
                            }
                            .accessibilityLabel("Descendre")
                        }
                    }
                } footer: {
                    Text("Le nom affiche ou masque une catégorie, les flèches la déplacent. Les catégories masquées restent dans « Tout ».")
                }
                if layouts.isCustomized(kind) {
                    Section {
                        Button("Réinitialiser l’ordre", systemImage: "arrow.counterclockwise", role: .destructive) { layouts.reset(kind) }
                    }
                }
            }
            .frame(maxWidth: 1200)
            .navigationTitle("Catégories")
        }
    }
}

/// The ids the category column starts with.
enum TVCategory {
    static let all = "_all"
    static let favorites = "_fav"
    static let recent = "_recent"

    @MainActor
    static func entries(_ categories: [Category], recent: Bool, kind: MediaKind) -> [(id: String, name: String)] {
        var list: [(String, String)] = [(favorites, String(localized: "Favoris"))]
        if recent { list.append((self.recent, String(localized: "Récents"))) }
        list.append((all, String(localized: "Tout")))
        return list + CategoryLayouts.shared.arranged(categories, kind).map { ($0.id, $0.name) }
    }
}

/// The channels a category stands for.
@MainActor
func tvChannels(_ selection: String, catalog: Catalog, library: Library) -> [ChannelGroup] {
    let guide = catalog.guide
    switch selection {
    case TVCategory.favorites: return library.favorites(.live).compactMap { guide.group($0.key) }
    case TVCategory.recent: return library.recentChannels.compactMap { guide.group($0) }
    case TVCategory.all: return guide.groups
    default: return guide.groups(in: selection)
    }
}

/// While the catalogue first loads, or when it couldn't.
struct TVCatalogState: View {
    @Environment(Catalog.self) private var catalog
    @Environment(AccountStore.self) private var account
    let empty: String

    var body: some View {
        if catalog.isLoading || catalog.isStarting || catalog.isIndexing {
            VStack(spacing: 24) {
                ProgressView()
                Text("Chargement du catalogue…").foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ContentUnavailableView {
                Label(catalog.error == nil ? empty : "Impossible de charger", systemImage: catalog.error == nil ? "tray" : "wifi.exclamationmark")
            } description: {
                Text(catalog.error ?? "Le fournisseur n’a rien envoyé cette fois.")
            } actions: {
                Button("Réessayer") {
                    if let client = account.client { Task { await catalog.refresh(client) } }
                }
            }
        }
    }
}
