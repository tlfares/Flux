import SwiftUI

/// The top of a film or series page: the backdrop fading into the
/// background, poster and title over it. Side by side when there's width
/// (landscape, iPad), stacked otherwise.
struct DetailHeader<Actions: View>: View {
    let title: String
    let poster: String?
    let backdrop: String?
    let facts: [String]
    @ViewBuilder var actions: Actions

    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.verticalSizeClass) private var vSize

    private var isWide: Bool { hSize == .regular || vSize == .compact }

    var body: some View {
        ZStack(alignment: .bottom) {
            RemoteImage(url: backdrop ?? poster, size: 900) { Color.clear }
                .frame(height: isWide ? 360 : 300)
                .frame(maxWidth: .infinity)
                .clipped()
                .blur(radius: backdrop == nil ? 30 : 0)
                .overlay {
                    LinearGradient(colors: [.clear, Color.screenBackground.opacity(0.7), Color.screenBackground],
                                   startPoint: .top, endPoint: .bottom)
                }

            HStack(alignment: .bottom, spacing: 18) {
                RemoteImage(url: poster, size: 260) { Rectangle().fill(.white.opacity(0.06)) }
                    .frame(width: isWide ? 150 : 112, height: isWide ? 225 : 168)
                    .clipShape(.rect(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(.white.opacity(0.1)))
                    .shadow(color: .black.opacity(0.4), radius: 18, y: 8)

                VStack(alignment: .leading, spacing: 8) {
                    Text(title)
                        .font(isWide ? .largeTitle.bold() : .title2.bold())
                        .lineLimit(3)
                        .minimumScaleFactor(0.7)
                    if !facts.isEmpty {
                        Text(facts.joined(separator: " · "))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                    if isWide { actions.padding(.top, 6) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 20)
        }
        if !isWide {
            actions
                .padding(.horizontal, 20)
                .padding(.top, 16)
        }
    }
}

struct MovieDetailView: View {
    let movie: Movie

    @Environment(AccountStore.self) private var account
    @Environment(Library.self) private var library
    @Environment(PlayerCenter.self) private var player
    @State private var details: MovieDetails?

    private var key: String { Library.key(.movie, movie.id) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                DetailHeader(title: DisplayName.clean(movie.name), poster: details?.poster ?? movie.poster, backdrop: details?.backdrop, facts: facts) {
                    actions
                }
                VStack(alignment: .leading, spacing: 16) {
                    if let plot = details?.plot {
                        Text(plot).font(.body).foregroundStyle(.primary.opacity(0.9))
                    } else if details == nil {
                        ProgressView().frame(maxWidth: .infinity)
                    }
                    if let director = details?.director { credit("Réalisation", director) }
                    if let cast = details?.cast { credit("Avec", cast) }
                }
                .padding(20)
                .frame(maxWidth: 820, alignment: .leading)
            }
        }
        .scrollIndicators(.hidden)
        .ignoresSafeArea(edges: .top)
        .background(Color.screenBackground.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { FavoriteButton(favorite: .movie(movie)) }
        }
        .task {
            guard details == nil, let client = account.client else { return }
            details = (try? await client.movieDetails(movie.id)) ?? MovieDetails()
        }
    }

    private func credit(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            Text(value).font(.subheadline)
        }
    }

    private var facts: [String] {
        var f: [String] = []
        if let year = details?.year { f.append(year) }
        if let duration = details?.duration { f.append(duration) }
        if let genre = details?.genre { f.append(genre) }
        if let rating = movie.rating { f.append("★ " + rating.formatted(.number.precision(.fractionLength(1)))) }
        return f
    }

    private var actions: some View {
        let progress = library.progress[key]
        let resumable = progress?.isResumable == true
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Button {
                    if let client = account.client { player.play(movie, client: client, library: library) }
                } label: {
                    Label(resumable ? "Reprendre" : "Lecture", systemImage: "play.fill")
                        .font(.headline)
                        .frame(maxWidth: 260)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.glassProminent)
                .controlSize(.large)

                if resumable {
                    Button {
                        if let client = account.client { player.play(movie, client: client, library: library, fromStart: true) }
                    } label: {
                        Image(systemName: "arrow.counterclockwise")
                            .font(.headline)
                            .padding(.vertical, 4)
                    }
                    .buttonStyle(.glass)
                    .controlSize(.large)
                    .accessibilityLabel("Depuis le début")
                }
            }
            if let progress, resumable {
                HStack(spacing: 10) {
                    ProgressView(value: progress.fraction).tint(.accentColor).frame(maxWidth: 200)
                    Text(Duration.seconds(max(0, progress.duration - progress.position)).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated)) + " restantes")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

struct SeriesDetailView: View {
    let series: Series

    @Environment(AccountStore.self) private var account
    @Environment(Library.self) private var library
    @Environment(PlayerCenter.self) private var player
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var details: SeriesDetails?
    @State private var failed = false
    @State private var season: Int?

    private var seasons: [Int] { details?.seasons.keys.sorted() ?? [] }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                DetailHeader(title: DisplayName.clean(series.name), poster: series.cover, backdrop: details?.backdrop, facts: facts) {
                    if let next = nextEpisode {
                        Button {
                            if let client = account.client { player.play(next, of: series, client: client, library: library) }
                        } label: {
                            Label(library.progress[Library.key(.series, next.id)] == nil ? "S\(next.season) E\(next.number)" : "Reprendre S\(next.season) E\(next.number)",
                                  systemImage: "play.fill")
                                .font(.headline)
                                .frame(maxWidth: 260)
                                .padding(.vertical, 4)
                        }
                        .buttonStyle(.glassProminent)
                        .controlSize(.large)
                    }
                }

                VStack(alignment: .leading, spacing: 18) {
                    if let plot = details?.plot {
                        Text(plot).foregroundStyle(.primary.opacity(0.9)).lineLimit(6)
                    }

                    if details == nil && !failed {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 20)
                    } else if seasons.isEmpty {
                        ContentUnavailableView("Aucun épisode", systemImage: "tv")
                    } else {
                        seasonPicker
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 340), spacing: 12)], spacing: 12) {
                            ForEach(details?.seasons[season ?? seasons[0]] ?? []) { episode in
                                EpisodeRow(episode: episode, progress: library.progress[Library.key(.series, episode.id)]) {
                                    if let client = account.client { player.play(episode, of: series, client: client, library: library) }
                                }
                            }
                        }
                    }
                }
                .padding(20)
            }
        }
        .scrollIndicators(.hidden)
        .ignoresSafeArea(edges: .top)
        .background(Color.screenBackground.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { FavoriteButton(favorite: .series(series)) }
        }
        .task {
            guard details == nil, let client = account.client else { return }
            do {
                let loaded = try await client.seriesDetails(series.id)
                details = loaded
                season = nextEpisode?.season ?? loaded.seasons.keys.min()
            } catch {
                failed = true
            }
        }
    }

    private var facts: [String] {
        var f: [String] = []
        if let year = details?.year ?? series.year { f.append(year) }
        if !seasons.isEmpty { f.append(seasons.count == 1 ? "1 saison" : "\(seasons.count) saisons") }
        if let genre = details?.genre { f.append(genre) }
        if let rating = series.rating { f.append("★ " + rating.formatted(.number.precision(.fractionLength(1)))) }
        return f
    }

    /// The episode last left unfinished, or the one after the last
    /// finished, or the very first.
    private var nextEpisode: Episode? {
        guard let details else { return nil }
        let all = seasons.flatMap { details.seasons[$0] ?? [] }
        var latest: (Int, Date)?
        for (i, e) in all.enumerated() {
            if let p = library.progress[Library.key(.series, e.id)], latest == nil || p.date > latest!.1 { latest = (i, p.date) }
        }
        guard let (i, _) = latest else { return all.first }
        if library.progress[Library.key(.series, all[i].id)]?.isResumable == true { return all[i] }
        return i + 1 < all.count ? all[i + 1] : all[i]
    }

    @ViewBuilder private var seasonPicker: some View {
        if seasons.count <= 4, sizeClass == .compact || seasons.count <= 6 {
            if seasons.count > 1 {
                GlassSegmentedPicker("Saison", options: seasons, selection: Binding(get: { season ?? seasons[0] }, set: { season = $0 }),
                                     title: { "Saison \($0)" })
            }
        } else {
            ScrollView(.horizontal) {
                GlassEffectContainer(spacing: 8) {
                    HStack(spacing: 8) {
                        ForEach(seasons, id: \.self) { s in
                            let selected = s == (season ?? seasons[0])
                            Button("Saison \(s)") { withAnimation(Motion.snappy) { season = s } }
                                .font(.subheadline.weight(selected ? .semibold : .medium))
                                .foregroundStyle(selected ? AnyShapeStyle(.white) : AnyShapeStyle(.primary))
                                .padding(.horizontal, 14)
                                .frame(height: 36)
                                .buttonStyle(.plain)
                                .background(FluxTint.horizontal.opacity(selected ? 1 : 0), in: .capsule)
                                .glassEffect(selected ? .clear.interactive() : .regular.interactive(), in: .capsule)
                        }
                    }
                }
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
            .sensoryFeedback(.selection, trigger: season)
        }
    }
}

private struct EpisodeRow: View {
    let episode: Episode
    let progress: Library.Progress?
    let play: () -> Void

    var body: some View {
        Button(action: play) {
            HStack(spacing: 12) {
                RemoteImage(url: episode.image, size: 180) {
                    ZStack {
                        Rectangle().fill(.white.opacity(0.06))
                        Text("\(episode.number)").font(.title3.bold()).foregroundStyle(.tertiary)
                    }
                }
                .frame(width: 128, height: 72)
                .clipShape(.rect(cornerRadius: 12))
                .overlay(alignment: .bottom) {
                    if let progress, progress.fraction > 0 {
                        ProgressView(value: progress.fraction).tint(.accentColor).padding(.horizontal, 6).padding(.bottom, 4)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Épisode \(episode.number)")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.tint)
                    Text(episode.title)
                        .font(.subheadline.weight(.medium))
                        .lineLimit(2)
                    if let duration = episode.duration {
                        Text(duration).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Image(systemName: progress?.fraction ?? 0 >= 0.95 ? "checkmark.circle.fill" : "play.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.tint)
            }
            .padding(10)
            .contentShape(.rect(cornerRadius: 20))
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 20))
    }
}
