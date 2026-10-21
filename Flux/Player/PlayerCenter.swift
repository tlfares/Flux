import AVKit
import SwiftUI

/// Something to play, with what the player shows about it.
struct Playable: Identifiable {
    let id: String
    let url: URL
    let title: String
    var subtitle: String?
    var artwork: String?
    var isLive: Bool
    var resumeAt: Double?
}

/// The one player, with UIKit's own AVPlayerViewController: the system
/// controls, AirPlay and Picture in Picture as iOS draws them.
///
/// Live TV plays inline, on a screen with the guide and the channels to
/// zap through; films and episodes go straight to full screen. Either
/// outlives its screen while in PiP and comes back when PiP is restored.
@MainActor
@Observable
final class PlayerCenter: NSObject {
    private(set) var current: Playable?
    private(set) var isInPiP = false
    /// The channel on the live screen, and which of its streams plays.
    private(set) var liveGroup: ChannelGroup?
    private(set) var liveVariant: ChannelVariant?
    /// Set when no stream of the channel would play.
    private(set) var liveError: String?
    /// Says which stream took over from one that failed.
    private(set) var fallbackNotice: String?
    /// Drives the live screen.
    var showsLive = false
    /// The live screen shows the picture alone.
    private(set) var isLiveFullScreen = false
    var library: Library?

    private(set) var controller: FluxPlayerController?
    private var player: AVPlayer?
    private var progressObserver: Any?
    private var statusObservation: NSKeyValueObservation?
    private var triedVariants: Set<Int> = []
    private var client: XtreamClient?
    private var pendingRestore: ((Bool) -> Void)?

    override init() {
        super.init()
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
    }

    // MARK: Live

    /// Opens the live screen on a channel, in the stream asked for or the
    /// one the settings and past picks point to; `delay` picks a time
    /// shift. Zapping keeps the screen.
    func watch(_ group: ChannelGroup, variant: ChannelVariant? = nil, delay: Int? = nil, client: XtreamClient) {
        if current?.isLive == false { stop() }
        self.client = client
        let preference = QualityPreference(rawValue: UserDefaults.standard.string(forKey: QualityPreference.storageKey) ?? "") ?? .best
        let remembered = library?.qualityChoices[group.id]
        let chosen = variant ?? group.preferred(preference, remembered: remembered, delay: delay)
        // A pick is kept for the channel, unless it's what the setting gives anyway.
        if let variant, variant.delay == group.baseDelay, group.hasChoice {
            let byDefault = group.preferred(preference, remembered: nil).id == variant.id
            library?.rememberQuality(byDefault ? nil : variant.quality.label, for: group)
        }
        library?.played(group)
        showsLive = true
        guard chosen.id != liveVariant?.id || liveError != nil else { liveGroup = group; return }
        triedVariants = []
        fallbackNotice = nil
        start(group, chosen)
    }

    private func start(_ group: ChannelGroup, _ variant: ChannelVariant) {
        guard let client else { return }
        triedVariants.insert(variant.id)
        liveGroup = group
        liveVariant = variant
        liveError = nil
        var details: [String] = []
        if variant.delay > 0 { details.append("Différé +\(variant.delay) h") }
        if group.variants(delay: variant.delay).count > 1 { details.append(variant.quality.label) }
        let subtitle = details.isEmpty ? nil : details.joined(separator: " · ")
        // The free channels' addresses are looked up first: one that can't
        // be found fails over like a stream that won't play.
        resolving?.cancel()
        resolving = Task {
            let url = await client.liveURL(variant.channel)
            guard !Task.isCancelled, liveVariant?.id == variant.id else { return }
            guard let url else { streamFailed(); return }
            load(Playable(id: Library.key(.live, variant.id), url: url, title: group.name,
                          subtitle: subtitle, artwork: group.icon, isLive: true))
        }
    }

    private var resolving: Task<Void, Never>?

