package com.jesperhaafkes.caster.domain

/**
 * The numbers that decide how the games feel.
 *
 * These were previously locals inside each screen's composable, which made them
 * invisible to a test and easy to change on one platform only. Caster is written
 * twice — SwiftUI on iOS, Compose here — so a constant that drifts makes the same
 * game play differently on the two phones, and nothing anywhere fails.
 *
 * `ParityTest` checks every value below against `shared/parity/golden.json`,
 * which is the fixture the iOS suite is meant to read too. Change a number here
 * and that test goes red until the contract is updated on purpose.
 */
object GameTuning {

    object Chicken {
        /**
         * Deliberately below human reaction time. The opening flashes are meant
         * to be unmissable in the bad sense; the tension is watching the window
         * creep up towards something anyone can actually hit.
         */
        const val START_WINDOW_MS = 100.0

        /** Every missed flash widens the window, so a round calibrates itself. */
        const val WINDOW_STEP_MS = 50.0

        /** Nothing needs a window this wide; it only stops a stuck round crawling. */
        const val MAX_WINDOW_MS = 2_500.0

        /**
         * Slack for the trip from glass to callback, so a lift that really did
         * land inside the window is not thrown out by delivery lag.
         */
        const val LATENCY_GRACE_MS = 90L

        const val SETTLE_DURATION_MS = 1_500L
    }

    object FingerPicker {
        /** How long everyone holds still before the draw resolves. */
        const val HOLD_DURATION_MS = 3_000
    }

    object Uppercut {
        const val SETTLE_DURATION_MS = 1_200L
    }

    object TapFrenzy {
        const val SETTLE_DURATION_MS = 1_500L

        /**
         * Tapping never buys certainty: everybody keeps a floor in the draw
         * whatever they do, so the gap between the hardest tapper and the
         * laziest is capped at this, in either direction.
         */
        const val MAX_ADVANTAGE_RATIO = 5.0
    }
}
