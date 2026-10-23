import AVKit
import SwiftUI

/// Live TV: the picture, what's on, and the channels to zap to. Upright,
/// the picture sits on top, edge to edge, with the guide and the channels
/// under it; sideways, the channels run down the left and the picture and
/// guide fill the right. The chevron on the picture closes the screen, the
/// button opposite it goes full screen.
struct LiveWatchView: View {
    @Environment(PlayerCenter.self) private var player
    @Environment(Catalog.self) private var catalog
    @Environment(Library.self) private var library
    @Environment(AccountStore.self) private var account
    @Environment(\.dismiss) private var dismiss
    @AppStorage("live.category") private var selection = CategoryBar.all
    @AppStorage(FavoritesStyle.storageKey) private var style = FavoritesStyle.separate
    @Environment(\.verticalSizeClass) private var verticalSize
    @State private var detailsHeight: CGFloat = 0
    @State private var showsControls = true
    @State private var hideTask: Task<Void, Never>?

    var body: some View {
        GeometryReader { geo in
            let wide = geo.size.width > geo.size.height && geo.size.width > 560
            // Turning a phone on its side goes full screen; leaving it there
            // shows the channels beside the picture.
            if player.isLiveFullScreen {
                LiveFullScreen()
            } else if wide {
                HStack(spacing: 0) {
                    VStack(spacing: 0) {
                        categories
                        channels
                    }
                    .frame(width: min(400, geo.size.width * 0.4))
                    VStack(spacing: 0) {
                        // As big as the column allows, leaving the guide a third.
                        video
                            .frame(maxHeight: geo.size.height * 0.62)
                        ScrollView {
                            LiveDetails(expanded: true)
                                .padding(.top, 14)
                                .padding(.bottom, 16)
                        }
                        .scrollIndicators(.hidden)
                    }
                    .padding(.top, 8)
                    .padding(.trailing, 16)
                }
            } else {
                VStack(spacing: 0) {
                    video
                        // Full width whatever opens under it.
                        .frame(height: geo.size.width * 9 / 16)
                        // Black up to the top edge, the status bar over it.
                        .background(Color.black.ignoresSafeArea(edges: .top))
                    // As tall as the guide, up to a cap past which it scrolls.
                    // Its height follows the guide's, on the same spring, so
                    // the channels move with it both ways.
                    ScrollView {
                        details.onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
                            withAnimation(Motion.smooth) { detailsHeight = height }
                        }
                    }
                    .scrollIndicators(.hidden)
                    .scrollBounceBehavior(.basedOnSize)
                    .frame(height: min(detailsHeight, geo.size.height * 0.45))
                    categories
                    channels
                        // Rows slide out under the chips rather than being cut.
                        .mask {
                            VStack(spacing: 0) {
                                LinearGradient(colors: [.clear, .black], startPoint: .top, endPoint: .bottom).frame(height: 10)
                                Color.black
                            }
                        }
                        // Down to the bottom edge, under the home indicator.
                        .ignoresSafeArea(edges: .bottom)
                }
            }
        }
        .background { Backdrop(icon: player.liveGroup?.icon) }
        .onChange(of: verticalSize) { _, size in
            // Sideways: full screen. Turned back upright: out of it.
            if size == .compact, !player.isLiveFullScreen, player.liveError == nil { player.setLiveFullScreen(true) }
            if size == .regular, player.isLiveFullScreen { player.setLiveFullScreen(false) }
        }
    }

    private var details: some View {
        LiveDetails(expanded: false)
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 4)
    }

    private var categories: some View {
        CategoryBar(categories: catalog.live.categories, kind: .live, selection: $selection, showsRecent: true, showsFavorites: style == .separate)
            .padding(.horizontal, -4)
    }

    private var video: some View {
        ZStack {
            Color.black
            // Handed to full screen while it's there; back here after.
            PlayerSurface(controller: player.controller) { player.liveSurfaceAppeared() }
            LiveErrorOverlay()
        }
        .aspectRatio(16 / 9, contentMode: .fit)
        .contentShape(.rect)
        .onTapGesture { show(!showsControls) }
        .overlay {
            // A tap brings them up for a few seconds, as in full screen.
            if showsControls {
                ZStack {
                    VideoButton(systemImage: "chevron.down", label: String(localized: "Fermer")) { dismiss() }
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    VideoButton(systemImage: "arrow.up.left.and.arrow.down.right", label: String(localized: "Plein écran")) { player.setLiveFullScreen(true) }
                        .disabled(player.liveError != nil)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    SubtitlesButton(size: 40)
                        .padding(10)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    if player.liveError == nil {
                        Button {
                            player.toggleLivePause()
                            show(true)
                        } label: {
                            Image(systemName: player.isLivePaused ? "play.fill" : "pause.fill")
                                .font(.system(size: 22, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 56, height: 56)
                                .contentShape(.circle)
                        }
                        .buttonStyle(.plain)
                        .glassEffect(.regular.interactive(), in: .circle)
                        .accessibilityLabel(player.isLivePaused ? "Lecture" : "Pause")
                    }
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: showsControls)
        .onAppear { show(true) }
        .frame(maxWidth: .infinity)
    }

    /// Up for three seconds, unless paused.
    private func show(_ visible: Bool) {
        showsControls = visible
        hideTask?.cancel()
        guard visible else { return }
        hideTask = Task {
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled, !player.isLivePaused else { return }
            showsControls = false
        }
    }

    /// In a container of its own, the chips beside it rather than in it:
    /// swiping them never scrolls the list.
    private var channels: some View {
        let groups = LiveListing(selection: selection, catalog: catalog, library: library, style: style).groups
        return ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 8) {
                    if groups.isEmpty {
                        LiveEmptySelection(selection: selection).padding(.top, 40)
                    }
                    rows(groups.top)
                    if !groups.top.isEmpty, !groups.rest.isEmpty {
                        Divider().padding(.vertical, 4).padding(.horizontal, 20)
                    }
                    rows(groups.rest)
                }
                .padding(.top, 4)
                .padding(.bottom, 44)
            }
            .scrollIndicators(.hidden)
            .onAppear {
                if let id = player.liveGroup?.id { proxy.scrollTo(id, anchor: .center) }
            }
        }
    }

    private func rows(_ groups: [ChannelGroup]) -> some View {
        ForEach(groups) { group in
            ChannelRow(group: group, isPlaying: player.liveGroup?.id == group.id) { variant in
                if let client = account.client { player.watch(group, variant: variant, client: client) }
            }
            .padding(.horizontal, 16)
            .id(group.id)
        }
    }
}

