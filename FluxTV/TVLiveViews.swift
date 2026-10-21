import SwiftUI

/// The home screen, live TV alone: the channel last watched up top, then
/// rows of channels with what's on, the way Oqee opens.
struct TVHomeView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @Environment(AccountStore.self) private var account
    @Environment(TVPlayer.self) private var player
    @FocusState private var focus: String?

    var body: some View {
        if catalog.guide.isEmpty {
            TVCatalogState(empty: "Aucune chaîne")
        } else {
            content
        }
    }

    private var content: some View {
        let guide = catalog.guide
        let recent = library.recentChannels.compactMap { guide.group($0) }
        let favorites = library.favorites(.live).compactMap { guide.group($0.key) }
        let numbered = guide.groups.filter { $0.number != nil }
        let tnt = Array((numbered.isEmpty ? guide.groups : numbered).prefix(30))

        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 50) {
                if let hero = recent.first ?? tnt.first {
                    TVHero(group: hero).focused($focus, equals: "hero")
                }
                if recent.count > 1 {
                    TVShelf(title: "Reprendre") {
                        ForEach(recent.dropFirst()) { card($0, shelf: "recent") }
                    }
                }
                if !favorites.isEmpty {
                    TVShelf(title: "Mes chaînes") {
                        ForEach(favorites) { card($0, shelf: "favorites") }
                    }
                }
                if !tnt.isEmpty {
                    TVShelf(title: "En ce moment à la TV") {
                        ForEach(tnt) { card($0, shelf: "tnt") }
                    }
                }
            }
            .padding(.bottom, 80)
        }
        .scrollIndicators(.hidden)
        // Down from the wide hero, the focus engine takes the card under
        // its middle; a row starts at its first card.
        .onChange(of: focus) { old, new in
            guard old == "hero", let new, let shelf = new.split(separator: "|").first else { return }
            let first: ChannelGroup? = switch shelf {
            case "recent": recent.dropFirst().first
            case "favorites": favorites.first
            default: tnt.first
            }
            if let first, new != "\(shelf)|\(first.id)" { focus = "\(shelf)|\(first.id)" }
        }
    }

    /// Wherever it's picked from here, a channel zaps through them all.
    private func card(_ group: ChannelGroup, shelf: String) -> some View {
        TVChannelCard(group: group, width: 400, isPlaying: player.channel?.id == group.id, focus: ($focus, "\(shelf)|\(group.id)")) {
            if let client = account.client { player.watch(group, in: catalog.guide.groups, client: client) }
        }
    }
}

/// The big tile at the top of the home screen: back to the last channel.
private struct TVHero: View {
    let group: ChannelGroup

    @Environment(EPG.self) private var epg
    @Environment(AccountStore.self) private var account
    @Environment(TVPlayer.self) private var player
    @Environment(Library.self) private var library
    @Environment(Catalog.self) private var catalog

