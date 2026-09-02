package com.jesperhaafkes.caster.ui.screens.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.domain.DrawEngine
import com.jesperhaafkes.caster.domain.GameMode
import com.jesperhaafkes.caster.touch.ArenaFinger
import com.jesperhaafkes.caster.touch.TouchArena
import com.jesperhaafkes.caster.ui.audio.Tone
import com.jesperhaafkes.caster.ui.components.KeepScreenOn
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.ControlPanel
import com.jesperhaafkes.caster.ui.components.EmptyPlayHint
import com.jesperhaafkes.caster.ui.components.PositionedFingerRing
import com.jesperhaafkes.caster.ui.components.SegmentedControl
import com.jesperhaafkes.caster.ui.components.StatusLine
import com.jesperhaafkes.caster.ui.components.StepperRow
import com.jesperhaafkes.caster.ui.components.TouchSurface
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import com.jesperhaafkes.caster.ui.theme.PlayerPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PickStyle(val label: String) {
    PICK("Pick"),
    TEAMS("Teams"),
    ORDER("Order"),
}

/**
 * A frozen copy of the table at the moment the draw resolved. Frozen on
 * purpose: the result has to stay on screen while people lift off, and reading
 * live touches would erase it finger by finger.
 */
private data class Resolution(
    val style: PickStyle,
    val fingers: List<ArenaFinger>,
    val chosen: Set<Int> = emptySet(),
    val teams: Map<Int, Int> = emptyMap(),
    val order: Map<Int, Int> = emptyMap(),
    val headline: String,
)

/**
 * Everyone puts a finger on the glass, the rings count down, and the app
 * answers the question. Three answers, the same three the genre settled on:
 * pick somebody, split the room into teams, or put everybody in an order.
 */
