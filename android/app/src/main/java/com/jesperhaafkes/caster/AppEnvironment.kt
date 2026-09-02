package com.jesperhaafkes.caster

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.jesperhaafkes.caster.domain.GameState
import com.jesperhaafkes.caster.domain.RosterStore
import com.jesperhaafkes.caster.domain.WheelStore
import com.jesperhaafkes.caster.ui.audio.SoundEngine
import com.jesperhaafkes.caster.ui.audio.Tone
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.haptics.HapticEngine

/**
 * App-wide services, handed down through a composition local.
 *
 * Seat colours live on the theme rather than here, so light and dark palettes
 * have a single owner.
 */
class AppEnvironment(context: Context) {
    val hapticEngine = HapticEngine(context)
    val soundEngine = SoundEngine(context)

    private val mutedState = mutableStateOf(false)

    /** Mirrored onto the sound engine so the toggle has one home. */
    var isMuted: Boolean
        get() = mutedState.value
        set(value) {
            mutedState.value = value
            soundEngine.isMuted = value
        }

    /**
     * The two cues that always fire together: a tap you feel and a tap you
     * hear. Kept here so no game has to remember to do both.
     */
    fun cue(feedback: FeedbackType, tone: Tone) {
        hapticEngine.playFeedback(feedback)
        soundEngine.play(tone)
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