    /// A stream that won't play hands over to the next one of the channel,
    /// at the same delay.
    private func streamFailed() {
        guard let group = liveGroup, let delay = liveVariant?.delay else { return }
        if let next = group.variants(delay: delay).first(where: { !triedVariants.contains($0.id) }) {
            fallbackNotice = "\(liveVariant?.quality.label ?? "Ce flux") ne répond pas · \(next.quality.label)"
            start(group, next)
        } else {
            liveError = "Cette chaîne ne répond pas pour le moment."
        }
    }

    /// Starts the channel over, every stream in play again.
    func retry() {
        guard let group = liveGroup, let variant = liveVariant else { return }
        triedVariants = []
        fallbackNotice = nil
        start(group, variant)
    }

    /// The live screen went away: playback ends unless it's in PiP.
    func liveScreenClosed() {
        showsLive = false
        guard !isInPiP else { return }
        stop()
    }

    /// Live full screen: the live screen shows the picture alone, edge to
    /// edge, and a phone turns sideways for it.
    /// Full screen turned the phone sideways itself, from upright.
    private var turnedSideways = false

    func setLiveFullScreen(_ on: Bool) {
        guard on != isLiveFullScreen else { return }
        isLiveFullScreen = on
        guard UIDevice.current.userInterfaceIdiom == .phone,
              let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first(where: { $0.activationState == .foregroundActive }) else { return }
        // Left while the phone is held sideways: stays sideways, the channels
        // beside the picture, rather than forced upright and back again.
        // Only undoes a turn it made itself.
        if !on { guard turnedSideways else { return }; turnedSideways = false }
        if on {
            guard !scene.effectiveGeometry.interfaceOrientation.isLandscape else { return }
            turnedSideways = true
        }
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: on ? .landscapeRight : .portrait)) { _ in }
    }

    /// Paused or playing, from the live screen's own controls.
    private(set) var isLivePaused = false

    func toggleLivePause() {
        guard let player else { return }
        if isLivePaused { player.play() } else { player.pause() }
        isLivePaused.toggle()
    }

    /// The live screen is back on: PiP can hand the video over.
    func liveSurfaceAppeared() {
        pendingRestore?(true)
        pendingRestore = nil
    }

    // MARK: Films and episodes

    func play(_ item: Playable) {
        stop()
        load(item)
        guard let controller else { return }
        controller.modalPresentationStyle = .fullScreen
        if !item.isLive {
            progressObserver = player?.addPeriodicTimeObserver(forInterval: CMTime(seconds: 5, preferredTimescale: 1), queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.saveProgress() }
            }
        }
        present(controller)
    }

    // MARK: Playback

    /// Starts an item, in the player already up when zapping.
    private func load(_ item: Playable) {
        // Can take a moment: kept off the tap.
        Task.detached(priority: .userInitiated) { try? AVAudioSession.sharedInstance().setActive(true) }
        let asset = AVURLAsset(url: item.url, options: [AVURLAssetPreferPreciseDurationAndTimingKey: false])
        let playerItem = AVPlayerItem(asset: asset)
        // Films buffer further ahead; live is left to AVPlayer, which
        // sizes its buffer from the playlist's segments.
        if !item.isLive { playerItem.preferredForwardBufferDuration = 20 }
        playerItem.externalMetadata = metadata(for: item)
        statusObservation = playerItem.observe(\.status) { [weak self] item, _ in
            guard item.status == .failed else { return }
            Task { @MainActor in
                guard let self, self.player?.currentItem === item else { return }
                if self.current?.isLive == true { self.streamFailed() }
            }
        }

        if let player {
            player.replaceCurrentItem(with: playerItem)
        } else {
            let player = AVPlayer(playerItem: playerItem)
            // Left on for live too: off, the first stall freezes the picture
            // on its last frame and playback never picks up again.
            player.automaticallyWaitsToMinimizeStalling = true
            player.allowsExternalPlayback = true
            self.player = player
        }
        if let resume = item.resumeAt {
            player?.seek(to: CMTime(seconds: resume, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .positiveInfinity)
        }

        if controller == nil {
            let controller = FluxPlayerController()
            controller.onClose = { [weak self] in self?.modalClosed() }
            controller.delegate = self
            controller.allowsPictureInPicturePlayback = true
            controller.canStartPictureInPictureAutomaticallyFromInline = true
            controller.updatesNowPlayingInfoCenter = true
            controller.entersFullScreenWhenPlaybackBegins = false
            controller.exitsFullScreenWhenPlaybackEnds = true
            self.controller = controller
        }
        controller?.player = player
        controller?.requiresLinearPlayback = false
        // Live: the live screen draws its own few buttons over the picture.
        controller?.showsPlaybackControls = !item.isLive
        isLivePaused = false
        current = item
        player?.play()
    }

    /// Ends playback: closed from the player, or PiP closed.
    func stop() {
        resolving?.cancel()
        saveProgress()
        if let progressObserver { player?.removeTimeObserver(progressObserver) }
        progressObserver = nil
        statusObservation = nil
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        controller?.player = nil
        // Presented itself, not a child of a screen that is.
        if let controller, controller.parent == nil, controller.presentingViewController != nil { controller.dismiss(animated: false) }
        controller = nil
        player = nil
        current = nil
        liveGroup = nil
        liveVariant = nil
        liveError = nil
        fallbackNotice = nil
        showsLive = false
        setLiveFullScreen(false)
        isInPiP = false
        pendingRestore?(false)
        pendingRestore = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func saveProgress() {
        guard let current, !current.isLive, let item = player?.currentItem else { return }
        library?.saveProgress(current.id, position: item.currentTime().seconds, duration: item.duration.seconds)
    }

    private func metadata(for item: Playable) -> [AVMetadataItem] {
        func entry(_ id: AVMetadataIdentifier, _ value: String) -> AVMetadataItem {
            let m = AVMutableMetadataItem()
            m.identifier = id
            m.value = value as NSString
            m.extendedLanguageTag = "und"
            return m
        }
        var items = [entry(.commonIdentifierTitle, item.title)]
        if let subtitle = item.subtitle { items.append(entry(.iTunesMetadataTrackSubTitle, subtitle)) }
        return items
    }

    private func present(_ controller: AVPlayerViewController, completion: (() -> Void)? = nil) {
        guard let top = Self.topViewController() else { return }
        top.present(controller, animated: true, completion: completion)
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController, !presented.isBeingDismissed { top = presented }
        return top
    }

    /// A film's full-screen player was closed.
    private func modalClosed() {
        // Live goes full screen from its own screen and comes back to it;
        // in PiP the screen goes but the video stays.
        guard current?.isLive == false, !isInPiP else { return }
        stop()
    }
}

extension PlayerCenter: @MainActor AVPlayerViewControllerDelegate {
    func playerViewControllerWillStartPictureInPicture(_ playerViewController: AVPlayerViewController) {
        isInPiP = true
    }

    func playerViewControllerDidStopPictureInPicture(_ playerViewController: AVPlayerViewController) {
        isInPiP = false
        guard playerViewController === controller else { return }
        // Closed from the PiP window rather than brought back.
        let onScreen = current?.isLive == true ? showsLive : playerViewController.presentingViewController != nil
        if !onScreen { stop() }
    }

    func playerViewController(_ playerViewController: AVPlayerViewController, restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        if current?.isLive == true {
            guard !showsLive else { completionHandler(true); return }
            // The live screen calls back once the video has a place in it.
            pendingRestore = completionHandler
            showsLive = true
            return
        }
        guard playerViewController.presentingViewController == nil else { completionHandler(true); return }
        present(playerViewController) { completionHandler(true) }
    }
}

/// Tells when its screen is closed (the ✕, or swiped down): the
/// delegate has no callback for a modal player's own dismissal.
final class FluxPlayerController: AVPlayerViewController {
    var onClose: (() -> Void)?

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || presentingViewController == nil && parent == nil { onClose?() }
    }
}