@Composable
fun FingerPickerScreen(onBack: () -> Unit) {
    val theme = LocalTheme.current
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()

    val arena = remember { TouchArena() }
    var style by remember { mutableStateOf(PickStyle.PICK) }
    var pickCount by remember { mutableIntStateOf(1) }
    var teamCount by remember { mutableIntStateOf(2) }
    var resolution by remember { mutableStateOf<Resolution?>(null) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    val waitProgress = remember { Animatable(0f) }

    val holdDurationMs = 3_000

    val spinTransition = rememberInfiniteTransition(label = "halo-spin")
    val spin by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo-angle",
    )

    fun isReadyToResolve(): Boolean {
        val count = arena.activeCount
        if (count < 2) return false
        return when (style) {
            PickStyle.PICK -> count > pickCount
            PickStyle.TEAMS -> count >= teamCount
            PickStyle.ORDER -> true
        }
    }

    fun resolve() {
        val fingers = arena.activeFingers
        if (fingers.size < 2) return

        val slots = fingers.map { it.slot }
        val outcome = when (style) {
            PickStyle.PICK -> {
                val winners = DrawEngine.pick(minOf(pickCount, slots.size - 1), slots)
                Resolution(
                    style = PickStyle.PICK,
                    fingers = fingers,
                    chosen = winners.toSet(),
                    headline = if (winners.size == 1) "Picked" else "Picked ${winners.size}",
                )
            }

            PickStyle.TEAMS -> {
                val assignments = DrawEngine.splitIntoTeams(slots, teamCount)
                Resolution(
                    style = PickStyle.TEAMS,
                    fingers = fingers,
                    teams = slots.mapIndexedNotNull { index, slot ->
                        assignments.getOrNull(index)?.let { slot to it }
                    }.toMap(),
                    headline = "$teamCount teams",
                )
            }

            PickStyle.ORDER -> {
                val ranks = DrawEngine.randomOrder(slots.size)
                Resolution(
                    style = PickStyle.ORDER,
                    fingers = fingers,
                    order = slots.mapIndexedNotNull { index, slot ->
                        ranks.getOrNull(index)?.let { slot to it }
                    }.toMap(),
                    headline = "Order set — lift to play again",
                )
            }
        }

        // Stop taking new fingers so a stray touch cannot join a finished draw.
        arena.acceptsNewSlots = false
        countdownJob = null
        scope.launch { waitProgress.snapTo(0f) }
        resolution = outcome
        environment.cue(FeedbackType.HEAVY, Tone.REVEAL)
    }

    /**
     * Any change in the number of fingers restarts the wait, so latecomers are
     * never shut out of a round that is already counting down.
     */
    fun restartCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        scope.launch { waitProgress.snapTo(0f) }

        if (resolution != null || !isReadyToResolve()) return

        countdownJob = scope.launch {
            waitProgress.snapTo(0f)
            // The fill and the hold are driven separately on purpose. A Compose
            // `tween` collapses to nothing when the animator duration scale is
            // off — Developer Options, Accessibility "Remove animations", some
            // battery savers — so timing the hold by the animation would resolve
            // the draw the instant a second finger landed. The ring is
            // decoration; `delay` is the rule. Both are children of this job, so
            // cancelling it stops the pair.
            launch {
                waitProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(holdDurationMs, easing = LinearEasing),
                )
            }
            delay(holdDurationMs.toLong())
            resolve()
        }
    }

    fun clearResolution() {
        resolution = null
        arena.reset()
    }

    DisposableEffect(arena) {
        arena.slotPolicy = TouchArena.SlotPolicy.Sequential
        arena.reset()

        arena.onBegan = {
            environment.cue(FeedbackType.LIGHT, Tone.PLACE)
            restartCountdown()
        }
        arena.onEnded = {
            if (resolution != null) {
                // The answer stays up until the last hand is off the glass.
                if (arena.activeCount == 0) clearResolution()
            } else {
                restartCountdown()
            }
        }

        onDispose {
            countdownJob?.cancel()
            countdownJob = null
            arena.onBegan = null
            arena.onEnded = null
            arena.reset()
        }
    }

    val activeCount = arena.activeCount
    val isControlPanelVisible = activeCount == 0 && resolution == null

    val statusText = resolution?.headline ?: when (activeCount) {
        0 -> "Everyone put a finger on the screen"
        1 -> "One finger down — needs at least two"
        else -> if (isReadyToResolve()) {
            "Hold still…"
        } else {
            when (style) {
                PickStyle.PICK -> "Picking $pickCount needs more than $pickCount fingers"
                PickStyle.TEAMS -> "$teamCount teams needs at least $teamCount fingers"
                PickStyle.ORDER -> "$activeCount fingers down"
            }
        }
    }

    KeepScreenOn()

    CasterScreen(title = GameMode.FINGER_PICKER.title, onBack = onBack) {
        TouchSurface(arena) {
            if (activeCount == 0 && resolution == null) {
                EmptyPlayHint(
                    glyph = "👆",
                    title = "Fingers down",
                    detail = "Two or more, held still for three seconds.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            val frozen = resolution
            if (frozen != null) {
                for (finger in frozen.fingers) {
                    ResolvedRing(finger = finger, resolution = frozen, spin = spin)
                }
            } else {
                for (finger in arena.activeFingers) {
                    PositionedFingerRing(
                        location = finger.location,
                        color = seatColor(finger.slot, activeCount),
                        progress = waitProgress.value,
                        spin = spin,
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(if (isControlPanelVisible) 1f else 0f)
            ) {
                if (isControlPanelVisible) {
                    ControlPanel {
                        SegmentedControl(
                            options = PickStyle.entries,
                            selected = style,
                            label = { it.label },
                        ) { option ->
                            style = option
                            environment.hapticEngine.playFeedback(FeedbackType.LIGHT)
                            restartCountdown()
                        }

                        if (style != PickStyle.ORDER) {
                            val isPick = style == PickStyle.PICK
                            StepperRow(
                                title = if (isPick) {
                                    if (pickCount == 1) "Pick 1 finger" else "Pick $pickCount fingers"
                                } else {
                                    "$teamCount teams"
                                },
                                value = if (isPick) pickCount else teamCount,
                                range = if (isPick) 1..5 else 2..6,
                            ) { next ->
                                if (isPick) pickCount = next else teamCount = next
                                restartCountdown()
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                StatusLine(
                    text = statusText,
                    emphasis = if (resolution == null) null else theme.textPrimary,
                    modifier = Modifier.padding(bottom = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun ResolvedRing(finger: ArenaFinger, resolution: Resolution, spin: Float) {
    val theme = LocalTheme.current
    val slot = finger.slot
    val total = resolution.fingers.size

    when (resolution.style) {
        PickStyle.PICK -> {
            val isChosen = slot in resolution.chosen
            PositionedFingerRing(
                location = finger.location,
                color = seatColor(slot, total),
                progress = if (isChosen) 1f else 0f,
                spin = spin,
                isHighlighted = isChosen,
                isDimmed = !isChosen,
            )
        }

        PickStyle.TEAMS -> {
            val team = resolution.teams[slot] ?: 0
            PositionedFingerRing(
                location = finger.location,
                color = theme.playerColor(team),
                progress = 1f,
                spin = spin,
                isHighlighted = true,
                badge = "${team + 1}",
            )
        }

        PickStyle.ORDER -> {
            val rank = resolution.order[slot] ?: 0
            PositionedFingerRing(
                location = finger.location,
                color = seatColor(slot, total),
                progress = 1f,
                spin = spin,
                isHighlighted = rank == 1,
                badge = "$rank",
            )
        }
    }
}

/**
 * Past the eight fixed seat colours the hues spread out instead, so ten fingers
 * still read as ten different people.
 *
 * [total] is passed in rather than read from the arena: a resolved draw keeps
 * its own count, or the colours would shift under the answer as people lift
 * their fingers off.
 */
private fun seatColor(slot: Int, total: Int): Color =
    PlayerPalette.spread(index = slot, total = maxOf(8, total))
