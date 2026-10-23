import AVFoundation
import SwiftUI

/// Live TV on the whole screen, as Oqee does it: the banner comes up from
/// the bottom with the channel and what's on, up and down zap, right opens
/// the channel list over the picture. Menu closes what's open, then the
/// screen.
struct TVLiveScreen: View {
    @Environment(TVPlayer.self) private var player
    @Environment(EPG.self) private var epg
    @Environment(AccountStore.self) private var account

    @State private var showsBanner = true
    @State private var showsList = false
    @State private var hideBanner: Task<Void, Never>?
    @FocusState private var pictureFocused: Bool

    var body: some View {
        ZStack {
            Color.black
            VideoSurface(player: player.player)
            if player.isBuffering, player.error == nil {
                ProgressView().scaleEffect(1.4)
            }
            if let error = player.error {
                ContentUnavailableView {
                    Label("Pas d’image", systemImage: "antenna.radiowaves.left.and.right.slash")
                } description: {
                    Text(error)
                }
            }

            // The picture itself takes the remote: clicks, swipes, play.
            Button { show(!showsBanner) } label: { Color.clear }
                .buttonStyle(BareButtonStyle())
                .focused($pictureFocused)
                .disabled(showsList)
                .onMoveCommand(perform: move)
                // Held down: the channel's other streams and the subtitles, when there are some.
                .contextMenu {
                    if let group = player.channel, group.hasChoice {
                        Section("Regarder en") {
                            ForEach(group.variants(delay: player.variant?.delay ?? group.baseDelay), id: \.id) { variant in
                                Button { player.pick(variant) } label: {
                                    if variant.id == player.variant?.id { Label(variant.quality.label, systemImage: "checkmark") }
                                    else { Text(variant.quality.label) }
                                }
                            }
                        }
                    }
                    let subtitles = player.subtitles
                    if !subtitles.options.isEmpty {
                        Picker("Sous-titres", selection: Binding(get: { subtitles.selected }, set: { subtitles.choose($0) })) {
                            Text("Désactivés").tag(AVMediaSelectionOption?.none)
                            ForEach(subtitles.options, id: \.self) { option in
                                Text(Subtitles.name(option)).tag(Optional(option))
                            }
                        }
                    }
                }

            if showsBanner, !showsList {
                TVLiveBanner()
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .allowsHitTesting(false)
            }
            if player.isPaused {
                Image(systemName: "pause.fill")
                    .font(.system(size: 60, weight: .semibold))
                    .padding(40)
                    .glassEffect(.regular, in: .circle)
                    .allowsHitTesting(false)
            }
            if showsList {
                HStack(spacing: 0) {
                    TVChannelList { showsList = false; show(true) }
                    Spacer(minLength: 0)
                }
                // Dark enough under the list for its rows to read over any picture.
                .background(alignment: .leading) {
                    LinearGradient(stops: [.init(color: .black.opacity(0.92), location: 0), .init(color: .black.opacity(0.85), location: 0.55),
                                           .init(color: .black.opacity(0), location: 1)],
                                   startPoint: .leading, endPoint: .trailing)
                        .frame(width: 1400)
                }
                .transition(.move(edge: .leading).combined(with: .opacity))
            }
        }
        .ignoresSafeArea()
        .animation(.easeInOut(duration: 0.3), value: showsBanner)
        .animation(.easeInOut(duration: 0.3), value: showsList)
        .onPlayPauseCommand {
            player.togglePause()
            show(true)
        }
        .onExitCommand {
            if showsList { showsList = false; pictureFocused = true }
            else if showsBanner { show(false) }
            else { player.closeLive() }
        }
        .onAppear {
            pictureFocused = true
            show(true)
        }
        .onChange(of: player.channel?.id) { show(true) }
        .onChange(of: showsList) { _, open in if !open { pictureFocused = true } }
        .task(id: player.channel?.id) {
            guard let group = player.channel, let client = account.client else { return }
            await epg.load(group, client: client, limit: 4)
        }
    }