/// The picture alone, edge to edge. A tap brings the controls up for a few
/// seconds: leave full screen, pause, AirPlay.
private struct LiveFullScreen: View {
    @Environment(PlayerCenter.self) private var player
    @Environment(EPG.self) private var epg
    @State private var showsControls = true
    @State private var hideTask: Task<Void, Never>?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            PlayerSurface(controller: player.controller) { player.liveSurfaceAppeared() }
                .ignoresSafeArea()
            LiveErrorOverlay()
            if showsControls { controls.transition(.opacity) }
        }
        .contentShape(.rect)
        .onTapGesture { show(!showsControls) }
        .onAppear { show(true) }
        .statusBarHidden(!showsControls)
        .persistentSystemOverlays(.hidden)
        .animation(.easeInOut(duration: 0.25), value: showsControls)
    }

    private var controls: some View {
        ZStack {
            // Darker at the edges, where the buttons and the title sit.
            LinearGradient(colors: [.black.opacity(0.55), .clear, .clear, .black.opacity(0.45)], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
                .allowsHitTesting(false)
            VStack {
                HStack(alignment: .top, spacing: 12) {
                    VideoButton(systemImage: "chevron.down", label: String(localized: "Quitter le plein écran")) { player.setLiveFullScreen(false) }
                        .padding(-10)
                    if let group = player.liveGroup {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(group.name).font(.headline)
                            if let now = epg.upcoming(epg.source(group)).first, now.isOn, (player.liveVariant?.delay ?? 0) == 0 {
                                Text(now.title).font(.subheadline).foregroundStyle(.white.opacity(0.75)).lineLimit(1)
                            }
                        }
                        .foregroundStyle(.white)
                        .padding(.top, 2)
                    }
                    Spacer()
                    SubtitlesButton(size: 40)
                    AirPlayButton()
                        .frame(width: 40, height: 40)
                        .glassEffect(.regular.interactive(), in: .circle)
                }
                Spacer()
                HStack {
                    Spacer()
                    VideoButton(systemImage: "arrow.down.right.and.arrow.up.left", label: String(localized: "Quitter le plein écran")) { player.setLiveFullScreen(false) }
                        .padding(-10)
                }
            }
            .padding(20)

            Button {
                player.toggleLivePause()
                show(true)
            } label: {
                Image(systemName: player.isLivePaused ? "play.fill" : "pause.fill")
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 68, height: 68)
                    .contentShape(.circle)
            }
            .buttonStyle(.plain)
            .glassEffect(.regular.interactive(), in: .circle)
            .accessibilityLabel(player.isLivePaused ? "Lecture" : "Pause")
        }
    }

    /// Up for three seconds, unless paused.
    private func show(_ visible: Bool) {
        showsControls = visible
        hideTask?.cancel()
        guard visible else { return }
        hideTask = Task {
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled, !player.isLivePaused else { return }
            showsControls = false
        }
    }
}

