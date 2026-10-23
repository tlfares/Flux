import AVFoundation

/// A stream's subtitles, off unless asked for: some free channels mark
/// theirs to show by default, and AVPlayer then shows them with no way
/// to turn them off from the live screens.
@MainActor
@Observable
final class Subtitles {
    /// The current stream's subtitle tracks, none when it has none.
    private(set) var options: [AVMediaSelectionOption] = []
    /// The one showing; nil when off.
    private(set) var selected: AVMediaSelectionOption?

    private weak var item: AVPlayerItem?
    private var group: AVMediaSelectionGroup?
    private var readiness: NSKeyValueObservation?

    /// Whether the last choice was to show them, kept for every stream.
    private static let storageKey = "subtitles.on"

    /// Reads a new stream's tracks, and shows one only if they were on.
    func load(_ item: AVPlayerItem) {
        self.item = item
        readiness = nil
        options = []
        selected = nil
        group = nil
        Task {
            guard let group = try? await item.asset.loadMediaSelectionGroup(for: .legible), self.item === item else { return }
            // Forced subtitles only translate a few lines: not a choice of their own.
            let options = group.options.filter { !$0.hasMediaCharacteristic(.containsOnlyForcedSubtitles) }
            self.group = group
            self.options = options
            let on = UserDefaults.standard.bool(forKey: Self.storageKey)
            let preferred = AVMediaSelectionGroup.mediaSelectionOptions(from: options, filteredAndSortedAccordingToPreferredLanguages: Locale.preferredLanguages).first
            apply(on ? preferred ?? options.first : nil)
            // Picked again once it plays, and a moment after: until then the
            // item settles on the track the playlist marks as the default.
            readiness = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
                guard item.status == .readyToPlay else { return }
                Task { @MainActor in
                    guard let self, self.item === item else { return }
                    self.readiness = nil
                    // At once, then 0.5 s, 1.5 s and 4 s in.
                    for wait in [0, 0.5, 1, 2.5] {
                        try? await Task.sleep(for: .seconds(wait))
                        guard self.item === item else { return }
                        self.apply(self.selected)
                    }
                }
            }
        }
    }

    /// Shows a track, or none; remembered for the next streams.
    func choose(_ option: AVMediaSelectionOption?) {
        apply(option)
        UserDefaults.standard.set(option != nil, forKey: Self.storageKey)
    }

    private func apply(_ option: AVMediaSelectionOption?) {
        guard let item, let group else { return }
        item.select(option ?? (group.allowsEmptySelection ? nil : group.defaultOption), in: group)
        selected = option
    }

    func clear() {
        readiness = nil
        item = nil
        group = nil
        options = []
        selected = nil
    }

    /// A track's name as the device's language says it: "Français", "English (SDH)".
    static func name(_ option: AVMediaSelectionOption) -> String {
        option.displayName(with: Locale.current)
    }
}
