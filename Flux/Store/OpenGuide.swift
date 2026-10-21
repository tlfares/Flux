import Foundation

/// The French TNT's guide from XML TV Fr (xmltvfr.fr), an open guide built
/// for apps like this one: titles, summaries and a picture for every
/// programme, more reliable for these channels than what panels send.
/// Fetched at most twice a day (the site asks to spare its bandwidth) and
/// kept on disk.
nonisolated enum OpenGuide {
    /// The TNT file: 30 channels over five days, half a megabyte as .xz.
    private static let source = URL(string: "https://xmltvfr.fr/xmltv/xmltv_tnt.xz")!
    private static let maxAge: TimeInterval = 12 * 3600
    private static var file: URL { URL.cachesDirectory.appending(path: "openguide-tnt.xz") }

    /// Channel key ("fr|tf1", as channel groups have it) → its programmes
    /// from an hour ago on, in order.
    @concurrent
    static func load() async -> [String: [Programme]] {
        let age = (try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate).map { Date().timeIntervalSince($0) }
        if age.map({ $0 > maxAge }) ?? true, let (data, response) = try? await URLSession.shared.data(from: source),
           (response as? HTTPURLResponse)?.statusCode == 200, !data.isEmpty {
            try? data.write(to: file, options: .atomic)
        }
        guard let packed = try? Data(contentsOf: file),
              let xml = try? (packed as NSData).decompressed(using: .lzma) as Data else { return [:] }
        return parse(xml)
    }

    private static func parse(_ xml: Data) -> [String: [Programme]] {
        let reader = Reader()
        let parser = XMLParser(data: xml)
        parser.delegate = reader
        parser.parse()
        // XML TV ids ("TF1.fr") → the keys channel groups go by.
        var keys: [String: String] = [:]
        for (id, name) in reader.channels {
            let key = ChannelName("|FR| " + name).key
            keys[id] = FrenchChannels.known(key)?.key ?? key
        }
        var guide: [String: [Programme]] = [:]
        let since = Date().addingTimeInterval(-3600)
        for (id, programme) in reader.programmes where programme.end > since {
            guard let key = keys[id] else { continue }
            guide[key, default: []].append(programme)
        }
        return guide.mapValues { $0.sorted { $0.start < $1.start } }
    }

    private final class Reader: NSObject, XMLParserDelegate {
        var channels: [(id: String, name: String)] = []
        var programmes: [(channel: String, programme: Programme)] = []

        private var channelID: String?
        private var current: (channel: String, start: Date, end: Date)?
        private var title: String?
        private var summary: String?
        private var image: String?
        private var text = ""

        private let dates: DateFormatter = {
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyyMMddHHmmss Z"
            return f
        }()

        func parser(_ parser: XMLParser, didStartElement element: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String] = [:]) {
            text = ""
            switch element {
            case "channel":
                channelID = attributes["id"]
            case "programme":
                if let channel = attributes["channel"], let start = attributes["start"].flatMap(dates.date), let end = attributes["stop"].flatMap(dates.date) {
                    current = (channel, start, end)
                }
                title = nil; summary = nil; image = nil
            case "icon" where current != nil:
                image = attributes["src"]
            default: break
            }
        }

        func parser(_ parser: XMLParser, foundCharacters string: String) { text += string }

        func parser(_ parser: XMLParser, didEndElement element: String, namespaceURI: String?, qualifiedName: String?) {
            // Quotes come escaped in some summaries (\"La Quotidienne\").
            let value = text.replacingOccurrences(of: "\\\"", with: "\"").trimmingCharacters(in: .whitespacesAndNewlines)
            switch element {
            case "display-name" where current == nil:
                if let channelID, !channels.contains(where: { $0.id == channelID }) { channels.append((channelID, value)) }
            case "channel":
                channelID = nil
            case "title" where current != nil && title == nil:
                title = value.nonEmpty
            case "desc" where current != nil && summary == nil:
                summary = value.nonEmpty
            case "programme":
                if let current, let title {
                    programmes.append((current.channel, Programme(title: title, summary: summary, start: current.start, end: current.end, image: image)))
                }
                current = nil
            default: break
            }
            text = ""
        }
    }
}