    private func move(_ direction: MoveCommandDirection) {
        switch direction {
        case .up: player.zap(-1)
        case .down: player.zap(1)
        case .right: showsList = true
        case .left: show(true)
        @unknown default: break
        }
    }

    /// The banner, gone again after a few seconds unless paused.
    private func show(_ on: Bool) {
        showsBanner = on
        hideBanner?.cancel()
        guard on else { return }
        hideBanner = Task {
            try? await Task.sleep(for: .seconds(6))
            guard !Task.isCancelled, !player.isPaused else { return }
            showsBanner = false
        }
    }
}

/// A button with no look of its own: the picture under the controls.
private struct BareButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.contentShape(.rect) }
}

/// The banner over the bottom of the picture: the channel, what's on,
/// how far it's got and what comes next.
private struct TVLiveBanner: View {
    @Environment(TVPlayer.self) private var player
    @Environment(EPG.self) private var epg

    var body: some View {
        if let group = player.channel {
            let upcoming = epg.upcoming(epg.source(group))
            let now = upcoming.first { $0.isOn }
            let next = upcoming.first { $0.start >= (now?.end ?? .now) }
            VStack {
                Spacer()
                HStack(alignment: .center, spacing: 40) {
                    VStack(spacing: 10) {
                        // The programme's still with the logo over it, else the logo.
                        if let image = now?.image {
                            RemoteImage(url: image, size: 400, contentMode: .fill) { Color.white.opacity(0.08) }
                                .frame(width: 256, height: 144)
                                .overlay(alignment: .bottomLeading) {
                                    TVChannelLogo(group: group, width: 80, height: 45)
                                        .shadow(color: .black.opacity(0.6), radius: 6)
                                        .padding(10)
                                }
                                .clipShape(.rect(cornerRadius: 22))
                        } else {
                            TVChannelLogo(group: group, width: 190, height: 108)
                                .padding(18)
                                .background(.white.opacity(0.08), in: .rect(cornerRadius: 24))
                        }
                        if let number = group.number {
                            Text("\(number)").font(.title3.weight(.bold).monospacedDigit()).foregroundStyle(.secondary)
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 14) {
                            Label("EN DIRECT", systemImage: "circle.fill")
                                .labelStyle(LiveLabelStyle())
                            Text(group.name).font(.headline).foregroundStyle(.secondary)
                            if let variant = player.variant, group.hasChoice {
                                Text(variant.quality.label)
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 4)
                                    .background(.white.opacity(0.14), in: .capsule)
                            }
                        }
                        Text(now?.title ?? group.name)
                            .font(.title2.weight(.bold))
                            .lineLimit(1)
                        if let now {
                            HStack(spacing: 18) {
                                Text(now.timeRange).font(.callout.monospacedDigit()).foregroundStyle(.secondary)
                                    .fixedSize()
                                LiveProgress(programme: now, height: 7).frame(maxWidth: 520)
                                Text(remaining(now)).font(.callout).foregroundStyle(.secondary)
                                    .fixedSize()
                            }
                        }
                        if let next {
                            Text("À suivre · \(next.start.formatted(date: .omitted, time: .shortened))  \(next.title)")
                                .font(.callout)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if let summary = now?.summary {
                        Text(summary)
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .lineLimit(4)
                            .frame(width: 520, alignment: .leading)
                    }
                }
                .padding(.horizontal, 44)
                .padding(.vertical, 34)
                .glassEffect(.regular, in: .rect(cornerRadius: 44))
                .overlay(alignment: .bottomTrailing) {
                    Text(hint(group))
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .padding(.trailing, 44)
                        .padding(.bottom, 14)
                }
                .padding(.horizontal, 80)
                .padding(.bottom, 60)
            }
            .background(alignment: .bottom) {
                LinearGradient(colors: [.clear, .black.opacity(0.7)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 520)
            }
        }
    }

    /// The remote's keys, and the long press when it has something to offer.
    private func hint(_ group: ChannelGroup) -> String {
        let keys = String(localized: "▲▼ Chaîne  ·  ▶ Liste des chaînes")
        let streams = group.hasChoice, subtitles = !player.subtitles.options.isEmpty
        if streams && subtitles { return keys + String(localized: "  ·  Appui long : flux et sous-titres") }
        if streams { return keys + String(localized: "  ·  Appui long : changer de flux") }
        if subtitles { return keys + String(localized: "  ·  Appui long : sous-titres") }
        return keys
    }

    private func remaining(_ p: Programme) -> String {
        let minutes = max(0, Int(p.end.timeIntervalSinceNow / 60))
        return minutes >= 60 ? String(localized: "\(minutes / 60) h \(String(format: "%02d", minutes % 60)) restantes") : String(localized: "\(minutes) min restantes")
    }
}

private struct LiveLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 8) {
            configuration.icon.font(.system(size: 12)).foregroundStyle(.red)
            configuration.title.font(.caption.weight(.heavy))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .background(.white.opacity(0.14), in: .capsule)
    }
}