    var body: some View {
        let now = epg.now(epg.source(group))
        let next = epg.next(epg.source(group))
        Button {
            if let client = account.client { player.watch(group, in: catalog.guide.groups, client: client) }
        } label: {
            HStack(spacing: 60) {
                VStack(alignment: .leading, spacing: 16) {
                    if now?.image != nil {
                        TVChannelLogo(group: group, width: 160, height: 90).shadow(color: .black.opacity(0.5), radius: 8)
                    }
                    Text(library.recentChannels.first == group.id ? "REPRENDRE EN DIRECT" : "EN DIRECT")
                        .font(.caption.weight(.heavy))
                        .foregroundStyle(FluxTint.blue)
                    Text(now?.title ?? group.name)
                        .font(.largeTitle.weight(.bold))
                        .lineLimit(2)
                    if let now {
                        HStack(spacing: 18) {
                            Text("\(group.name) · \(now.timeRange)").foregroundStyle(.secondary)
                            LiveProgress(programme: now, height: 6).frame(width: 360)
                        }
                        .font(.callout)
                    }
                    if let summary = now?.summary {
                        Text(summary).font(.callout).foregroundStyle(.secondary).lineLimit(3)
                    }
                    if let next {
                        Text("À suivre · \(next.start.formatted(date: .omitted, time: .shortened))  \(next.title)")
                            .font(.callout)
                            .foregroundStyle(.tertiary)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: 1000, alignment: .leading)
                Spacer()
                if now?.image == nil {
                    TVChannelLogo(group: group, width: 380, height: 214)
                }
            }
            .padding(60)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .background {
                ProgrammeBackdrop(group: group, image: now?.image)
                    .overlay { LinearGradient(colors: [.black.opacity(0.85), .black.opacity(0.4), .clear], startPoint: .leading, endPoint: .trailing) }
            }
            .frame(height: 440)
        }
        .buttonStyle(.card)
        .padding(.horizontal, 80)
        .padding(.top, 20)
        .task(id: group.id) {
            guard let client = account.client else { return }
            await epg.load(group, client: client, limit: 4)
        }
    }
}

/// Every channel, by category: the list down the left, the channels as
/// tiles on the right.
struct TVChannelsView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @Environment(AccountStore.self) private var account
    @Environment(TVPlayer.self) private var player
    @AppStorage("tv.live.category") private var selection = TVCategory.all

    private let columns = [GridItem(.adaptive(minimum: 380, maximum: 420), spacing: 44, alignment: .top)]

    var body: some View {
        if catalog.guide.isEmpty {
            TVCatalogState(empty: "Aucune chaîne")
        } else {
            let channels = tvChannels(selection, catalog: catalog, library: library)
            HStack(alignment: .top, spacing: 30) {
                TVCategoryColumn(entries: TVCategory.entries(catalog.live.categories, recent: true), selection: $selection)
                ScrollView {
                    if channels.isEmpty {
                        ContentUnavailableView(selection == TVCategory.favorites ? "Aucun favori" : "Rien ici", systemImage: "tv",
                                               description: selection == TVCategory.favorites ? Text("Maintiens le clic sur une chaîne pour l’ajouter.") : nil)
                            .padding(.top, 120)
                    }
                    LazyVGrid(columns: columns, spacing: 50) {
                        ForEach(channels) { group in
                            TVChannelCard(group: group, width: 390, isPlaying: player.channel?.id == group.id) {
                                if let client = account.client { player.watch(group, in: selection == TVCategory.recent ? catalog.guide.groups : channels, client: client) }
                            }
                            .contextMenu { TVFavoriteButton(favorite: .live(group)) }
                        }
                    }
                    .padding(.vertical, 30)
                    .padding(.trailing, 60)
                }
                .scrollIndicators(.hidden)
                .scrollClipDisabled()
                .focusSection()
            }
        }
    }
}