/// When no stream of the channel would play.
private struct LiveErrorOverlay: View {
    @Environment(PlayerCenter.self) private var player

    var body: some View {
        if let error = player.liveError {
            ZStack {
                Color.black
                VStack(spacing: 12) {
                    Image(systemName: "antenna.radiowaves.left.and.right.slash").font(.title)
                    Text(error).font(.subheadline)
                    Button("Réessayer") { player.retry() }
                        .font(.subheadline.weight(.semibold))
                        .buttonStyle(.glass)
                }
                .foregroundStyle(.white.opacity(0.85))
                .multilineTextAlignment(.center)
                .padding()
            }
        }
    }
}

/// The system's AirPlay picker.
private struct AirPlayButton: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView {
        let view = AVRoutePickerView()
        view.tintColor = .white
        view.prioritizesVideoDevices = true
        return view
    }

    func updateUIView(_ view: AVRoutePickerView, context: Context) {}
}

/// The stream's subtitles, off or one of them: there only when it has some.
private struct SubtitlesButton: View {
    let size: CGFloat
    @Environment(PlayerCenter.self) private var player

    var body: some View {
        let subtitles = player.subtitles
        if !subtitles.options.isEmpty {
            Menu {
                Picker("Sous-titres", selection: Binding(get: { subtitles.selected }, set: { subtitles.choose($0) })) {
                    Text("Désactivés").tag(AVMediaSelectionOption?.none)
                    ForEach(subtitles.options, id: \.self) { option in
                        Text(Subtitles.name(option)).tag(Optional(option))
                    }
                }
            } label: {
                Image(systemName: subtitles.selected == nil ? "captions.bubble" : "captions.bubble.fill")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: size, height: size)
                    .contentShape(.circle)
            }
            .menuStyle(.button)
            .buttonStyle(.plain)
            .glassEffect(.regular.interactive(), in: .circle)
            .accessibilityLabel("Sous-titres")
        }
    }
}

/// A round glass button sitting on the picture, in a corner.
private struct VideoButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .contentShape(.circle)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .circle)
        .padding(10)
        .accessibilityLabel(label)
    }
}

/// The channel's logo, blown up and blurred behind the glass: the screen
/// takes the colours of what's on.
private struct Backdrop: View {
    let icon: String?

