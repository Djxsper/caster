package com.jesperhaafkes.caster

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.jesperhaafkes.caster.billing.BillingService
import com.jesperhaafkes.caster.domain.AdPacingStore
import com.jesperhaafkes.caster.domain.EntitlementStore
import com.jesperhaafkes.caster.domain.GameState
import com.jesperhaafkes.caster.domain.RosterStore
import com.jesperhaafkes.caster.domain.WheelStore
import com.jesperhaafkes.caster.ui.ads.AdPresenter
import com.jesperhaafkes.caster.ui.ads.AdPresenterFactory
import com.jesperhaafkes.caster.ui.ads.FakeAdPresenter
import com.jesperhaafkes.caster.ui.audio.SoundEngine
import com.jesperhaafkes.caster.ui.audio.Tone
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.haptics.HapticEngine
import com.jesperhaafkes.caster.ui.theme.ThemeStore

/**
 * App-wide services, handed down through a composition local.
 *
 * Seat colours live on the theme rather than here, so light and dark palettes
 * have a single owner.
 */
class AppEnvironment(context: Context, private val prefs: SharedPreferences) {
    val hapticEngine = HapticEngine(context)
    val soundEngine = SoundEngine(context)

    /**
     * No-op on any build without an ad network linked, which is every build
     * from this repository. See [AdPresenterFactory].
     */
    val ads: AdPresenter = AdPresenterFactory.make()

    /**
     * Counts the things the interstitial rules need counted. Never leaves the
     * device and has no SDK behind it.
     */
    val pacing = AdPacingStore(prefs)

    /**
     * The very same object as [ads], downcast so the debug overlay can observe
     * it. Not a second instance — a second one would count its own ads and
     * report numbers that had nothing to do with what you saw.
     */
    val fakeAds: FakeAdPresenter? get() = ads as? FakeAdPresenter

    private val mutedState = mutableStateOf(prefs.getBoolean(MUTED_KEY, false))

    /**
     * Mirrored onto the sound engine so the toggle has one home, and written
     * through to disk so the settings screen does not forget it on relaunch.
     */
    var isMuted: Boolean
        get() = mutedState.value
        set(value) {
            mutedState.value = value
            soundEngine.isMuted = value
            prefs.edit().putBoolean(MUTED_KEY, value).apply()
        }

    init {
        soundEngine.isMuted = mutedState.value
    }

    /**
     * The two cues that always fire together: a tap you feel and a tap you
     * hear. Kept here so no game has to remember to do both.
     *
     * And, for the same reason, so no game has to remember to say a round
     * finished. [Tone.REVEAL] and [Tone.BOOM] are exactly the tones that mean
     * "this round is over", and between them all six games fire one, once, at
     * the moment they resolve. Counting here rather than in six screen files
     * means a new game gets it for free and cannot forget it.
     */
    fun cue(feedback: FeedbackType, tone: Tone) {
        hapticEngine.playFeedback(feedback)
        soundEngine.play(tone)

        if (tone == Tone.REVEAL || tone == Tone.BOOM) {
            pacing.recordRoundCompleted()
        }
    }

    private companion object {
        const val MUTED_KEY = "caster.sound.muted"
    }
}

val LocalAppEnvironment = staticCompositionLocalOf<AppEnvironment> {
    error("AppEnvironment not provided")
}

val LocalGameState = staticCompositionLocalOf<GameState> {
    error("GameState not provided")
}

val LocalRosterStore = staticCompositionLocalOf<RosterStore> {
    error("RosterStore not provided")
}

val LocalWheelStore = staticCompositionLocalOf<WheelStore> {
    error("WheelStore not provided")
}

val LocalEntitlements = staticCompositionLocalOf<EntitlementStore> {
    error("EntitlementStore not provided")
}

val LocalBilling = staticCompositionLocalOf<BillingService> {
    error("BillingService not provided")
}

val LocalThemeStore = staticCompositionLocalOf<ThemeStore> {
    error("ThemeStore not provided")
}
