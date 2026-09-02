package com.jesperhaafkes.caster.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.domain.GameTuning
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.domain.DrawEngine
import com.jesperhaafkes.caster.domain.GameMode
import com.jesperhaafkes.caster.touch.TouchArena
import com.jesperhaafkes.caster.ui.audio.Tone
import com.jesperhaafkes.caster.ui.components.KeepScreenOn
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.ControlPanel
import com.jesperhaafkes.caster.ui.components.EmptyPlayHint
import com.jesperhaafkes.caster.ui.components.PositionedFingerRing
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.components.ResultTable
import com.jesperhaafkes.caster.ui.components.SecondaryButton
import com.jesperhaafkes.caster.ui.components.SegmentedControl
import com.jesperhaafkes.caster.ui.components.StatusLine
import com.jesperhaafkes.caster.ui.components.TouchSurface
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the draw is for, and therefore which way the taps push. */
private enum class Stake(val label: String, val caption: String, val verb: String) {
    AVOID("Avoid It", "Tap the most to be safest — never safe.", "loses"),
    WIN("Win It", "Tap the most to be likeliest — never certain.", "wins"),
}

private enum class FrenzyPhase { CLAIMING, COUNTDOWN, TAPPING, REVEALING, FINISHED }

/**
 * One row per player, longest odds first. `share` is the real probability of
 * being drawn, taken straight from the weights the draw itself uses.
 */
private data class OddsRow(val slot: Int, val taps: Int, val share: Double)

/**
 * Finger Picker with a lever. Claim a circle, then hammer it for five seconds
 * to lean the draw your way.
 *
 * Which way is up to the table. Set it to **Avoid** and tapping shortens your
 * odds of being picked; set it to **Win** and tapping lengthens them, for the
 * times the thing being handed out is worth having. Either way the lever has a
 * floor and a ceiling: the top tapper still carries real weight and the laziest
 * player still has a real chance, so no amount of tapping settles it outright.
 */