    var body: some View {
        ZStack {
            Color.screenBackground
            RemoteImage(url: icon, size: 96, contentMode: .fit) { Color.clear }
                .frame(width: 260, height: 260)
                .blur(radius: 90)
                .opacity(0.55)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .offset(y: 40)
                .animation(Motion.smooth, value: icon)
        }
        .ignoresSafeArea()
    }
}

/// What's on the channel playing: its name, the programme now and next,
/// the qualities and time shifts it comes in.
private struct LiveDetails: View {
    /// The longer schedule, when there's room beside the picture.
    let expanded: Bool

    @Environment(PlayerCenter.self) private var player
    @Environment(EPG.self) private var epg
    @Environment(AccountStore.self) private var account
    @State private var showsSummary = false

    var body: some View {
        if let group = player.liveGroup {
            let id = epg.source(group)
            let schedule = epg.upcoming(id)
            let delay = player.liveVariant?.delay ?? 0
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    ChannelLogo(group: group, width: 52, height: 34)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(group.name).font(.title3.bold()).lineLimit(1)
                        HStack(spacing: 5) {
                            if delay > 0 {
                                Image(systemName: "clock.arrow.circlepath").foregroundStyle(.tint)
                                Text("Différé de \(delay) h")
                            } else {
                                Circle().fill(.red).frame(width: 7, height: 7)
                                Text("En direct")
                            }
                            if let variant = player.liveVariant, group.variants(delay: delay).count > 1 {
                                Text("· \(variant.quality.label)")
                            }
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    FavoriteButton(favorite: .live(group))
                        .labelStyle(.iconOnly)
                        .font(.body.weight(.semibold))
                        .frame(width: 40, height: 40)
                        .buttonStyle(.plain)
                        .glassEffect(.regular.interactive(), in: .circle)
                }

                if let notice = player.fallbackNotice {
                    Label(notice, systemImage: "arrow.triangle.2.circlepath")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                choices(group, delay: delay)

                if delay > 0 {
                    Text("Les programmes passent \(delay) h après les horaires du guide.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else if let now = schedule.first, now.isOn {
                    VStack(alignment: .leading, spacing: 12) {
                        nowCard(now)
                        let later = schedule.dropFirst().prefix(expanded ? 6 : 1)
                        if !later.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(Array(later), id: \.self) { p in
                                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                                        Text(p.start.formatted(date: .omitted, time: .shortened))
                                            .font(.footnote.monospacedDigit().weight(.semibold))
                                            .foregroundStyle(.tint)
                                            .frame(width: 48, alignment: .leading)
                                        Text(p.title).font(.subheadline).lineLimit(expanded ? 2 : 1)
                                    }
                                }
                            }
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .glassEffect(.regular, in: .rect(cornerRadius: 22, style: .continuous))
                } else if epg.isEmpty(id) {
                    Text("Pas de guide des programmes pour cette chaîne.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .task(id: group.id) {
                showsSummary = false
                guard let client = account.client else { return }
                await epg.load(group, client: client, limit: 8)
            }
        }
    }

    /// What's on now; its summary folds out under a tap.
    private func nowCard(_ now: Programme) -> some View {
        Button {
            guard now.summary != nil else { return }
            withAnimation(Motion.smooth) { showsSummary.toggle() }
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(now.title)
                        .font(.headline)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if now.summary != nil {
                        Image(systemName: "chevron.down")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .rotationEffect(.degrees(showsSummary ? 180 : 0))
                    }
                }
                HStack(spacing: 10) {
                    Text(now.timeRange)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                    ProgressView(timerInterval: now.start...now.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                        .progressViewStyle(.linear)
                        .tint(.accentColor)
                }
                if let summary = now.summary, showsSummary {
                    Text(summary)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityHint(now.summary == nil ? "" : (showsSummary ? "Masque le résumé" : "Affiche le résumé"))
    }

    /// Time shifts, then the qualities at the one playing.
    @ViewBuilder
    private func choices(_ group: ChannelGroup, delay: Int) -> some View {
        let delays = group.delays
        let qualities = group.variants(delay: delay)
        if delays.count > 1 || qualities.count > 1 {
            ScrollView(.horizontal) {
                HStack(spacing: 6) {
                    if delays.count > 1 {
                        ForEach(delays, id: \.self) { d in
                            ChoiceChip(title: d == 0 ? "Direct" : "+\(d) h", systemImage: d == 0 ? "dot.radiowaves.left.and.right" : nil,
                                       selected: d == delay) {
                                guard let client = account.client else { return }
                                player.watch(group, delay: d, client: client)
                            }
                        }
                        if qualities.count > 1 {
                            Divider().frame(height: 20).padding(.horizontal, 4)
                        }
                    }
                    if qualities.count > 1 {
                        ForEach(qualities) { variant in
                            ChoiceChip(title: variant.quality.label, selected: variant.id == player.liveVariant?.id) {
                                guard let client = account.client else { return }
                                player.watch(group, variant: variant, client: client)
                            }
                        }
                    }
                }
                .padding(.vertical, 2)
            }
            .scrollIndicators(.hidden)
            .scrollBounceBehavior(.basedOnSize)
            .scrollClipDisabled()
            .sensoryFeedback(.selection, trigger: player.liveVariant?.id)
        }
    }
}

/// A small pick among a few: the gradient when chosen.
struct ChoiceChip: View {
    let title: String
    var systemImage: String?
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button {
            guard !selected else { return }
            action()
        } label: {
            HStack(spacing: 4) {
                if let systemImage { Image(systemName: systemImage).font(.caption) }
                Text(title)
            }
            .font(.footnote.weight(selected ? .semibold : .medium))
            .foregroundStyle(selected ? AnyShapeStyle(.white) : AnyShapeStyle(.primary))
            .padding(.horizontal, 12)
            .frame(height: 30)
        }
        .buttonStyle(PressableStyle())
        .background(FluxTint.horizontal.opacity(selected ? 1 : 0), in: .capsule)
        .glassEffect(selected ? .clear : .regular, in: .capsule)
    }
}

/// Shrinks a little under the finger: glass that stays put in a strip
/// that scrolls sideways, where interactive glass would follow the drag.
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(.capsule)
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

/// Hosts the player's own view controller inline. The controller belongs
/// to PlayerCenter and moves from one host to the next (upright, sideways)
/// without the video stopping.
struct PlayerSurface: UIViewControllerRepresentable {
    let controller: AVPlayerViewController?
    let appeared: () -> Void

    func makeUIViewController(context: Context) -> PlayerHostController {
        let host = PlayerHostController()
        host.appeared = appeared
        host.embed(controller)
        return host
    }

    func updateUIViewController(_ host: PlayerHostController, context: Context) {
        host.appeared = appeared
        host.embed(controller)
    }

    static func dismantleUIViewController(_ host: PlayerHostController, coordinator: ()) {
        host.release()
    }
}

final class PlayerHostController: UIViewController {
    var appeared: (() -> Void)?
    private weak var embedded: AVPlayerViewController?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        appeared?()
    }

    func embed(_ controller: AVPlayerViewController?) {
        guard controller !== embedded else { return }
        release()
        // Full screen has it, presented on its own: it comes back once closed.
        guard let controller, !(controller.parent == nil && controller.presentingViewController != nil) else { return }
        if controller.parent != nil {
            controller.willMove(toParent: nil)
            controller.view.removeFromSuperview()
            controller.removeFromParent()
        }
        addChild(controller)
        controller.view.frame = view.bounds
        controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(controller.view)
        controller.didMove(toParent: self)
        embedded = controller
    }

    /// Lets go of the player, unless another host has taken it already.
    func release() {
        guard let embedded, embedded.parent === self else { embedded = nil; return }
        embedded.willMove(toParent: nil)
        embedded.view.removeFromSuperview()
        embedded.removeFromParent()
        self.embedded = nil
    }
}
