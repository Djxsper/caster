import SwiftUI
import Observation

/// App-wide services. Injected with `.environment(_:)` and read with
/// `@Environment(AppEnvironment.self)` — `@EnvironmentObject` only works with
/// `ObservableObject`, not with `@Observable` types.
///
/// Seat colours live on `Theme` rather than here, so light and dark palettes
/// have a single owner.
@Observable
@MainActor
final class AppEnvironment {
    let hapticEngine = HapticEngine.shared
    let soundEngine = SoundEngine.shared

    /// Mirrored onto the sound engine so the toggle has one home.
    var isMuted = false {
        didSet { soundEngine.isMuted = isMuted }
    }

    /// The two cues that always fire together: a tap you feel and a tap you
    /// hear. Kept here so no game has to remember to do both.
    func cue(_ feedback: FeedbackType, _ tone: Tone) {
        hapticEngine.playFeedback(type: feedback)
        soundEngine.play(tone)
    }
}