/// The channels down the left of the picture, the one playing in focus:
/// moving through them shows what's on, a click tunes in.
private struct TVChannelList: View {
    let close: () -> Void

    @Environment(TVPlayer.self) private var player
    @Environment(EPG.self) private var epg
    @Environment(Library.self) private var library
    @Environment(Catalog.self) private var catalog
    @Environment(AccountStore.self) private var account
    @State private var favoritesOnly = false
    @FocusState private var focused: String?

    private var channels: [ChannelGroup] {
        favoritesOnly ? library.favorites(.live).compactMap { catalog.guide.group($0.key) } : player.lineup
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            HStack(spacing: 16) {
                tab(String(localized: "Toutes les chaînes"), on: !favoritesOnly) { favoritesOnly = false }
                tab("Favoris", on: favoritesOnly) { favoritesOnly = true }
            }
            .focusSection()

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 10) {
                        if channels.isEmpty {
                            Text(favoritesOnly ? "Aucune chaîne favorite." : "Aucune chaîne.")
                                .foregroundStyle(.secondary)
                                .padding(.top, 40)
                        }
                        ForEach(channels) { group in
                            row(group).id(group.id)
                        }
                    }
                    .padding(.vertical, 16)
                    .padding(.horizontal, 12)
                }
                .scrollIndicators(.hidden)
                .scrollClipDisabled()
                .onAppear {
                    proxy.scrollTo(player.channel?.id, anchor: .center)
                    focused = player.channel?.id
                }
            }
            .focusSection()
        }
        .padding(.top, 60)
        .padding(.leading, 70)
        .frame(width: 780)
        .defaultFocus($focused, player.channel?.id)
    }

    private func tab(_ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.callout.weight(.semibold))
                .padding(.horizontal, 26)
                .padding(.vertical, 12)
        }
        .buttonStyle(TVRowStyle(cornerRadius: 30, isCurrent: on))
    }

    private func row(_ group: ChannelGroup) -> some View {
        let now = epg.now(epg.source(group))
        let playing = group.id == player.channel?.id
        return Button {
            if let client = account.client {
                player.watch(group, in: favoritesOnly ? channels : player.lineup, client: client)
            }
            close()
        } label: {
            HStack(spacing: 20) {
                Text(group.number.map(String.init) ?? "")
                    .font(.callout.weight(.bold).monospacedDigit())
                    .opacity(0.7)
                    .frame(width: 56)
                TVChannelLogo(group: group, width: 96, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 10) {
                        Text(group.name).font(.callout.weight(.semibold)).lineLimit(1)
                        if playing { Image(systemName: "waveform").font(.caption).foregroundStyle(FluxTint.blue) }
                    }
                    if let now {
                        Text(now.title).font(.caption).opacity(0.7).lineLimit(1)
                        LiveProgress(programme: now, height: 4).frame(maxWidth: 380)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
        }
        .buttonStyle(TVRowStyle(isCurrent: playing))
        .focused($focused, equals: group.id)
        .task(id: group.id) {
            guard let client = account.client else { return }
            try? await Task.sleep(for: .milliseconds(200))
            guard !Task.isCancelled else { return }
            await epg.load(group, client: client)
        }
    }
}
