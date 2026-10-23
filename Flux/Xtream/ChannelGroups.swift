import Foundation

/// How good a stream claims to be, read from its name.
nonisolated struct Quality: Hashable, Sendable, Codable {
    /// 1 SD … 6 4K. Untagged streams sit with HD.
    var rank: Int
    var label: String

    static let untagged = Quality(rank: 3, label: String(localized: "Standard"))
}

/// One way to watch a channel: a stream, its quality, and how many hours
/// behind the live broadcast it runs (0 for live).
nonisolated struct ChannelVariant: Hashable, Sendable, Identifiable {
    var channel: LiveChannel
    var quality: Quality
    var delay = 0
    /// One of the free channels' streams, standing in for the provider's.
    var isFree = false
    var id: Int { channel.id }

    static let freeQuality = Quality(rank: 7, label: String(localized: "Gratuit"))
}

/// A channel as people think of it: "TF1", whatever the number of streams
/// (UHD, HD, SD, backups, "+4" time shifts) the provider lists for it.
nonisolated struct ChannelGroup: Hashable, Sendable, Identifiable {
    /// Country and folded name, "fr|tf1": the same from one refresh, or one
    /// provider, to the next. Favorites and recents are kept by it.
    var id: String
    var name: String
    var icon: String?
    /// Where the channel sits in the numbering picked in the settings.
    var number: Int?
    /// The streams of the earliest broadcast (live, unless only time
    /// shifts are listed), best first.
    var variants: [ChannelVariant]
    /// The time-shifted streams, by delay then quality.
    var shifted: [ChannelVariant] = []

    var streams: [ChannelVariant] { variants + shifted }
    var epgChannel: LiveChannel { variants.first { $0.channel.epgID != nil }?.channel ?? variants[0].channel }
    var hasChoice: Bool { variants.count > 1 }
    var baseDelay: Int { variants[0].delay }
    /// Every delay the channel comes in, the earliest first.
    var delays: [Int] {
        var list = [baseDelay]
        for v in shifted where !list.contains(v.delay) { list.append(v.delay) }
        return list
    }

    func variants(delay: Int) -> [ChannelVariant] {
        delay == baseDelay ? variants : shifted.filter { $0.delay == delay }
    }

    /// The stream to start: the one picked last time for this channel, else
    /// the closest to the preference.
    func preferred(_ preference: QualityPreference, remembered: String?, delay: Int? = nil) -> ChannelVariant {
        let candidates = variants(delay: delay ?? baseDelay)
        guard !candidates.isEmpty else { return variants[0] }
        if let remembered, let v = candidates.first(where: { $0.quality.label == remembered }) { return v }
        // The broadcaster's own stream, whatever the quality asked for: it
        // plays better and takes none of the subscription's screens.
        if let free = candidates.first(where: \.isFree) { return free }
        switch preference {
        case .best: return candidates[0]
        case .saver: return candidates.last!
        case .hd:
            // Full HD or HD first, then the nearest above, then below.
            return candidates.min { a, b in
                let da = abs(Double(a.quality.rank) - 3.5), db = abs(Double(b.quality.rank) - 3.5)
                return da == db ? a.quality.rank > b.quality.rank : da < db
            }!
        }
    }
}

nonisolated enum QualityPreference: String, CaseIterable, Sendable {
    case best, hd, saver

    var title: String {
        switch self {
        case .best: String(localized: "Maximale")
        case .hd: "HD"
        case .saver: String(localized: "Économie")
        }
    }

    var symbol: String {
        switch self {
        case .best: "sparkles.tv"
        case .hd: "tv"
        case .saver: "leaf"
        }
    }

    static let storageKey = "live.quality"
}

