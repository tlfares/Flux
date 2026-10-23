import SwiftUI

/// The chips above each section: favorites, everything, then the
/// provider's categories, in the order chosen for them. They all open as a
/// panel too, where they're searched, reordered and put away.
struct CategoryBar: View {
    static let all = "_all"
    static let favorites = "_fav"
    static let recent = "_recent"
    /// The bar's height, padding included.
    static let height: CGFloat = 56

    let categories: [Category]
    let kind: MediaKind
    @Binding var selection: String
    var showsRecent = false
    /// Off when favorites are pinned in the lists instead.
    var showsFavorites = true

    @State private var showsList = false
    @Environment(Catalog.self) private var catalog
    private var layouts: CategoryLayouts { .shared }

    private var entries: [(id: String, name: String)] {
        var list: [(String, String)] = []
        if showsFavorites { list.append((Self.favorites, String(localized: "Favoris"))) }
        if showsRecent { list.append((Self.recent, String(localized: "Récents"))) }
        list.append((Self.all, String(localized: "Tout")))
        return list + layouts.arranged(categories, kind).map { ($0.id, $0.name) }
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
        // A category that went away (another country, another account): everything instead.
        .onChange(of: categories.map(\.id), initial: true) { _, ids in
            if !ids.isEmpty, !selection.hasPrefix("_"), !ids.contains(selection) { selection = Self.all }
        }
        .sheet(isPresented: $showsList) {
            CategoryPanel(categories: categories, kind: kind, selection: $selection,
                          showsRecent: showsRecent, showsFavorites: showsFavorites, count: count)
                .presentationDetents([.medium, .large])
        }
    }

    /// What a category holds: channels put together, films or series.
    private func count(_ id: String) -> Int {
        switch kind {
        case .live: catalog.guide.isEmpty ? catalog.live.index[id]?.count ?? 0 : catalog.guide.count(in: id)
        case .movie: catalog.movies.index[id]?.count ?? 0
        case .series: catalog.series.index[id]?.count ?? 0
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

/// Every category at once: the shortcuts as tiles, then the categories
/// with what's in them. Editing reorders them and puts some away.
private struct CategoryPanel: View {
    let categories: [Category]
    let kind: MediaKind
    @Binding var selection: String
    let showsRecent: Bool
    let showsFavorites: Bool
    let count: (String) -> Int

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var isEditing = false
    private var layouts: CategoryLayouts { .shared }

    var body: some View {
        NavigationStack {
            Group {
                if isEditing { editor } else { browser }
            }
            .navigationTitle("Catégories")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isEditing ? "OK" : "Modifier") {
                        withAnimation(Motion.smooth) { isEditing.toggle() }
                    }
                    .fontWeight(isEditing ? .semibold : .regular)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(role: .close) { dismiss() }
                }
            }
        }
    }

    // MARK: Choosing

