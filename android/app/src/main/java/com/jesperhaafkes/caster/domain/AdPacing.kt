package com.jesperhaafkes.caster.domain

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Everything the interstitial decision is allowed to look at. Plain data with
 * no clock and no storage of its own, so the rules below can be tested by
 * handing them a state and a timestamp rather than by waiting eight minutes and
 * watching a screen.
 *
 * Times are epoch milliseconds, and `0L` means never. The iOS twin uses
 * `Date?`; the shape of the decision is identical either way.
 */
data class AdPacingState(
    val launchCount: Int = 0,
    val roundsCompleted: Int = 0,
    val lastInterstitialAt: Long = 0L,
    /** Reset on every cold start, so they never persist. */
    val interstitialsThisSession: Int = 0,
    val sessionStartedAt: Long = 0L,
)

/**
 * When an interstitial may fire, and nothing else.
 *
 * There is exactly one placement in the whole app — coming back from a game to
 * the mode list — and every clause here must pass. Written as a pure function
 * rather than as conditions scattered through the screens because this is the
 * part that decides whether Caster feels like a party game or like an ad
 * delivery mechanism, and it should be readable in one screen and provable in a
 * test.
 *
 * The numbers live in `shared/monetization/offering.json`; iOS reads the same
 * file, and `OfferingParityTest` fails if this drifts from it.
 */
object AdPacing {
    /**
     * The first two sessions never show one. A first impression is worth more
     * than an impression.
     */
    const val MINIMUM_LAUNCHES = 3

    /**
     * Somebody who has not finished five rounds has not yet decided whether
     * they like this app.
     */
    const val MINIMUM_ROUNDS_COMPLETED = 5

    const val QUIET_PERIOD_MS = 8 * 60 * 1000L
    const val PER_SESSION_CAP = 2

    /**
     * Nothing within twenty seconds of opening the app: an ad on the way *in*
     * is the single most resented placement there is.
     */
    const val LAUNCH_GRACE_MS = 20 * 1000L

    fun shouldShowInterstitial(
        state: AdPacingState,
        hasPlus: Boolean,
        now: Long,
    ): Boolean {
        // Plus removes them entirely. This is the benefit people actually buy.
        if (hasPlus) return false

        if (state.launchCount < MINIMUM_LAUNCHES) return false
        if (state.roundsCompleted < MINIMUM_ROUNDS_COMPLETED) return false
        if (state.interstitialsThisSession >= PER_SESSION_CAP) return false
        if (now - state.sessionStartedAt < LAUNCH_GRACE_MS) return false

        if (state.lastInterstitialAt != 0L && now - state.lastInterstitialAt < QUIET_PERIOD_MS) {
            return false
        }

        return true
    }
}

/**
 * Persists [AdPacingState] and counts the things it needs counted.
 *
 * Deliberately not an analytics layer: these counters never leave the device
 * and there is no SDK behind them. Install and conversion numbers come from the
 * Play Console, which gives them away free and does not cost the app its "no
 * accounts and no network" promise.
 */
class AdPacingStore(private val prefs: SharedPreferences) {

    var state: AdPacingState by mutableStateOf(AdPacingState())
        private set

    /**
     * Set when a game screen appears, cleared when the mode list consumes it.
     *
     * This is what pins the one placement in place. Without it the mode list
     * could not tell "the user just finished a game" from "the user pressed
     * Begin" or "the user backed out of the wheel editor", and the ad would
     * start appearing on the way *into* the app. Not persisted: an arm that
     * survived a relaunch would fire on the next cold start.
     */
    var isArmed: Boolean by mutableStateOf(false)
        private set

    init {
        load()
    }

    /**
     * Called once per cold start. The session counters reset here rather than
     * in `init` so a test can construct a store without pretending to launch.
     */
    fun beginSession(now: Long = System.currentTimeMillis()) {
        state = state.copy(
            launchCount = state.launchCount + 1,
            interstitialsThisSession = 0,
            sessionStartedAt = now,
        )
        save()
    }

    /**
     * Every game reaches this when a round resolves — the same moment it shows
     * a result, not the moment it is entered.
     */
    fun recordRoundCompleted() {
        state = state.copy(roundsCompleted = state.roundsCompleted + 1)
        save()
    }

    /** Called by the game host as a game appears. */
    fun armForInterstitial() {
        isArmed = true
    }

    /**
     * Consumed by the mode list on the way back. Returns whether this really is
     * a return from a game, and disarms either way.
     */
    fun consumeArming(): Boolean {
        val wasArmed = isArmed
        isArmed = false
        return wasArmed
    }

    fun recordInterstitialShown(now: Long = System.currentTimeMillis()) {
        state = state.copy(
            interstitialsThisSession = state.interstitialsThisSession + 1,
            lastInterstitialAt = now,
        )
        save()
    }

    fun shouldShowInterstitial(hasPlus: Boolean, now: Long = System.currentTimeMillis()): Boolean =
        AdPacing.shouldShowInterstitial(state, hasPlus, now)

    /**
     * Puts the counters back to a fresh install, so the "nothing in the first
     * two sessions, nothing before the fifth round" rules can be felt more than
     * once without clearing the app's data.
     *
     * Debug affordance, but not compiled out: Kotlin has no `#if DEBUG`, so the
     * gate is that nothing in a release build calls it — the Settings section
     * that does is behind `BuildConfig.DEBUG`.
     */
    fun resetForTesting(now: Long = System.currentTimeMillis()) {
        state = AdPacingState(sessionStartedAt = now)
        isArmed = false
        save()
    }

    private fun load() {
        val raw = prefs.getString(STORAGE_KEY, null) ?: return
        val decoded = runCatching {
            val json = JSONObject(raw)
            AdPacingState(
                launchCount = json.optInt("launchCount", 0),
                roundsCompleted = json.optInt("roundsCompleted", 0),
                lastInterstitialAt = json.optLong("lastInterstitialAt", 0L),
                // Session counters are deliberately not read back: a session is
                // this process, and a stored one belongs to a previous launch.
            )
        }.getOrNull() ?: return
        state = decoded
    }

    private fun save() {
        val json = JSONObject()
            .put("launchCount", state.launchCount)
            .put("roundsCompleted", state.roundsCompleted)
            .put("lastInterstitialAt", state.lastInterstitialAt)
        prefs.edit().putString(STORAGE_KEY, json.toString()).apply()
    }

    private companion object {
        const val STORAGE_KEY = "caster.ads.pacing"
    }
}