/// The guide: what's on every channel of a category over the next three
/// hours, on a grid; a programme on now tunes in.
struct TVGuideView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @Environment(AccountStore.self) private var account
    @Environment(TVPlayer.self) private var player
    /// Every channel each time, whatever Direct was left on.
    @State private var selection = TVCategory.all

    private static let channelWidth: CGFloat = 300
    private static let span: TimeInterval = 3 * 3600

    var body: some View {
        if catalog.guide.isEmpty {
            TVCatalogState(empty: "Aucune chaîne")
        } else {
            TimelineView(.periodic(from: .now, by: 60)) { context in
                let start = Self.windowStart(context.date)
                let channels = tvChannels(selection, catalog: catalog, library: library)
                GeometryReader { geo in
                    let width = geo.size.width - Self.channelWidth - 160
                    let perSecond = width / Self.span
                    VStack(alignment: .leading, spacing: 0) {
                        header(start: start, perSecond: perSecond, now: context.date)
                        ScrollView {
                            LazyVStack(alignment: .leading, spacing: 10) {
                                ForEach(channels) { group in
                                    TVGuideRow(group: group, lineup: selection == TVCategory.recent ? catalog.guide.groups : channels, start: start, perSecond: perSecond, channelWidth: Self.channelWidth)
                                }
                            }
                            .padding(.horizontal, 80)
                            .padding(.vertical, 20)
                        }
                        .scrollIndicators(.hidden)
                        .overlay(alignment: .topLeading) {
                            // Now, as a line down the grid.
                            Rectangle()
                                .fill(FluxTint.blue)
                                .frame(width: 3)
                                .offset(x: 80 + Self.channelWidth + 12 + context.date.timeIntervalSince(start) * perSecond)
                                .allowsHitTesting(false)
                        }
                    }
                }
            }
        }
    }

    private func header(start: Date, perSecond: CGFloat, now: Date) -> some View {
        HStack(spacing: 12) {
            Picker("Catégorie", selection: $selection) {
                ForEach(TVCategory.entries(catalog.live.categories, recent: true), id: \.id) { entry in
                    Text(DisplayName.clean(entry.name)).tag(entry.id)
                }
            }
            .pickerStyle(.menu)
            .frame(width: Self.channelWidth)
            ZStack(alignment: .leading) {
                ForEach(0..<6) { i in
                    let date = start.addingTimeInterval(Double(i) * 1800)
                    Text(date.formatted(date: .omitted, time: .shortened))
                        .font(.callout.monospacedDigit())
                        .foregroundStyle(.secondary)
                        .offset(x: Double(i) * 1800 * perSecond)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 80)
        .padding(.top, 10)
        .focusSection()
    }

    /// The half hour before now.
    private static func windowStart(_ date: Date) -> Date {
        let t = date.timeIntervalSinceReferenceDate
        return Date(timeIntervalSinceReferenceDate: t - t.truncatingRemainder(dividingBy: 1800))
    }
}

private struct TVGuideRow: View {
    let group: ChannelGroup
    let lineup: [ChannelGroup]
    let start: Date
    let perSecond: CGFloat
    let channelWidth: CGFloat

    @Environment(EPG.self) private var epg
    @Environment(AccountStore.self) private var account
    @Environment(TVPlayer.self) private var player

    var body: some View {
        let end = start.addingTimeInterval(3 * 3600)
        let programmes = epg.upcoming(epg.source(group)).filter { $0.start < end && $0.end > start }
        HStack(spacing: 12) {
            HStack(spacing: 16) {
                Text(group.number.map(String.init) ?? "")
                    .font(.callout.weight(.bold).monospacedDigit())
                    .foregroundStyle(.secondary)
                    .frame(width: 50)
                TVChannelLogo(group: group, width: 110, height: 62)
                Spacer(minLength: 0)
            }
            .frame(width: channelWidth, height: 96)
            .background(.white.opacity(0.05), in: .rect(cornerRadius: 16))

            ZStack(alignment: .leading) {
                if programmes.isEmpty {
                    Button { watch() } label: {
                        Text(group.name)
                            .font(.callout)
                            .padding(.horizontal, 20)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                    }
                    .buttonStyle(TVRowStyle(cornerRadius: 14))
                }
                ForEach(programmes, id: \.self) { p in
                    let from = max(p.start, start), to = min(p.end, end)
                    Button { watch() } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(p.title).font(.callout.weight(.semibold)).lineLimit(1)
                            Text(p.timeRange).font(.caption2.monospacedDigit()).opacity(0.65).lineLimit(1)
                        }
                        .padding(.horizontal, 16)
                        .frame(width: max(8, to.timeIntervalSince(from) * perSecond - 6), height: 96, alignment: .leading)
                    }
                    .buttonStyle(TVRowStyle(cornerRadius: 14, isCurrent: p.isOn))
                    // Laid out where it starts, not just drawn there: the
                    // focus engine moves between frames.
                    .padding(.leading, from.timeIntervalSince(start) * perSecond)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 96)
        }
        .focusSection()
        .task(id: group.id) {
            guard let client = account.client else { return }
            try? await Task.sleep(for: .milliseconds(150))
            guard !Task.isCancelled else { return }
            await epg.load(group, client: client, limit: 10)
        }
    }

    private func watch() {
        if let client = account.client { player.watch(group, in: lineup, client: client) }
    }
}