    private var browser: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if query.isEmpty { shortcuts }
                let shown = filtered
                if shown.isEmpty {
                    ContentUnavailableView.search(text: query).padding(.top, 40)
                } else {
                    LazyVStack(spacing: 4) {
                        ForEach(shown) { category in
                            row(category)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Rechercher une catégorie")
    }

    private var shortcuts: some View {
        HStack(spacing: 10) {
            if showsFavorites { tile(CategoryBar.favorites, String(localized: "Favoris"), "star.fill") }
            if showsRecent { tile(CategoryBar.recent, String(localized: "Récents"), "clock.fill") }
            tile(CategoryBar.all, String(localized: "Tout"), "square.grid.2x2.fill")
        }
    }

    private func tile(_ id: String, _ title: String, _ symbol: String) -> some View {
        let selected = id == selection
        return Button {
            choose(id)
        } label: {
            VStack(spacing: 8) {
                Image(systemName: symbol).font(.title3)
                Text(title).font(.subheadline.weight(.semibold)).lineLimit(1)
            }
            .foregroundStyle(selected ? AnyShapeStyle(.white) : AnyShapeStyle(.primary))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background {
                if selected { RoundedRectangle(cornerRadius: 20).fill(FluxTint.horizontal) }
            }
            .contentShape(.rect(cornerRadius: 20))
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 20))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func row(_ category: Category) -> some View {
        let selected = category.id == selection
        let label = CategoryLabel(category.name, showsFlags: showsFlags)
        return Button {
            choose(category.id)
        } label: {
            HStack(spacing: 12) {
                CategoryBadge(label: label)
                Text(label.title)
                    .font(.body.weight(selected ? .semibold : .regular))
                    .lineLimit(1)
                Spacer(minLength: 8)
                Text(count(category.id).formatted())
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(selected ? AnyShapeStyle(.white.opacity(0.85)) : AnyShapeStyle(.secondary))
            }
            .foregroundStyle(selected ? AnyShapeStyle(.white) : AnyShapeStyle(.primary))
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                selected ? AnyShapeStyle(FluxTint.horizontal) : AnyShapeStyle(Color.primary.opacity(0.05)),
                in: .rect(cornerRadius: 16)
            )
            .contentShape(.rect(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func choose(_ id: String) {
        withAnimation(Motion.snappy) { selection = id }
        dismiss()
    }

    private var filtered: [Category] {
        let list = layouts.arranged(categories, kind)
        guard !query.isEmpty else { return list }
        let q = SearchIndex.fold(query)
        return list.filter { SearchIndex.fold(DisplayName.clean($0.name)).contains(q) }
    }

    /// Flags rather than initials when categories come from several countries.
    private var showsFlags: Bool {
        Set(categories.lazy.map { ChannelName.splitCountry(Substring($0.name)).country }.filter { !$0.isEmpty }).count > 1
    }

    // MARK: Editing

    private var editor: some View {
        let list = layouts.arranged(categories, kind, includingHidden: true)
        return List {
            Section {
                ForEach(list) { category in
                    let hidden = layouts.isHidden(category.id, kind)
                    let label = CategoryLabel(category.name, showsFlags: showsFlags)
                    HStack(spacing: 12) {
                        CategoryBadge(label: label)
                        Text(label.title).lineLimit(1)
                        Spacer(minLength: 8)
                        Button {
                            withAnimation(Motion.snappy) { layouts.toggleHidden(category.id, kind) }
                        } label: {
                            Image(systemName: hidden ? "eye.slash" : "eye")
                                .font(.body.weight(.medium))
                                .foregroundStyle(hidden ? AnyShapeStyle(.secondary) : AnyShapeStyle(.tint))
                                .frame(width: 36, height: 36)
                                .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(hidden ? "Afficher" : "Masquer")
                    }
                    .opacity(hidden ? 0.45 : 1)
                }
                .onMove { from, to in
                    var ids = list.map(\.id)
                    ids.move(fromOffsets: from, toOffset: to)
                    layouts.setOrder(ids, kind)
                }
            } footer: {
                Text("Fais glisser pour changer l’ordre. Les catégories masquées restent dans « Tout ».")
            }
            if layouts.isCustomized(kind) {
                Section {
                    Button("Réinitialiser l’ordre", systemImage: "arrow.counterclockwise", role: .destructive) {
                        withAnimation(Motion.smooth) { layouts.reset(kind) }
                    }
                }
            }
        }
        .environment(\.editMode, .constant(.active))
    }
}

/// A category's name for the panel, its flag or initials apart.
private struct CategoryLabel {
    var title: String
    var flag: String?
    var initials: String

    init(_ name: String, showsFlags: Bool) {
        let country = ChannelName.splitCountry(Substring(name)).country
        var title = DisplayName.clean(name)
        // "🇺🇸 News": the flag goes in the badge.
        let again = ChannelName.splitCountry(Substring(title))
        if !again.country.isEmpty, title.unicodeScalars.first.map({ (0x1F1E6...0x1F1FF).contains($0.value) }) == true {
            title = again.rest.trimmingCharacters(in: .whitespaces)
        }
        self.title = title
        flag = showsFlags ? FreeScope.flag(country) : nil
        let words = title.split { !$0.isLetter && !$0.isNumber }
        initials = String(words.prefix(2).compactMap(\.first)).uppercased().nonEmpty ?? "#"
    }
}

/// The square at the start of a category's row: its country's flag, or
/// its initials on a colour of its own.
private struct CategoryBadge: View {
    let label: CategoryLabel

    var body: some View {
        Group {
            if let flag = label.flag {
                Text(flag).font(.title3)
            } else {
                Text(label.initials)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(tint.gradient, in: .rect(cornerRadius: 10))
            }
        }
        .frame(width: 36, height: 36)
    }

    /// The same muted hue for a name every time.
    private var tint: Color {
        var hash: UInt64 = 1469598103934665603
        for byte in label.title.utf8 { hash = (hash ^ UInt64(byte)) &* 1099511628211 }
        return Color(hue: Double(hash % 360) / 360, saturation: 0.45, brightness: 0.62)
    }
}
