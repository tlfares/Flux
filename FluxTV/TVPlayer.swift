import AVKit
import SwiftUI

/// A film or an episode, for the system's full-screen player.
struct TVVideo: Identifiable {
    let id: String
    let url: URL
    let title: String
    var subtitle: String?
    var resumeAt: Double?
}

/// The one player of the Apple TV app. Live TV plays on a screen of its
/// own, drawn by Flux (the zapping banner, the channel list); films and
/// episodes play in the system's player, with tvOS's own controls.
@MainActor
@Observable
final class TVPlayer {
    /// The channel on screen, and which of its streams plays.
    private(set) var channel: ChannelGroup?
    private(set) var variant: ChannelVariant?
    /// The channels zapped through: the list the channel was picked from.
    private(set) var lineup: [ChannelGroup] = []
    /// Set when no stream of the channel would play.
    private(set) var error: String?
    private(set) var isPaused = false
    private(set) var isBuffering = false
    var showsLive = false
    var video: TVVideo?
    var library: Library?

    let player: AVPlayer = {
        let player = AVPlayer()
        // Left on for live too: off, the first stall freezes the picture.
        player.automaticallyWaitsToMinimizeStalling = true
        return player
    }()

    private var client: XtreamClient?
    private var tried: Set<Int> = []
    private var statusObservation: NSKeyValueObservation?
    private var waitingObservation: NSKeyValueObservation?
    private var progressObserver: Any?
    private var pendingZap: Task<Void, Never>?

    init() {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        waitingObservation = player.observe(\.timeControlStatus) { [weak self] player, _ in
            let waiting = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
            Task { @MainActor in self?.isBuffering = waiting }
        }
    }

    // MARK: Live

    /// Opens the live screen on a channel; `lineup` is what up and down
    /// zap through, the list it was picked from.
    func watch(_ group: ChannelGroup, in lineup: [ChannelGroup], client: XtreamClient) {
        if video != nil { closeVideo() }
        self.client = client
        if !lineup.isEmpty { self.lineup = lineup }
        if !self.lineup.contains(where: { $0.id == group.id }) { self.lineup.insert(group, at: 0) }
        pendingZap?.cancel()
        showsLive = true
        start(group)
    }

    /// The next channel up the list (`step` 1) or down it (-1), shown at
    /// once and tuned once the presses stop: zapping through five channels
    /// opens one stream, not five.
    func zap(_ step: Int) {
        guard let current = channel, let i = lineup.firstIndex(where: { $0.id == current.id }), lineup.count > 1 else { return }
        let target = lineup[(i + step + lineup.count) % lineup.count]
        channel = target
        error = nil
        pendingZap?.cancel()
        pendingZap = Task {
            try? await Task.sleep(for: .milliseconds(550))
            guard !Task.isCancelled else { return }
            start(target)
        }
    }

    private func start(_ group: ChannelGroup) {
        let preference = QualityPreference(rawValue: UserDefaults.standard.string(forKey: QualityPreference.storageKey) ?? "") ?? .best
        let chosen = group.preferred(preference, remembered: library?.qualityChoices[group.id])
        library?.played(group)
        tried = []
        tune(group, chosen)
    }

    private func tune(_ group: ChannelGroup, _ variant: ChannelVariant) {
        guard let client else { return }
        tried.insert(variant.id)
        channel = group
        self.variant = variant
        error = nil
        // The free channels' addresses are looked up first: one that can't
        // be found fails over like a stream that won't play.
        resolving?.cancel()
        resolving = Task {
            let url = await client.liveURL(variant.channel)
            guard !Task.isCancelled, self.variant?.id == variant.id else { return }
            guard let url else { streamFailed(); return }
            load(url, title: group.name, subtitle: nil, resumeAt: nil, isLive: true)
        }
    }

    private var resolving: Task<Void, Never>?

    /// A stream that won't play hands over to the next one of the channel.
    private func streamFailed() {
        guard let group = channel, let delay = variant?.delay else { return }
        if let next = group.variants(delay: delay).first(where: { !tried.contains($0.id) }) {
            tune(group, next)
        } else {
            error = "Cette chaîne ne répond pas pour le moment."
        }
    }

