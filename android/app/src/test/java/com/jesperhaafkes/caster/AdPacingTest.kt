package com.jesperhaafkes.caster

import com.jesperhaafkes.caster.domain.AdPacing
import com.jesperhaafkes.caster.domain.AdPacingState
import com.jesperhaafkes.caster.domain.AdPacingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide whether Caster feels like a party game or like an ad
 * delivery mechanism.
 *
 * Every one of these is a promise made on the paywall or in the README, so a
 * change that loosens one should have to delete a test that says so out loud.
 * The twin of `CasterTests/AdPacingTests.swift`.
 */
class AdPacingTest {

    /** Well past every threshold, so each test can spoil exactly one clause. */
    private fun eligible(now: Long) = AdPacingState(
        launchCount = AdPacing.MINIMUM_LAUNCHES,
        roundsCompleted = AdPacing.MINIMUM_ROUNDS_COMPLETED,
        lastInterstitialAt = 0L,
        interstitialsThisSession = 0,
        sessionStartedAt = now - AdPacing.LAUNCH_GRACE_MS,
    )

    private val now = 1_700_000_000_000L

    @Test
    fun `the baseline state is eligible`() {
        // Without this the tests below could all be passing for the wrong
        // reason - a fixture that never qualifies proves nothing.
        assertTrue(AdPacing.shouldShowInterstitial(eligible(now), hasPlus = false, now = now))
    }

    @Test
    fun `Plus removes interstitials entirely`() {
        assertFalse(AdPacing.shouldShowInterstitial(eligible(now), hasPlus = true, now = now))
    }

    @Test
    fun `the first two sessions never show one`() {
        for (launch in 1 until AdPacing.MINIMUM_LAUNCHES) {
            val state = eligible(now).copy(launchCount = launch)
            assertFalse(
                "launch $launch should be too early",
                AdPacing.shouldShowInterstitial(state, hasPlus = false, now = now),
            )
        }
    }

    @Test
    fun `nothing before the fifth completed round`() {
        for (rounds in 0 until AdPacing.MINIMUM_ROUNDS_COMPLETED) {
            val state = eligible(now).copy(roundsCompleted = rounds)
            assertFalse(
                "$rounds rounds should be too few",
                AdPacing.shouldShowInterstitial(state, hasPlus = false, now = now),
            )
        }
    }

    @Test
    fun `the session cap holds`() {
        val state = eligible(now).copy(interstitialsThisSession = AdPacing.PER_SESSION_CAP)
        assertFalse(AdPacing.shouldShowInterstitial(state, hasPlus = false, now = now))
    }

    @Test
    fun `nothing within the launch grace period`() {
        // An ad on the way *in* is the single most resented placement there is.
        val state = eligible(now).copy(sessionStartedAt = now - (AdPacing.LAUNCH_GRACE_MS - 1))
        assertFalse(AdPacing.shouldShowInterstitial(state, hasPlus = false, now = now))
    }

    @Test
    fun `the quiet period is respected and then released`() {
        val tooSoon = eligible(now).copy(lastInterstitialAt = now - (AdPacing.QUIET_PERIOD_MS - 1))
        assertFalse(AdPacing.shouldShowInterstitial(tooSoon, hasPlus = false, now = now))

        val longEnough = eligible(now).copy(lastInterstitialAt = now - AdPacing.QUIET_PERIOD_MS)
        assertTrue(AdPacing.shouldShowInterstitial(longEnough, hasPlus = false, now = now))
    }

    // MARK: - The store

    @Test
    fun `arming is consumed exactly once`() {
        val store = AdPacingStore(FakePrefs())
        assertFalse("nothing is armed to begin with", store.consumeArming())

        store.armForInterstitial()
        assertTrue("the first read is the return from a game", store.consumeArming())
        assertFalse("a second read is the way back in, not out", store.consumeArming())
    }

    @Test
    fun `counters survive a relaunch but the session does not`() {
        val prefs = FakePrefs()
        val first = AdPacingStore(prefs)
        first.beginSession(now)
        repeat(3) { first.recordRoundCompleted() }
        first.recordInterstitialShown(now)

        val second = AdPacingStore(prefs)
        assertEquals("rounds are cumulative", 3, second.state.roundsCompleted)
        assertEquals("launches are cumulative", 1, second.state.launchCount)
        assertEquals(
            "the per-session count belongs to the process that made it",
            0,
            second.state.interstitialsThisSession,
        )
    }

    @Test
    fun `an armed interstitial cannot survive a relaunch`() {
        val prefs = FakePrefs()
        AdPacingStore(prefs).armForInterstitial()

        // Otherwise the app would open onto an ad, which is the one placement
        // the whole design exists to prevent.
        assertFalse(AdPacingStore(prefs).consumeArming())
    }
}
