import SwiftUI

/// The guide on a phone: what's on every channel of a category over the
/// next hours, on a grid scrolled both ways. The logos stay on the left
/// and the hours on top; a programme tunes in its channel.
struct GuideView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @AppStorage("guide.category") private var selection = CategoryBar.all
    @AppStorage(FavoritesStyle.storageKey) private var style = FavoritesStyle.separate

    static let logoWidth: CGFloat = 72
    static let rowHeight: CGFloat = 64
    /// Half an hour is 120 points: a title fits in a short programme.
    static let perSecond: CGFloat = 4.0 / 60
    static let span: TimeInterval = 4 * 3600

    var body: some View {
        let listing = LiveListing(selection: selection, catalog: catalog, library: library, style: style).groups
        let channels = listing.top + listing.rest
        VStack(spacing: 0) {
            if !catalog.live.isEmpty {
                CategoryBar(categories: catalog.live.categories, kind: .live, selection: $selection, showsRecent: true, showsFavorites: style == .separate)
            }
            if catalog.live.isEmpty {
                CatalogState(empty: String(localized: "Aucune chaîne"))
            } else if catalog.guide.isEmpty {
                ProgressView().controlSize(.large).frame(maxHeight: .infinity)
            } else if channels.isEmpty {
                LiveEmptySelection(selection: selection).frame(maxHeight: .infinity)
            } else {
                TimelineView(.periodic(from: .now, by: 60)) { context in
                    grid(channels, lineup: selection == CategoryBar.recent ? catalog.guide.groups : channels, now: context.date)
                }
            }
        }
        .background(Color.screenBackground.ignoresSafeArea())
        .navigationTitle("Guide")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func grid(_ channels: [ChannelGroup], lineup: [ChannelGroup], now: Date) -> some View {
        let start = Self.windowStart(now)
        return ScrollView([.horizontal, .vertical]) {
            LazyVStack(alignment: .leading, spacing: 6, pinnedViews: .sectionHeaders) {
                Section {
                    ForEach(channels) { group in
                        GuideRow(group: group, lineup: lineup, start: start, now: now)
                    }
                } header: {
                    header(start: start, now: now)
                }
            }
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        // A fresh category starts at the top left, by now.
        .id(selection)
    }

    private func header(start: Date, now: Date) -> some View {
        HStack(spacing: 0) {
            Color.clear.frame(width: Self.logoWidth + 8)
            ZStack(alignment: .leading) {
                ForEach(0..<Int(Self.span / 1800), id: \.self) { i in
                    let date = start.addingTimeInterval(Double(i) * 1800)
                    Text(date.formatted(date: .omitted, time: .shortened))
                        .font(.caption.weight(.semibold).monospacedDigit())
                        .foregroundStyle(.secondary)
                        .offset(x: Double(i) * 1800 * Self.perSecond + 4)
                }
                Circle()
                    .fill(.tint)
                    .frame(width: 7, height: 7)
                    .offset(x: now.timeIntervalSince(start) * Self.perSecond - 3.5, y: 11)
            }
            .frame(width: Self.span * Self.perSecond, alignment: .leading)
        }
        .frame(height: 32)
        .background(Color.screenBackground)
    }

    /// The half hour before now.
    static func windowStart(_ date: Date) -> Date {
        let t = date.timeIntervalSinceReferenceDate
        return Date(timeIntervalSinceReferenceDate: t - t.truncatingRemainder(dividingBy: 1800))
    }
}

private struct GuideRow: View {
    let group: ChannelGroup
    let lineup: [ChannelGroup]
    let start: Date
    let now: Date

    @Environment(EPG.self) private var epg
    @Environment(AccountStore.self) private var account
    @Environment(PlayerCenter.self) private var player

    var body: some View {
        let end = start.addingTimeInterval(GuideView.span)
        let programmes = epg.upcoming(epg.source(group)).filter { $0.start < end && $0.end > start }
        let isPlaying = player.liveGroup?.id == group.id
        HStack(spacing: 8) {
            Button(action: watch) {
                ChannelLogo(group: group, width: GuideView.logoWidth - 14, height: GuideView.rowHeight - 14)
                    .overlay {
                        RoundedRectangle(cornerRadius: 11).strokeBorder(.tint, lineWidth: isPlaying ? 2 : 0)
                    }
            }
            .buttonStyle(.plain)
            .background(Color.screenBackground)
            // Kept on the left as the programmes scroll under it.
            .visualEffect { content, proxy in
                content.offset(x: max(0, -proxy.frame(in: .scrollView).minX))
            }
            .zIndex(1)

            ZStack(alignment: .leading) {
                // Now, down the grid, behind the titles.
                Rectangle()
                    .fill(.tint)
                    .frame(width: 2)
                    .offset(x: now.timeIntervalSince(start) * GuideView.perSecond - 1)
                    .allowsHitTesting(false)
                if programmes.isEmpty {
                    block(title: group.name, subtitle: nil, isOn: false)
                        .frame(width: 280)
                }
                ForEach(programmes, id: \.self) { p in
                    let from = max(p.start, start), to = min(p.end, end)
                    block(title: p.title, subtitle: p.timeRange, isOn: p.isOn)
                        .frame(width: max(6, to.timeIntervalSince(from) * GuideView.perSecond - 4))
                        .offset(x: from.timeIntervalSince(start) * GuideView.perSecond)
                }
            }
            .frame(width: GuideView.span * GuideView.perSecond, height: GuideView.rowHeight, alignment: .leading)
        }
        .padding(.leading, 8)
        .task(id: group.id) {
            guard let client = account.client else { return }
            // Not for rows flung past.
            try? await Task.sleep(for: .milliseconds(200))
            guard !Task.isCancelled else { return }
            await epg.load(group, client: client, limit: 10)
        }
    }

    private func block(title: String, subtitle: String?, isOn: Bool) -> some View {
        Button(action: watch) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.semibold)).lineLimit(1)
                if let subtitle {
                    Text(subtitle).font(.caption2.monospacedDigit()).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .background(isOn ? AnyShapeStyle(.tint.opacity(0.22)) : AnyShapeStyle(.white.opacity(0.06)), in: .rect(cornerRadius: 12))
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .frame(height: GuideView.rowHeight)
    }

    private func watch() {
        if let client = account.client { player.watch(group, client: client) }
    }
}
