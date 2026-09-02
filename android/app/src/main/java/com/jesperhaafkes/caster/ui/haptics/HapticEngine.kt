package com.jesperhaafkes.caster.ui.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The four cue strengths the games ask for. Named after the feel rather than
 * the API so the mapping below is the only place hardware terms appear.
 */
enum class FeedbackType {
    LIGHT,
    MEDIUM,
    HEAVY,
    SHARP;

    /** 0…1, matched to the intensities the iOS build uses. */
    val intensity: Float
        get() = when (this) {
            LIGHT -> 0.3f
            MEDIUM -> 0.5f
            HEAVY -> 0.8f
            SHARP -> 0.4f
        }

    /** How long the transient runs. Sharper cues are shorter, not weaker. */
    val durationMs: Long
        get() = when (this) {
            LIGHT -> 12L
            MEDIUM -> 20L
            HEAVY -> 34L
            SHARP -> 8L
        }

    /** The composition primitive that best matches this cue, on hardware that has them. */
    val primitive: Int
        get() = when (this) {
            LIGHT -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            MEDIUM -> VibrationEffect.Composition.PRIMITIVE_CLICK
            HEAVY -> VibrationEffect.Composition.PRIMITIVE_THUD
            SHARP -> VibrationEffect.Composition.PRIMITIVE_TICK
        }
}

/**
 * The Android answer to Core Haptics. Composition primitives are the close
 * equivalent of a `CHHapticEvent` transient and are used where the hardware
 * reports them; everything else degrades to an amplitude-controlled one-shot,
 * and then to a plain buzz, so a device without an actuator simply feels
 * nothing rather than crashing.
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private val hasVibrator: Boolean = vibrator?.hasVibrator() == true

    // Every element here comes from FeedbackType.primitive, which returns
    // nothing but VibrationEffect.Composition.PRIMITIVE_* constants — but the
    // @IntDef does not survive the trip through List<Int> and the spread into
    // arePrimitivesSupported, so lint cannot see that and flags WrongConstant.
    @Suppress("WrongConstant")
    private val supportedPrimitives: Set<Int> by lazy {
        val device = vibrator
        if (device == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@lazy emptySet()
        val candidates = FeedbackType.entries.map { it.primitive }.distinct()
        runCatching {
            val supported = device.arePrimitivesSupported(*candidates.toIntArray())
            candidates.filterIndexed { index, _ -> supported.getOrElse(index) { false } }.toSet()
        }.getOrDefault(emptySet())
    }

    private val hasAmplitudeControl: Boolean by lazy {
        runCatching { vibrator?.hasAmplitudeControl() == true }.getOrDefault(false)
    }

    /**
     * Kept for parity with the iOS engine, which owns a real engine object that
     * has to be spun up and torn down. Android's vibrator is stateless, so
     * these are no-ops rather than absent — the call sites stay identical.
     */
    fun startEngine() = Unit

    fun endEngine() {
        runCatching { vibrator?.cancel() }
    }

    fun prepareGenerators() = Unit

    fun playFeedback(type: FeedbackType) {
        val device = vibrator ?: return
        if (!hasVibrator) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                type.primitive in supportedPrimitives
            ) {
                device.play(
                    VibrationEffect.startComposition()
                        .addPrimitive(type.primitive, type.intensity)
                        .compose()
                )
                return
            }

            val amplitude = if (hasAmplitudeControl) {
                (type.intensity * 255).toInt().coerceIn(1, 255)
            } else {
                VibrationEffect.DEFAULT_AMPLITUDE
            }
            device.play(VibrationEffect.createOneShot(type.durationMs, amplitude))
        }
    }

    /**
     * Plays [effect] as interactive feedback rather than as an anonymous buzz.
     *
     * A bare `vibrate(effect)` carries no usage, so the system files it under
     * USAGE_UNKNOWN and is free to drop it depending on ringer and
     * Do-Not-Disturb state — which would silently take the cue out of a game
     * whose whole signal is that cue. Declaring it as touch feedback ties it to
     * the haptics setting the user actually chose.
     */
    private fun Vibrator.play(effect: VibrationEffect) {
        // VibrationAttributes exists from API 30, but the vibrate() overload
        // that takes one only arrives in 33; below that the AudioAttributes
        // overload carries the same intent.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrate(
                effect,
                VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build(),
            )
        } else {
            vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }
}
