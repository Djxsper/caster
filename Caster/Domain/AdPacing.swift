import Foundation
import Observation

/// Everything the interstitial decision is allowed to look at. Plain `Codable`
/// data with no clock and no storage of its own, so the rules below can be
/// tested by handing them a state and a date rather than by waiting eight
/// minutes and watching a screen.
struct AdPacingState: Codable, Equatable {
    var launchCount = 0
    var roundsCompleted = 0
    var lastInterstitialAt: Date?

    /// Reset on every cold start, so they never persist.
    var interstitialsThisSession = 0
    var sessionStartedAt = Date()
}

/// When an interstitial may fire, and nothing else.
///
/// There is exactly one placement in the whole app — coming back from a game to
/// the mode list — and every clause here must pass. Written as a pure function
/// rather than as conditions scattered through the views because this is the
/// part that decides whether Caster feels like a party game or like an ad
/// delivery mechanism, and it should be readable in one screen and provable in
/// a test.
///
/// The numbers live in `shared/monetization/offering.json`; Android reads the
/// same file.
enum AdPacing {
    /// The first two sessions never show one. A first impression is worth more
    /// than an impression.
    static let minimumLaunches = 3
    /// Somebody who has not finished five rounds has not yet decided whether
    /// they like this app.
    static let minimumRoundsCompleted = 5
    static let quietPeriod: TimeInterval = 8 * 60
    static let perSessionCap = 2
    /// Nothing within twenty seconds of opening the app: an ad on the way *in*
    /// is the single most resented placement there is.
    static let launchGrace: TimeInterval = 20

    static func shouldShowInterstitial(
        state: AdPacingState,
        hasPlus: Bool,
        now: Date = Date()
    ) -> Bool {
        // Plus removes them entirely. This is the benefit people actually buy.
        guard !hasPlus else { return false }

        guard state.launchCount >= minimumLaunches else { return false }
        guard state.roundsCompleted >= minimumRoundsCompleted else { return false }
        guard state.interstitialsThisSession < perSessionCap else { return false }
        guard now.timeIntervalSince(state.sessionStartedAt) >= launchGrace else { return false }

        if let last = state.lastInterstitialAt {
            guard now.timeIntervalSince(last) >= quietPeriod else { return false }
        }

        return true
    }
}

/// Persists `AdPacingState` and counts the things it needs counted.
///
/// Deliberately not an analytics layer: these counters never leave the device
/// and there is no SDK behind them. Install and conversion numbers come from
/// App Store Connect, which gives them away free and does not cost the app its
/// "no accounts and no network" promise.
@Observable
@MainActor
final class AdPacingStore {
    private static let storageKey = "caster.ads.pacing"

    private(set) var state = AdPacingState()

    /// Set when a game screen appears, cleared when the mode list consumes it.
    ///
    /// This is what pins the one placement in place. Without it the mode list
    /// could not tell "the user just finished a game" from "the user pressed
    /// Begin" or "the user backed out of the wheel editor", and the ad would
    /// start appearing on the way *into* the app. Not persisted: an arm that
    /// survived a relaunch would fire on the next cold start.
    private(set) var isArmed = false

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        load()
    }

    /// Called once per cold start. The session counters reset here rather than
    /// in `init` so a test can construct a store without pretending to launch.
    func beginSession(now: Date = Date()) {
        state.launchCount += 1
        state.interstitialsThisSession = 0
        state.sessionStartedAt = now
        save()
    }

    /// Every game calls this when a round resolves — the same moment it shows a
    /// result, not the moment it is entered.
    func recordRoundCompleted() {
        state.roundsCompleted += 1
        save()
    }

    /// Called by the game host as a game appears.
    func armForInterstitial() {
        isArmed = true
    }

    /// Consumed by the mode list on the way back. Returns whether this really is
    /// a return from a game, and disarms either way.
    func consumeArming() -> Bool {
        defer { isArmed = false }
        return isArmed
    }

    func recordInterstitialShown(now: Date = Date()) {
        state.interstitialsThisSession += 1
        state.lastInterstitialAt = now
        save()
    }

    func shouldShowInterstitial(hasPlus: Bool, now: Date = Date()) -> Bool {
        AdPacing.shouldShowInterstitial(state: state, hasPlus: hasPlus, now: now)
    }

    #if DEBUG
    /// Puts the counters back to a fresh install, so the "nothing in the first
    /// two sessions, nothing before the fifth round" rules can be felt more than
    /// once without deleting the app.
    func resetForTesting() {
        state = AdPacingState()
        isArmed = false
        save()
    }
    #endif

    private func load() {
        guard let data = defaults.data(forKey: Self.storageKey),
              let decoded = try? JSONDecoder().decode(AdPacingState.self, from: data) else {
            return
        }
        state = decoded
    }

    private func save() {
        if let data = try? JSONEncoder().encode(state) {
            defaults.set(data, forKey: Self.storageKey)
        }
    }
}
