import XCTest
@testable import Caster

/// The interstitial rules, proved directly.
///
/// `AdPacing.shouldShowInterstitial` takes its clock as an argument precisely so
/// these can run in milliseconds instead of waiting eight minutes and watching a
/// screen. Every clause gets its own test, because the whole point of the
/// function is that all of them hold at once.
final class AdPacingTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    /// A state that passes every clause, so each test can break exactly one.
    private func eligible() -> AdPacingState {
        AdPacingState(
            launchCount: AdPacing.minimumLaunches,
            roundsCompleted: AdPacing.minimumRoundsCompleted,
            lastInterstitialAt: nil,
            interstitialsThisSession: 0,
            sessionStartedAt: now.addingTimeInterval(-AdPacing.launchGrace - 1)
        )
    }

    func testBaselineIsEligible() {
        XCTAssertTrue(AdPacing.shouldShowInterstitial(state: eligible(), hasPlus: false, now: now))
    }

    func testPlusNeverSeesOne() {
        XCTAssertFalse(AdPacing.shouldShowInterstitial(state: eligible(), hasPlus: true, now: now))
    }

    func testFirstTwoSessionsAreClean() {
        for launches in 0..<AdPacing.minimumLaunches {
            var state = eligible()
            state.launchCount = launches
            XCTAssertFalse(
                AdPacing.shouldShowInterstitial(state: state, hasPlus: false, now: now),
                "launch \(launches) should not show an ad"
            )
        }
    }

    func testNothingBeforeTheFifthRound() {
        var state = eligible()
        state.roundsCompleted = AdPacing.minimumRoundsCompleted - 1
        XCTAssertFalse(AdPacing.shouldShowInterstitial(state: state, hasPlus: false, now: now))
    }

    func testQuietPeriodHolds() {
        var state = eligible()
        state.lastInterstitialAt = now.addingTimeInterval(-AdPacing.quietPeriod + 1)
        XCTAssertFalse(AdPacing.shouldShowInterstitial(state: state, hasPlus: false, now: now))

        state.lastInterstitialAt = now.addingTimeInterval(-AdPacing.quietPeriod)
        XCTAssertTrue(AdPacing.shouldShowInterstitial(state: state, hasPlus: false, now: now))
    }

    func testSessionCap() {
        var state = eligible()
        state.interstitialsThisSession = AdPacing.perSessionCap
        XCTAssertFalse(AdPacing.shouldShowInterstitial(state: state, hasPlus: false, now: now))
    }

    /// The one that protects the first impression hardest: nothing on the way
    /// into the app, however many rounds have been played before.
    func testLaunchGrace() {
        var state = eligible()
        state.sessionStartedAt = now.addingTimeInterval(-AdPacing.launchGrace + 1)
        XCTAssertFalse(AdPacing.shouldShowInterstitial(state: state, hasPlus: false, now: now))
    }

    @MainActor
    func testArmingIsRequiredAndConsumedOnce() {
        let store = AdPacingStore(defaults: Self.freshDefaults())
        XCTAssertFalse(store.consumeArming())

        store.armForInterstitial()
        XCTAssertTrue(store.consumeArming())
        XCTAssertFalse(store.consumeArming(), "arming must not survive being consumed")
    }

    @MainActor
    func testSessionCountersResetButTotalsPersist() {
        let defaults = Self.freshDefaults()

        let first = AdPacingStore(defaults: defaults)
        first.beginSession()
        first.recordRoundCompleted()
        first.recordRoundCompleted()
        first.recordInterstitialShown()
        XCTAssertEqual(first.state.interstitialsThisSession, 1)

        let second = AdPacingStore(defaults: defaults)
        second.beginSession()
        XCTAssertEqual(second.state.roundsCompleted, 2, "rounds are cumulative")
        XCTAssertEqual(second.state.launchCount, 2)
        XCTAssertEqual(second.state.interstitialsThisSession, 0, "the per-session cap resets")
        XCTAssertNotNil(second.state.lastInterstitialAt, "the quiet period survives a relaunch")
    }

    private static func freshDefaults() -> UserDefaults {
        let name = "caster.tests.\(UUID().uuidString)"
        UserDefaults().removePersistentDomain(forName: name)
        return UserDefaults(suiteName: name)!
    }
}
