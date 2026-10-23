import Foundation

/// The order the categories show in, and those put away, chosen by hand
/// for each account and section. Categories the provider adds later come
/// after those placed, in its order.
@MainActor
@Observable
final class CategoryLayouts {
    static let shared = CategoryLayouts()

    private struct Layout: Codable {
        var order: [String] = []
        var hidden: Set<String> = []
    }

    private static let storageKey = "categories.layouts"
    private var layouts: [String: Layout]
    /// The account the screens show, set by the app as it changes.
    var account = ""

    private init() {
        layouts = UserDefaults.standard.data(forKey: Self.storageKey)
            .flatMap { try? JSONDecoder().decode([String: Layout].self, from: $0) } ?? [:]
    }

    private func key(_ kind: MediaKind) -> String { account + "|" + kind.rawValue }

    /// The categories in the chosen order, without those put away unless asked.
    func arranged(_ categories: [Category], _ kind: MediaKind, includingHidden: Bool = false) -> [Category] {
        guard let layout = layouts[key(kind)] else { return categories }
        let rank = Dictionary(layout.order.enumerated().map { ($1, $0) }, uniquingKeysWith: { a, _ in a })
        let placed = categories.filter { rank[$0.id] != nil }.sorted { rank[$0.id]! < rank[$1.id]! }
        let list = placed + categories.filter { rank[$0.id] == nil }
        return includingHidden ? list : list.filter { !layout.hidden.contains($0.id) }
    }

    func isHidden(_ id: String, _ kind: MediaKind) -> Bool {
        layouts[key(kind)]?.hidden.contains(id) ?? false
    }

    func isCustomized(_ kind: MediaKind) -> Bool { layouts[key(kind)] != nil }

    /// Keeps `ids` as the order, all of them shown or not.
    func setOrder(_ ids: [String], _ kind: MediaKind) {
        layouts[key(kind), default: Layout()].order = ids
        save()
    }

    /// One place up or down among `categories` (all of them, hidden too).
    func move(_ id: String, by offset: Int, in categories: [Category], _ kind: MediaKind) {
        var ids = arranged(categories, kind, includingHidden: true).map(\.id)
        guard let from = ids.firstIndex(of: id) else { return }
        let to = min(max(from + offset, 0), ids.count - 1)
        guard to != from else { return }
        ids.insert(ids.remove(at: from), at: to)
        setOrder(ids, kind)
    }

    func toggleHidden(_ id: String, _ kind: MediaKind) {
        var layout = layouts[key(kind), default: Layout()]
        if layout.hidden.contains(id) { layout.hidden.remove(id) } else { layout.hidden.insert(id) }
        layouts[key(kind)] = layout
        save()
    }

    /// Back to the provider's order, everything shown.
    func reset(_ kind: MediaKind) {
        layouts[key(kind)] = nil
        save()
    }

    private func save() {
        UserDefaults.standard.set(try? JSONEncoder().encode(layouts), forKey: Self.storageKey)
    }
}
