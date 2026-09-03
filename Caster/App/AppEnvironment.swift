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
    private static let mutedKey = "caster.sound.muted"

    let hapticEngine = HapticEngine.shared
    let soundEngine = SoundEngine.shared

    /// No-op on any build without an ad SDK linked, which is every build from
    /// the public repository. See `AdPresenterFactory`.
    let ads: AdPresenter

    /// Counts the things the interstitial rules need counted. Never leaves the
    /// device and has no SDK behind it.
    let pacing: AdPacingStore

    #if DEBUG
    /// The very same object as `ads`, downcast so the debug overlay can observe
    /// it. Not a second instance — a second one would count its own ads and
    /// report numbers that had nothing to do with what you saw.
    var fakeAds: FakeAdPresenter? { ads as? FakeAdPresenter }
    #endif

    private let defaults: UserDefaults

    /// Mirrored onto the sound engine so the toggle has one home, and written
    /// through to disk so the settings screen does not forget it on relaunch.
    var isMuted = false {
        didSet {
            soundEngine.isMuted = isMuted
            defaults.set(isMuted, forKey: Self.mutedKey)
        }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.pacing = AdPacingStore(defaults: defaults)
        self.ads = AdPresenterFactory.make()
        let muted = defaults.bool(forKey: Self.mutedKey)
        self.isMuted = muted
        soundEngine.isMuted = muted
    }

    /// The two cues that always fire together: a tap you feel and a tap you
    /// hear. Kept here so no game has to remember to do both.
    ///
    /// And, for the same reason, so no game has to remember to say a round
    /// finished. `.reveal` and `.boom` are exactly the tones that mean "this
    /// round is over" — `.reveal` is documented as such on `Tone` — and between
    /// them all six games fire one, once, at the moment they resolve. Counting
    /// here rather than in six view files means a new game gets it for free and
    /// cannot forget it.
    func cue(_ feedback: FeedbackType, _ tone: Tone) {
        hapticEngine.playFeedback(type: feedback)
        soundEngine.play(tone)

        if tone == .reveal || tone == .boom {
            pacing.recordRoundCompleted()
        }
    }
}
