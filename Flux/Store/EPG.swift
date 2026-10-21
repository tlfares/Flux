import Foundation

/// What's on, fetched for the rows on screen only and kept a few minutes.
/// At most four requests at once: panels throttle bursts.
@MainActor
@Observable
final class EPG {
    private struct Entry {
        var programmes: [Programme]
        var fetched: Date
        var limit: Int
    }

    private var cache: [Int: Entry] = [:]
    private var inFlight: Set<Int> = []
    private var running = 0
    private var waiting: [CheckedContinuation<Void, Never>] = []

    func now(_ channelID: Int) -> Programme? {
        let date = Date()
        return cache[channelID]?.programmes.first { $0.start <= date && $0.end > date }
    }

    func next(_ channelID: Int) -> Programme? {
        let date = Date()
        return cache[channelID]?.programmes.first { $0.start > date }
    }

    /// What's on and what follows, for the player.
    func upcoming(_ channelID: Int) -> [Programme] {
        let date = Date()
        return cache[channelID]?.programmes.filter { $0.end > date } ?? []
    }

    /// The stream whose guide stands for the channel: panels often fill it
    /// in for one quality and not the others.
    func source(_ group: ChannelGroup) -> Int {
        let candidates = [group.epgChannel.id] + group.variants.prefix(3).map(\.id)
        return candidates.first { cache[$0]?.programmes.isEmpty == false } ?? group.epgChannel.id
    }

    /// Channel key → programmes from the open guide (XML TV Fr), which
    /// stand in for the panel's on the channels it covers.
    private var open: [String: [Programme]] = [:]

    /// Takes the open guide, and fills in at once the channels it has,
    /// rows on screen included.
    func useOpenGuide(_ guide: [String: [Programme]], for groups: [ChannelGroup]) {
        if !guide.isEmpty { open = guide }
        guard !open.isEmpty else { return }
        for group in groups {
            guard let programmes = open[group.id], cache[group.epgChannel.id]?.programmes != programmes else { continue }
            cache[group.epgChannel.id] = Entry(programmes: programmes, fetched: Date(), limit: .max)
        }
    }

    /// Tries the channel's streams until one has a guide; the open guide
    /// first, when it has the channel.
    func load(_ group: ChannelGroup, client: XtreamClient, limit: Int = 3) async {
        if let programmes = open[group.id], programmes.contains(where: { $0.end > Date() }) {
            if cache[group.epgChannel.id]?.programmes != programmes {
                cache[group.epgChannel.id] = Entry(programmes: programmes, fetched: Date(), limit: .max)
            }
            return
        }
        var candidates = [group.epgChannel.id]
        for v in group.variants.prefix(3) where !candidates.contains(v.id) { candidates.append(v.id) }
        for id in candidates {
            await load(id, client: client, limit: limit)
            if Task.isCancelled || !isEmpty(id) { return }
        }
    }

    /// Whether the guide was asked and had nothing.
    func isEmpty(_ channelID: Int) -> Bool { cache[channelID]?.programmes.isEmpty == true }

    func load(_ channelID: Int, client: XtreamClient, limit: Int = 3) async {
        if let entry = cache[channelID], entry.limit >= limit, Date().timeIntervalSince(entry.fetched) < 300,
           now(channelID) != nil || entry.programmes.isEmpty { return }
        guard !inFlight.contains(channelID) else { return }
        inFlight.insert(channelID)
        defer { inFlight.remove(channelID) }
        if running >= 4 { await withCheckedContinuation { waiting.append($0) } }
        guard !Task.isCancelled else { release(); return }
        running += 1
        let programmes = (try? await client.shortEPG(channelID, limit: limit)) ?? []
        running -= 1
        release()
        cache[channelID] = Entry(programmes: Self.untangled(programmes), fetched: Date(), limit: limit)
    }

    /// In order, one at a time: panels often merge two guides a few minutes
    /// apart ("Rex 16:30–17:25" and "Rex 16:35–17:30"), and a programme
    /// starting before the last one ends is the other guide's.
    private static func untangled(_ programmes: [Programme]) -> [Programme] {
        var kept: [Programme] = []
        for p in programmes.sorted(by: { $0.start < $1.start }) where p.end > p.start {
            if let last = kept.last, p.start < last.end.addingTimeInterval(-120) { continue }
            kept.append(p)
        }
        return kept
    }

    private func release() {
        if !waiting.isEmpty { waiting.removeFirst().resume() }
    }
}