    func retry() {
        guard let channel else { return }
        start(channel)
    }

    func togglePause() {
        if isPaused { player.play() } else { player.pause() }
        isPaused.toggle()
    }

    func closeLive() {
        pendingZap?.cancel()
        showsLive = false
        stop()
        channel = nil
        variant = nil
        error = nil
    }

    // MARK: Films and episodes

    func play(_ video: TVVideo) {
        if showsLive { closeLive() }
        load(video.url, title: video.title, subtitle: video.subtitle, resumeAt: video.resumeAt, isLive: false)
        progressObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 5, preferredTimescale: 1), queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.saveProgress() }
        }
        self.video = video
    }

    func play(_ movie: Movie, client: XtreamClient, fromStart: Bool = false) {
        guard let url = client.movieURL(movie) else { return }
        let key = Library.key(.movie, movie.id)
        play(TVVideo(id: key, url: url, title: DisplayName.clean(movie.name), resumeAt: fromStart ? nil : library?.resumePosition(key)))
    }

    func play(_ episode: Episode, of series: Series, client: XtreamClient, fromStart: Bool = false) {
        guard let url = client.episodeURL(episode) else { return }
        let key = Library.key(.series, episode.id)
        play(TVVideo(id: key, url: url, title: episode.title, subtitle: "\(DisplayName.clean(series.name)) · S\(episode.season) E\(episode.number)",
                     resumeAt: fromStart ? nil : library?.resumePosition(key)))
    }

    /// The system player went away.
    func closeVideo() {
        saveProgress()
        stop()
        video = nil
    }

    // MARK: Playback

    private func load(_ url: URL, title: String, subtitle: String?, resumeAt: Double?, isLive: Bool) {
        if let progressObserver { player.removeTimeObserver(progressObserver) }
        progressObserver = nil
        let item = AVPlayerItem(asset: AVURLAsset(url: url))
        if !isLive { item.preferredForwardBufferDuration = 20 }
        item.externalMetadata = metadata(title: title, subtitle: subtitle)
        statusObservation = item.observe(\.status) { [weak self] item, _ in
            guard item.status == .failed else { return }
            Task { @MainActor in
                guard let self, self.player.currentItem === item, self.channel != nil else { return }
                self.streamFailed()
            }
        }
        player.replaceCurrentItem(with: item)
        if let resumeAt {
            player.seek(to: CMTime(seconds: resumeAt, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .positiveInfinity)
        }
        isPaused = false
        Task.detached(priority: .userInitiated) { try? AVAudioSession.sharedInstance().setActive(true) }
        player.play()
    }

    private func stop() {
        resolving?.cancel()
        if let progressObserver { player.removeTimeObserver(progressObserver) }
        progressObserver = nil
        statusObservation = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
        isPaused = false
    }

    private func saveProgress() {
        guard let video, let item = player.currentItem else { return }
        library?.saveProgress(video.id, position: item.currentTime().seconds, duration: item.duration.seconds)
    }

    private func metadata(title: String, subtitle: String?) -> [AVMetadataItem] {
        func entry(_ id: AVMetadataIdentifier, _ value: String) -> AVMetadataItem {
            let m = AVMutableMetadataItem()
            m.identifier = id
            m.value = value as NSString
            m.extendedLanguageTag = "und"
            return m
        }
        var items = [entry(.commonIdentifierTitle, title)]
        if let subtitle { items.append(entry(.iTunesMetadataTrackSubTitle, subtitle)) }
        return items
    }
}

/// The picture alone, for the live screen, which draws its own controls.
struct VideoSurface: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        view.playerLayer.videoGravity = .resizeAspect
        view.playerLayer.player = player
        return view
    }

    func updateUIView(_ view: PlayerLayerView, context: Context) {
        if view.playerLayer.player !== player { view.playerLayer.player = player }
    }

    final class PlayerLayerView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }
}

/// Films and episodes: tvOS's own player, its transport bar and info panel.
struct SystemPlayer: UIViewControllerRepresentable {
    let player: AVPlayer

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        return controller
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {}
}