@Composable
fun TapFrenzyScreen(onBack: () -> Unit) {
    val theme = LocalTheme.current
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()

    val arena = remember { TouchArena() }
    var phase by remember { mutableStateOf(FrenzyPhase.CLAIMING) }
    var stake by remember { mutableStateOf(Stake.AVOID) }
    val roundSlots = remember { mutableStateListOf<Int>() }
    val taps = remember { mutableStateMapOf<Int, Int>() }
    var countdownValue by remember { mutableIntStateOf(3) }
    var timeRemaining by remember { mutableFloatStateOf(0f) }
    var spotlightSlot by remember { mutableStateOf<Int?>(null) }

    /** Whoever the draw landed on. What that means to them depends on [stake]. */
    var pickedSlot by remember { mutableStateOf<Int?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var roundJob by remember { mutableStateOf<Job?>(null) }

    val settleDurationMs = GameTuning.TapFrenzy.SETTLE_DURATION_MS
    val tapWindowSeconds = 5.0f

    /**
     * Everyone's floor in the draw, and the most the taps can add on top. The
     * ratio between them is all the lever is worth: five to one, either way.
     */
    val baseWeight = 10
    val swingWeight = 40

    /**
     * Weight in the draw, per player, in [roundSlots] order.
     *
     * Everyone keeps [baseWeight] whatever they do; the taps only decide how
     * much of [swingWeight] sits on top. Avoiding, that swing goes to whoever
     * tapped *least*; winning, it goes to whoever tapped most.
     */
    fun drawWeights(): List<Int> {
        val counts = roundSlots.map { taps[it] ?: 0 }
        val best = counts.maxOrNull() ?: 0
        if (best <= 0) return counts.map { baseWeight }

        return counts.map { count ->
            val fraction = if (stake == Stake.AVOID) {
                (best - count).toDouble() / best
            } else {
                count.toDouble() / best
            }
            baseWeight + (swingWeight * fraction).roundToInt()
        }
    }

    /**
     * A short spotlight sweep before the answer, slowing as it goes. The draw
     * happens first; the sweep is theatre laid over a decision already made.
     */
    suspend fun runReveal() {
        phase = FrenzyPhase.REVEALING
        environment.cue(FeedbackType.MEDIUM, Tone.REVEAL)

        if (roundSlots.isEmpty()) return
        val weights = drawWeights()
        val index = DrawEngine.drawIndexWithWeights(weights, roundSlots.size) ?: return
        val drawn = roundSlots[index]

        var delayMs = 60.0
        var elapsed = 0.0
        var cursor = 0
        while (elapsed < 1_800.0) {
            spotlightSlot = roundSlots[cursor % roundSlots.size]
            cursor += 1
            environment.hapticEngine.playFeedback(FeedbackType.LIGHT)
            delay(delayMs.toLong())
            elapsed += delayMs
            delayMs = minOf(260.0, delayMs * 1.16)
        }

        spotlightSlot = null
        pickedSlot = drawn
        phase = FrenzyPhase.FINISHED
        environment.cue(
            FeedbackType.HEAVY,
            if (stake == Stake.AVOID) Tone.MISS else Tone.SAFE,
        )
    }

    fun beginRound() {
        if (phase != FrenzyPhase.CLAIMING || arena.activeCount < 2) return

        roundSlots.clear()
        roundSlots.addAll(arena.occupiedSlots.sorted())
        taps.clear()
        pickedSlot = null
        spotlightSlot = null
        countdownValue = 3
        // From here every touch is a tap for an existing circle, never a new one.
        arena.acceptsNewSlots = false
        phase = FrenzyPhase.COUNTDOWN

        roundJob = scope.launch {
            for (pip in 3 downTo 1) {
                countdownValue = pip
                environment.cue(FeedbackType.MEDIUM, Tone.PIP)
                delay(1_000)
            }

            phase = FrenzyPhase.TAPPING
            timeRemaining = tapWindowSeconds
            environment.cue(FeedbackType.HEAVY, Tone.CUE)

            val started = System.nanoTime()
            while (true) {
                val elapsed = (System.nanoTime() - started) / 1_000_000_000.0f
                timeRemaining = maxOf(0f, tapWindowSeconds - elapsed)
                if (elapsed >= tapWindowSeconds) break
                delay(50)
            }

            runReveal()
        }
    }

    fun scheduleStart() {
        settleJob?.cancel()
        settleJob = null
        if (phase != FrenzyPhase.CLAIMING || arena.activeCount < 2) return

        settleJob = scope.launch {
            delay(settleDurationMs)
            beginRound()
        }
    }

    fun teardown() {
        settleJob?.cancel()
        roundJob?.cancel()
        settleJob = null
        roundJob = null
        arena.reset()
    }

    fun resetRound() {
        teardown()
        phase = FrenzyPhase.CLAIMING
        roundSlots.clear()
        taps.clear()
        pickedSlot = null
        spotlightSlot = null
        timeRemaining = 0f
        countdownValue = 3
    }

    DisposableEffect(arena) {
        // Generous radius: taps land near a circle, not on it. Nearest anchor
        // still wins, so neighbours sitting close together stay separable.
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(130.dp)
        arena.reset()

        arena.onBegan = { finger ->
            when (phase) {
                FrenzyPhase.CLAIMING -> {
                    environment.cue(FeedbackType.LIGHT, Tone.PLACE)
                    scheduleStart()
                }

                FrenzyPhase.TAPPING -> {
                    if (finger.slot in roundSlots) {
                        taps[finger.slot] = (taps[finger.slot] ?: 0) + 1
                        environment.hapticEngine.playFeedback(FeedbackType.LIGHT)
                    }
                }

                else -> Unit
            }
        }

        arena.onEnded = {
            if (phase == FrenzyPhase.CLAIMING) scheduleStart()
        }

        onDispose {
            arena.onBegan = null
            arena.onEnded = null
            teardown()
        }
    }

    val outcomeTint = if (stake == Stake.AVOID) theme.danger else theme.success
    val isStakePanelVisible = phase == FrenzyPhase.CLAIMING && arena.activeCount == 0

    val oddsRows = run {
        val weights = drawWeights()
        val total = maxOf(1, weights.sum())
        roundSlots
            .mapIndexed { index, slot ->
                OddsRow(
                    slot = slot,
                    taps = taps[slot] ?: 0,
                    share = (weights.getOrElse(index) { 0 }).toDouble() / total,
                )
            }
            .sortedByDescending { it.share }
    }

    val statusText = when (phase) {
        FrenzyPhase.CLAIMING -> when (arena.activeCount) {
            0 -> "Everyone place a finger to claim a circle"
            1 -> "One circle claimed — needs at least two"
            else -> "Hold still…"
        }

        FrenzyPhase.COUNTDOWN -> "Lift off. Tap your own circle when it hits zero."
        FrenzyPhase.TAPPING -> "TAP!"
        FrenzyPhase.REVEALING -> "Drawing…"
        FrenzyPhase.FINISHED -> pickedSlot
            ?.let { "Player ${it + 1} ${stake.verb}." }
            ?: "Round complete."
    }

    val statusTint = when (phase) {
        FrenzyPhase.TAPPING -> theme.accent
        FrenzyPhase.FINISHED -> outcomeTint
        else -> null
    }

    KeepScreenOn()

    CasterScreen(title = GameMode.TAP_FRENZY.title, onBack = onBack) {
        TouchSurface(arena) {
            if (phase == FrenzyPhase.CLAIMING && arena.activeCount == 0) {
                EmptyPlayHint(
                    glyph = "👆",
                    title = "Claim a circle",
                    detail = "Put a finger down where you want your circle. You will tap it later.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (phase == FrenzyPhase.CLAIMING) {
                for (finger in arena.activeFingers) {
                    PositionedFingerRing(
                        location = finger.location,
                        color = theme.playerColor(finger.slot),
                        diameter = 80.dp,
                    )
                }
            } else {
                for (slot in roundSlots) {
                    val anchor = arena.anchor(slot) ?: continue
                    val ringColor = when {
                        pickedSlot != null ->
                            if (slot == pickedSlot) outcomeTint else theme.playerColor(slot)

                        slot == spotlightSlot -> theme.warning
                        else -> theme.playerColor(slot)
                    }
                    PositionedFingerRing(
                        location = anchor,
                        color = ringColor,
                        diameter = 80.dp,
                        isHighlighted = slot == spotlightSlot || slot == pickedSlot,
                        isDimmed = pickedSlot != null && slot != pickedSlot,
                        badge = if (phase == FrenzyPhase.CLAIMING ||
                            phase == FrenzyPhase.COUNTDOWN
                        ) {
                            null
                        } else {
                            "${taps[slot] ?: 0}"
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .alpha(if (isStakePanelVisible) 1f else 0f)
            ) {
                if (isStakePanelVisible) {
                    ControlPanel {
                        SegmentedControl(
                            options = Stake.entries,
                            selected = stake,
                            label = { it.label },
                        ) {
                            stake = it
                            environment.hapticEngine.playFeedback(FeedbackType.LIGHT)
                        }
                        androidx.compose.material3.Text(
                            text = stake.caption,
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                fontFamily = CasterFontFamily,
                                fontSize = 12.sp,
                                color = theme.textSecondary,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }

            when (phase) {
                FrenzyPhase.COUNTDOWN -> androidx.compose.material3.Text(
                    text = "$countdownValue",
                    style = TextStyle(
                        fontFamily = CasterFontFamily,
                        fontSize = 84.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.accent,
                    ),
                )

                FrenzyPhase.TAPPING -> androidx.compose.material3.Text(
                    text = String.format("%.1f", timeRemaining),
                    style = TextStyle(
                        fontFamily = CasterFontFamily,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        color = if (timeRemaining < 1.5f) theme.danger else theme.textPrimary,
                    ),
                )

                else -> Spacer(Modifier.height(1.dp))
            }

            Spacer(Modifier.weight(1f))

            if (phase == FrenzyPhase.REVEALING || phase == FrenzyPhase.FINISHED) {
                ResultTable(Modifier.padding(horizontal = 16.dp)) {
                    for (row in oddsRows) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(theme.playerColor(row.slot))
                            )
                            androidx.compose.material3.Text(
                                text = "Player ${row.slot + 1}",
                                style = TextStyle(fontFamily = CasterFontFamily, fontSize = 14.sp, color = theme.textPrimary),
                            )
                            androidx.compose.material3.Text(
                                text = "${row.taps} taps",
                                modifier = Modifier.weight(1f),
                                style = TextStyle(fontFamily = CasterFontFamily, fontSize = 12.sp, color = theme.textSecondary),
                            )
                            androidx.compose.material3.Text(
                                text = "${(row.share * 100).roundToInt()}%",
                                style = TextStyle(
                                    fontFamily = CasterFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.slot == pickedSlot) {
                                        outcomeTint
                                    } else {
                                        theme.textSecondary
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            StatusLine(text = statusText, emphasis = statusTint)

            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
            ) {
                when (phase) {
                    FrenzyPhase.CLAIMING -> Unit
                    FrenzyPhase.COUNTDOWN, FrenzyPhase.TAPPING, FrenzyPhase.REVEALING ->
                        SecondaryButton(title = "Reset") { resetRound() }

                    FrenzyPhase.FINISHED -> PrimaryButton(title = "Play Again") { resetRound() }
                }
            }
        }
    }
}
