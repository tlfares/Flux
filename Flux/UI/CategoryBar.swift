import SwiftUI

/// The chips above each section: favorites, everything, then the
/// provider's categories. Long lists also open as a searchable sheet.
struct CategoryBar: View {
    static let all = "_all"
    static let favorites = "_fav"
    static let recent = "_recent"
    /// The bar's height, padding included.
    static let height: CGFloat = 56

    let categories: [Category]
    @Binding var selection: String
    var showsRecent = false
    /// Off when favorites are pinned in the lists instead.
    var showsFavorites = true

    @State private var showsList = false

    private var entries: [(id: String, name: String)] {
        var list: [(String, String)] = []
        if showsFavorites { list.append((Self.favorites, "Favoris")) }
        if showsRecent { list.append((Self.recent, "Récents")) }
        list.append((Self.all, "Tout"))
        return list + categories.map { ($0.id, $0.name) }
    }

    var body: some View {
        HStack(spacing: 8) {
            Button {
                showsList = true
            } label: {
                Image(systemName: "list.bullet")
                    .font(.body.weight(.semibold))
                    .frame(width: 44, height: 44)
                    .contentShape(.circle)
            }
            .buttonStyle(.plain)
            .glassEffect(.regular.interactive(), in: .circle)
            .accessibilityLabel("Toutes les catégories")

            // One glass track the chips slide in, like a segmented control:
            // the glass stays still, only the labels scroll. Glass on every
            // chip redrew under the finger and dragged the strip about.
            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 2) {
                        ForEach(entries, id: \.id) { entry in
                            chip(entry.id, entry.name).id(entry.id)
                        }
                    }
                    .padding(.horizontal, 4)
                    // Exactly the track's height. A lazy stack otherwise
                    // grows taller than the track, and the strip then scrolls
                    // up and down too, following any slanted swipe.
                    .frame(height: 44)
                }
                .scrollIndicators(.hidden)
                .scrollBounceBehavior(.basedOnSize, axes: [.horizontal, .vertical])
                .frame(height: 44)
                // Labels fade at the ends of the track instead of being sliced.
                .mask {
                    HStack(spacing: 0) {
                        LinearGradient(colors: [.clear, .black], startPoint: .leading, endPoint: .trailing).frame(width: 12)
                        Color.black
                        LinearGradient(colors: [.black, .clear], startPoint: .leading, endPoint: .trailing).frame(width: 12)
                    }
                }
                .clipShape(.capsule)
                .glassEffect(.regular, in: .capsule)
                .onAppear { reveal(selection, proxy) }
                .onChange(of: selection) { _, new in
                    withAnimation(Motion.smooth) { reveal(new, proxy) }
                }
                // Favoris or Récents showing up in front left the strip where
                // it was, the new chips out of sight to the left.
                .onChange(of: entries.prefix(3).map(\.id)) { reveal(selection, proxy) }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .frame(height: Self.height)
        .sensoryFeedback(.selection, trigger: selection)
        .sheet(isPresented: $showsList) {
            CategoryList(entries: entries, selection: $selection)
                .presentationDetents([.medium, .large])
        }
    }

    /// The chips in front show from the start of the strip; the others
    /// come to the middle.
    private func reveal(_ id: String, _ proxy: ScrollViewProxy) {
        let index = entries.firstIndex { $0.id == id } ?? 0
        if index < 3, let first = entries.first {
            proxy.scrollTo(first.id, anchor: .leading)
        } else {
            proxy.scrollTo(id, anchor: .center)
        }
    }

    private func chip(_ id: String, _ name: String) -> some View {
        let selected = id == selection
        return Button {
            withAnimation(Motion.snappy) { selection = id }
        } label: {
            HStack(spacing: 5) {
                if id == Self.favorites { Image(systemName: "star.fill").font(.caption) }
                if id == Self.recent { Image(systemName: "clock").font(.caption) }
                Text(DisplayName.clean(name)).lineLimit(1)
            }
            .font(.subheadline.weight(selected ? .semibold : .medium))
            .foregroundStyle(selected ? AnyShapeStyle(.white) : AnyShapeStyle(.primary))
            .padding(.horizontal, 14)
            .frame(height: 36)
            .background(FluxTint.horizontal.opacity(selected ? 1 : 0), in: .capsule)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct CategoryList: View {
    let entries: [(id: String, name: String)]
    @Binding var selection: String
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        NavigationStack {
            List {
                ForEach(filtered, id: \.id) { entry in
                    Button {
                        selection = entry.id
                        dismiss()
                    } label: {
                        HStack {
                            Text(entry.name).foregroundStyle(.primary)
                            Spacer()
                            if entry.id == selection {
                                Image(systemName: "checkmark").foregroundStyle(.tint).fontWeight(.semibold)
                            }
                        }
                    }
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Catégorie")
            .navigationTitle("Catégories")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(role: .close) { dismiss() }
                }
            }
        }
    }

    private var filtered: [(id: String, name: String)] {
        guard !query.isEmpty else { return entries }
        let q = SearchIndex.fold(query)
        return entries.filter { SearchIndex.fold($0.name).contains(q) }
    }
}