/// What shapes the guide besides the provider's list: set in the settings.
nonisolated struct GuideOptions: Hashable, Sendable {
    /// Channels running hours behind ("TF1 +4"), as streams of their channel.
    var showsShifted = false
    var numbering = ChannelNumbering.provider
    /// The free channels' streams first for the channels the provider has
    /// too (France 2, Arte, BFM TV…), the provider's kept as backups.
    var prefersFree = true
    /// The free account's channels: one country's, or all.
    var freeScope = FreeScope.us

    static let shiftedKey = "live.showsShifted"
    static let prefersFreeKey = "live.prefersFree"
    /// Whether the Guide tab shows: on by default on the TV, off on phones.
    static let tabKey = "guide.tab"
    /// Whether the Films and Séries tabs show, when there's something in them.
    static let moviesTabKey = "tabs.movies"
    static let seriesTabKey = "tabs.series"

    @MainActor static var current: GuideOptions {
        let d = UserDefaults.standard
        return GuideOptions(showsShifted: d.bool(forKey: shiftedKey),
                            numbering: ChannelNumbering(rawValue: d.string(forKey: ChannelNumbering.storageKey) ?? "") ?? .default,
                            prefersFree: d.object(forKey: prefersFreeKey) as? Bool ?? true,
                            freeScope: FreeScope(rawValue: d.string(forKey: FreeScope.storageKey) ?? "") ?? .detected)
    }
}

/// The live section with each channel's streams put together, and indexed
/// the ways the screens look it up.
nonisolated struct LiveGuide: Sendable {
    /// Numbered channels first, by number; the rest in the provider's order.
    private(set) var groups: [ChannelGroup] = []
    /// Category id → indices into `groups`, in the same order.
    private var byCategory: [String: [Int]] = [:]
    private var byKey: [String: Int] = [:]
    private var byStream: [Int: Int] = [:]

    var isEmpty: Bool { groups.isEmpty }

    func count(in categoryID: String) -> Int { byCategory[categoryID]?.count ?? 0 }

    func groups(in categoryID: String?) -> [ChannelGroup] {
        guard let categoryID else { return groups }
        return (byCategory[categoryID] ?? []).map { groups[$0] }
    }

    func group(_ key: String) -> ChannelGroup? { byKey[key].map { groups[$0] } }
    func group(stream id: Int) -> ChannelGroup? { byStream[id].map { groups[$0] } }

    /// `free`: the free channels' streams, added to the channels the
    /// provider has too.
    static func build(_ section: CatalogSection<LiveChannel>, options: GuideOptions = .init(), free: [LiveChannel] = []) -> LiveGuide {
        // Untagged names take their category's country: "beIN Sports 1" in
        // "|AR| BEIN SPORT" isn't the French one.
        let countries = Dictionary(section.categories.map { ($0.id, ChannelName.categoryCountry($0.name)) }, uniquingKeysWith: { a, _ in a })
        var parsed = section.items.map { channel in
            ChannelName(channel.name, fallbackCountry: channel.categoryID.flatMap { countries[$0] } ?? "")
        }
        // "TF1 1" next to "TF1", with no "TF1 2": a backup stream of TF1,
        // not a channel of its own.
        let keys = Set(parsed.map(\.key))
        for i in parsed.indices {
            if let base = parsed[i].backupOf, keys.contains(base), !keys.contains(base + "2") { parsed[i].key = base }
        }

        // Radios kept apart from the TV channel of the same name: France
        // Info the radio isn't franceinfo the channel.
        let radioCategories = Set(section.categories.filter { c in
            SearchIndex.fold(c.name).lowercased().split { !$0.isLetter && !$0.isNumber }.contains { $0 == "radio" || $0 == "radios" }
        }.map(\.id))

        var groups: [ChannelGroup] = []
        var byKey: [String: Int] = [:]
        var names: [Int: [String]] = [:]
        for (channel, name) in zip(section.items, parsed) {
            if name.delay > 0, !options.showsShifted { continue }
            var key = name.key
            var display = name.display
            var icon = channel.icon
            if let category = channel.categoryID, radioCategories.contains(category) {
                key = "radio|" + key
            } else if name.country.uppercased() == "FR", let known = FrenchChannels.known(name.key) {
                key = known.key
                display = known.name
                icon = known.logo ?? icon
            }
            let variant = ChannelVariant(channel: channel, quality: name.quality, delay: name.delay)
            if let i = byKey[key] {
                groups[i].variants.append(variant)
                if groups[i].icon == nil { groups[i].icon = icon }
                names[i, default: []].append(display)
            } else {
                byKey[key] = groups.count
                names[groups.count] = [display]
                groups.append(ChannelGroup(id: key, name: display, icon: icon, variants: [variant]))
            }
        }
        // Only to channels already here: the free list is the subscription's
        // channels played better, not more channels.
        for channel in free {
            let name = ChannelName(channel.name, fallbackCountry: "FR")
            let key = FrenchChannels.known(name.key)?.key ?? name.key
            guard let i = byKey[key] else { continue }
            groups[i].variants.append(ChannelVariant(channel: channel, quality: ChannelVariant.freeQuality, isFree: true))
        }

        let numbers = options.numbering.numbers
        for i in groups.indices {
            var group = groups[i]
            // Earliest broadcast first, then highest quality; ties keep the
            // provider's order.
            let sorted = group.variants.enumerated().sorted { a, b in
                if a.element.delay != b.element.delay { return a.element.delay < b.element.delay }
                if a.element.quality.rank != b.element.quality.rank { return a.element.quality.rank > b.element.quality.rank }
                return a.offset < b.offset
            }.map(\.element)
            // Two "HD" read "HD" and "HD 2": the second is a backup.
            var seen: [String: Int] = [:]
            var labelled: [ChannelVariant] = []
            for var v in sorted {
                let tag = "\(v.delay)|\(v.quality.label)"
                seen[tag, default: 0] += 1
                if let n = seen[tag], n > 1 { v.quality.label = "\(v.quality.label) \(n)" }
                labelled.append(v)
            }
            let base = labelled[0].delay
            group.variants = labelled.filter { $0.delay == base }
            group.shifted = labelled.filter { $0.delay != base }
            // "France 2" reads better than "FRANCE 2"; known channels keep their own name.
            if FrenchChannels.known(group.id) == nil {
                group.name = names[i]?.first { $0 != $0.uppercased() } ?? group.name
            }
            // Not in the line-up picked: the provider's own number, with its numbering.
            group.number = numbers[group.id]
                ?? (options.numbering == .provider ? group.variants.compactMap(\.channel.number).filter { $0 > 0 }.min() : nil)
            groups[i] = group
        }

        // Numbered channels first, by number.
        let order = groups.indices.sorted { a, b in
            switch (groups[a].number, groups[b].number) {
            case let (x?, y?): return x != y ? x < y : a < b
            case (_?, nil): return true
            case (nil, _?): return false
            case (nil, nil): return a < b
            }
        }
        var guide = LiveGuide()
        guide.groups = order.map { groups[$0] }
        // The rest follow on, as on a box: 27, 28…
        var next = (guide.groups.compactMap(\.number).max() ?? 0) + 1
        for i in guide.groups.indices where guide.groups[i].number == nil {
            guide.groups[i].number = next
            next += 1
        }
        for (i, group) in guide.groups.enumerated() {
            guide.byKey[group.id] = i
            for v in group.streams { guide.byStream[v.id] = i }
        }
        // A channel is in every category one of its streams is in, numbered
        // ones first there too.
        for (categoryID, indices) in section.index {
            var seen = Set<Int>()
            var list: [Int] = []
            for item in indices {
                guard let g = guide.byStream[section.items[item].id], seen.insert(g).inserted else { continue }
                list.append(g)
            }
            let numbered = list.filter { guide.groups[$0].number != nil }.sorted()
            guide.byCategory[categoryID] = numbered + list.filter { guide.groups[$0].number == nil }
        }
        return guide
    }
}

/// A provider's channel name taken apart: "|FR| beIN SPORTS ᵁᴴᴰ 1" is
/// beIN Sports 1, in France, in UHD.
nonisolated struct ChannelName {
    var country: String
    var display: String
    var quality: Quality
    var key: String
    /// Hours behind the live broadcast: "+4", "|-2H|", "+6H".
    var delay = 0
    /// For "TF1 1": the key of "TF1", which it may be a backup of.
    var backupOf: String?

    private static let superscripts: [Character: Character] = [
        "ᴬ": "A", "ᴮ": "B", "ᶜ": "C", "ᴰ": "D", "ᴱ": "E", "ᶠ": "F", "ᴳ": "G", "ᴴ": "H", "ᴵ": "I", "ᴶ": "J",
        "ᴷ": "K", "ᴸ": "L", "ᴹ": "M", "ᴺ": "N", "ᴼ": "O", "ᴾ": "P", "ᴿ": "R", "ˢ": "S", "ᵀ": "T", "ᵁ": "U",
        "ⱽ": "V", "ᵂ": "W", "ˣ": "X", "ᵞ": "Y", "ᶻ": "Z", "⁰": "0", "¹": "1", "²": "2", "³": "3", "⁴": "4",
        "⁵": "5", "⁶": "6", "⁷": "7", "⁸": "8", "⁹": "9", "ᵏ": "K", "ʰ": "H", "ᵈ": "D", "ˡ": "L",
        // Small capitals, used the same way: "ʜᴅ".
        "ʜ": "H", "ᴅ": "D", "ꜱ": "S", "ᴜ": "U", "ᴋ": "K",
    ]

    /// Tag → rank. Several can stack: "4K HDR".
    private static let qualityTags: [String: (rank: Int, label: String)] = [
        "SD": (1, "SD"), "LQ": (1, "SD"), "LOW": (1, "SD"), "480P": (1, "SD"), "576P": (1, "SD"),
        "HD": (3, "HD"), "720P": (3, "HD"), "HQ": (3, "HD"),
        "FHD": (4, "FHD"), "FULLHD": (4, "FHD"), "1080P": (4, "FHD"), "1080": (4, "FHD"), "1080I": (4, "FHD"),
        "HEVC": (5, "HEVC"), "H265": (5, "HEVC"), "H.265": (5, "HEVC"), "X265": (5, "HEVC"),
        "UHD": (5, "UHD"), "4K": (6, "4K"), "2160P": (6, "4K"), "8K": (6, "8K"),
        "HDR": (6, "HDR"), "50FPS": (4, String(localized: "50 i/s")), "60FPS": (4, String(localized: "60 i/s")),
        "RAW": (4, "RAW"), "VIP": (3, "VIP"), "BACKUP": (2, String(localized: "Secours")), "BK": (2, String(localized: "Secours")),
    ]

    /// The ISO country codes, and "UK": a bare "FR TF1" or "TF1 FR" is read
    /// as a country only with one of these.
    private static let countryCodes: Set<String> = Set("""
        AD AE AF AG AL AM AO AR AT AU AZ BA BB BD BE BF BG BH BI BJ BN BO BR BS BT BW BY BZ CA CD CF CG CH CI CL CM CN CO CR \
        CU CV CY CZ DE DJ DK DM DO DZ EC EE EG ER ES ET FI FJ FR GA GB GD GE GH GM GN GQ GR GT GW GY HK HN HR HT HU ID IE IL \
        IN IQ IR IS IT JM JO JP KE KG KH KM KN KP KR KW KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MG MK ML MM MN MR MT \
        MU MV MW MX MY MZ NA NE NG NI NL NO NP NZ OM PA PE PG PH PK PL PS PT PY QA RE RO RS RU RW SA SC SD SE SG SI SK SL SN \
        SO SR SS SV SY SZ TD TG TH TJ TL TM TN TR TT TW TZ UA UG UK US UY UZ VE VN YE ZA ZM ZW
        """.split(whereSeparator: \.isWhitespace).map(String.init))

    /// Without brackets, "FR TF1" or "TF1 FR" could be words of the name
    /// ("LA CHAINE METEO", "Planète+ CI"): only the countries panels list.
    private static let bareCountries: Set<String> = ["FR", "BE", "CH", "CA", "UK", "US", "DE", "ES", "IT", "PT", "NL", "AR", "MA", "DZ", "TN", "TR", "PL", "RO", "GR"]

    /// Three letters, or the country spelt out, as some panels write it.
    private static let countryNames: [String: String] = [
        "FRA": "FR", "FRANCE": "FR", "FRENCH": "FR", "FRANCAIS": "FR", "FRANÇAIS": "FR", "BEL": "BE", "BELGIQUE": "BE", "BELGIUM": "BE",
        "SUI": "CH", "SUISSE": "CH", "SWISS": "CH", "CAN": "CA", "CANADA": "CA", "QUEBEC": "CA", "QUÉBEC": "CA", "USA": "US",
        "ENG": "UK", "GER": "DE", "DEU": "DE", "ITA": "IT", "ESP": "ES", "POR": "PT", "TUR": "TR", "ARB": "AR", "ARAB": "AR",
    ]

    /// A country written before a name or a category: "FR", "fr", "FRA" or "France".
    static func country(_ word: some StringProtocol) -> String? {
        let w = word.trimmingCharacters(in: .whitespaces).uppercased()
        if w.count == 2, countryCodes.contains(w) { return w }
        return countryNames[w]
    }

    /// "|FR| TF1", "[FR] TF1", "(FR) TF1", "🇫🇷 TF1", "FR: TF1", "FR - TF1",
    /// "FRANCE | TF1", "FR TF1" → FR, TF1. Panels write it every way.
    static func splitCountry(_ name: Substring) -> (country: String, rest: Substring) {
        let s = name.drop { $0 == " " }
        guard let first = s.first else { return ("", s) }
        // A flag: two regional indicator letters, one character.
        let scalars = Array(first.unicodeScalars)
        if scalars.count == 2, scalars.allSatisfy({ (0x1F1E6...0x1F1FF).contains($0.value) }) {
            let code = String(String.UnicodeScalarView(scalars.map { Unicode.Scalar($0.value - 0x1F1E6 + 65)! }))
            return (code == "GB" ? "UK" : code, s.dropFirst())
        }
        if first == "|", let close = s.dropFirst().firstIndex(of: "|"), s.distance(from: s.startIndex, to: close) <= 8 {
            return (s[s.index(after: s.startIndex)..<close].trimmingCharacters(in: .whitespaces), s[s.index(after: close)...])
        }
        if let closing = ["[": "]", "(": ")", "{": "}"][first], let close = s.dropFirst().firstIndex(of: Character(closing)),
           s.distance(from: s.startIndex, to: close) <= 9, let code = country(s[s.index(after: s.startIndex)..<close]) {
            return (code, s[s.index(after: close)...])
        }
        if let sep = s.prefix(9).firstIndex(where: { "|:-▎┃│»".contains($0) }) {
            let head = s[..<sep].trimmingCharacters(in: .whitespaces)
            // A real country, so "LCP-Public Sénat" or "RMC-Story" stay whole.
            if head.allSatisfy({ $0.isUppercase }), let code = country(head) { return (code, s[s.index(after: sep)...]) }
        }
        // "FR TF1": only a real country code, so "TV Breizh" stays whole.
        if let space = s.firstIndex(of: " "), s.distance(from: s.startIndex, to: space) == 2,
           s[..<space].allSatisfy({ $0.isUppercase && $0.isLetter }), bareCountries.contains(String(s[..<space])) {
            let code = String(s[..<space])
            return (code, s[space...])
        }
        return ("", s)
    }

    /// A category's country: as a channel's, or its first word spelt out
    /// ("FRANCE FHD | TV").
    static func categoryCountry(_ name: String) -> String {
        let split = splitCountry(Substring(name)).country
        if !split.isEmpty { return split }
        guard let first = name.split(whereSeparator: { $0.isWhitespace || "|:-_".contains($0) }).first else { return "" }
        let word = first.uppercased()
        return bareCountries.contains(word) ? word : countryNames[word] ?? ""
    }

    /// Tags glued to each other ("UHD/4K", "HD|FHD") or to the name ("TF1HD").
    private static func tags(in word: Substring) -> (name: Substring?, tags: [(rank: Int, label: String)])? {
        func tag(_ s: some StringProtocol) -> (rank: Int, label: String)? {
            qualityTags[s.uppercased().trimmingCharacters(in: CharacterSet(charactersIn: ".,;:()[]-_*✪★|"))]
        }
        let parts = word.split(whereSeparator: { "/|+".contains($0) })
        if parts.count > 1, let found = Optional(parts.compactMap(tag)), found.count == parts.count { return (nil, found) }
        let upper = word.uppercased()
        for suffix in ["UHD", "FHD", "HEVC", "HD", "SD", "4K"] where upper.hasSuffix(suffix) && upper.count > suffix.count + 1 {
            let head = word.dropLast(suffix.count)
            if let last = head.last, last.isNumber, head.contains(where: \.isLetter), let t = tag(suffix) { return (head, [t]) }
        }
        return nil
    }

    init(_ raw: String, fallbackCountry: String = "") {
        // Superscript runs ("ᵁᴴᴰ", glued as in "2ˢᴰ") become separate words.
        var text = ""
        var inSuper = false
        for ch in raw {
            if let plain = Self.superscripts[ch] {
                if !inSuper { text.append(" ") }
                text.append(plain)
                inSuper = true
            } else {
                if inSuper { text.append(" ") }
                text.append(ch)
                inSuper = false
            }
        }

        let split = Self.splitCountry(Substring(text))
        var country = split.country
        let rest = split.rest
        var words: [Substring] = []
        var tags: [(rank: Int, label: String)] = []
        var delay = 0
        let all = rest.split(whereSeparator: \.isWhitespace)
        var skipNext = false
        for (n, word) in all.enumerated() {
            if skipNext { skipNext = false; continue }
            // "+ 3": the sign and the hours apart.
            if word == "+", n + 1 < all.count, let h = Self.hours(all[n + 1]) {
                delay = h
                skipNext = true
                continue
            }
            if let h = Self.shift(word) { delay = h; continue }
            // "DUE+24": glued to the name.
            if let plus = word.lastIndex(of: "+"), plus != word.startIndex,
               let h = Self.hours(word[word.index(after: plus)...]),
               word[..<plus].suffix(2).allSatisfy(\.isLetter), word[..<plus].uppercased() != "CANAL" {
                delay = h
                words.append(word[..<plus])
                continue
            }
            let bare = word.uppercased().trimmingCharacters(in: CharacterSet(charactersIn: ".,;:()[]-_*✪★|"))
            if let tag = Self.qualityTags[bare] {
                tags.append(tag)
            } else if let glued = Self.tags(in: word) {
                tags += glued.tags
                if let name = glued.name { words.append(name) }
            } else if !bare.isEmpty || word.contains(where: \.isLetter) {
                words.append(word)
            }
        }
        self.delay = delay
        // "TF1 (FR)", "TF1 FR": the country at the end.
        if country.isEmpty, words.count > 1, let last = words.last {
            let inner = last.trimmingCharacters(in: CharacterSet(charactersIn: "()[]{}|"))
            if let code = Self.country(inner), inner != last || Self.bareCountries.contains(inner) {
                country = code
                words.removeLast()
            }
        }
        if country.isEmpty { country = fallbackCountry }
        self.country = country
        // "beIN Sports 2 HD FR": the country again, at the end.
        if let last = words.last, !country.isEmpty, words.count > 1,
           Self.country(last.trimmingCharacters(in: CharacterSet(charactersIn: "()[]{}|"))) == country { words.removeLast() }
        if words.isEmpty { words = rest.split(whereSeparator: \.isWhitespace) }

        if tags.isEmpty {
            quality = .untagged
        } else {
            var labels: [String] = []
            for t in tags.sorted(by: { $0.rank > $1.rank }) where !labels.contains(t.label) { labels.append(t.label) }
            quality = Quality(rank: tags.map(\.rank).max()!, label: labels.prefix(2).joined(separator: " "))
        }

        display = words.joined(separator: " ").trimmingCharacters(in: CharacterSet(charactersIn: " .-|"))
        if display.isEmpty { display = raw }

        let prefix = country.lowercased() + "|"
        let folded = Self.fold(words)
        key = prefix + (folded.isEmpty ? raw.lowercased() : folded)
        if words.count > 1, words.last == "1" { backupOf = prefix + Self.fold(words.dropLast()) }
    }

    /// The key ignores case, accents, spacing and punctuation ("CANAL+
    /// SPORT360" is "Canal Sport 360") and a plural s.
    static func fold(_ words: some Sequence<Substring>) -> String {
        var key = ""
        for word in words {
            var w = word.folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: nil)
            w = String(w.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }.map(Character.init))
            if w.count > 3, w.hasSuffix("s") { w.removeLast() }
            key += w
        }
        return key
    }

    /// "+4", "-2H", "|-12H|", "+6h": hours behind.
    private static func shift(_ word: Substring) -> Int? {
        let w = word.trimmingCharacters(in: CharacterSet(charactersIn: "|()[]"))
        guard let sign = w.first, sign == "+" || sign == "-" else { return nil }
        return hours(w.dropFirst())
    }

    /// "4", "4H", "24h", within two days.
    private static func hours(_ s: Substring) -> Int? {
        var digits = s
        if let last = digits.last, last == "H" || last == "h" { digits = digits.dropLast() }
        guard !digits.isEmpty, digits.count <= 2, digits.allSatisfy(\.isNumber), let h = Int(digits), (1...48).contains(h) else { return nil }
        return h
    }

}
