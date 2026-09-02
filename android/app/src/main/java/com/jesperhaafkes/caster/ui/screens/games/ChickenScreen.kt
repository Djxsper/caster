package com.jesperhaafkes.caster.ui.screens.games

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.domain.GameMode
import com.jesperhaafkes.caster.touch.TouchArena
import com.jesperhaafkes.caster.ui.audio.Tone
import com.jesperhaafkes.caster.ui.components.KeepScreenOn
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.EmptyPlayHint
import com.jesperhaafkes.caster.ui.components.PositionedFingerRing
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.components.SecondaryButton
import com.jesperhaafkes.caster.ui.components.StatusLine
import com.jesperhaafkes.caster.ui.components.TouchSurface
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class ChickenPhase { GATHERING, PLAYING, FINISHED }

/**
 * Everyone holds a finger down. One circle lights up at a time; let go before
 * it goes out and you are safe and out of the game. Too slow and you stay in.
 * The last one still holding loses.
 *
 * The window opens at 100 ms — quicker than anyone can actually move — and
 * widens by 50 ms every time a flash is missed. The round therefore calibrates
 * itself to whoever is playing: it keeps easing until it crosses the fastest
 * pair of reflexes in the room, that player escapes, and it goes on easing past
 * everyone else in turn. Whoever it reaches last is the slowest, and they are
 * the one left holding.
 */
@Composable
fun ChickenScreen(onBack: () -> Unit) {
    val theme = LocalTheme.current
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()

    val arena = remember { TouchArena() }
    var phase by remember { mutableStateOf(ChickenPhase.GATHERING) }
    val activeSlots = remember { mutableStateListOf<Int>() }
    val safeSlots = remember { mutableStateListOf<Int>() }
    var flashSlot by remember { mutableStateOf<Int?>(null) }

    /** Kept past the end of a flash so the next pick can avoid an immediate repeat. */
    var lastFlashSlot by remember { mutableStateOf<Int?>(null) }
    val flashProgress = remember { Animatable(0f) }
    var liftedSlot by remember { mutableStateOf<Int?>(null) }

    /**
     * When that lift happened, from the pointer event's own timestamp. At a
     * 100 ms window, noticing a lift in a callback is far too coarse to judge
     * it by.
     */
    var liftedAt by remember { mutableStateOf<Long?>(null) }
    var loserSlot by remember { mutableStateOf<Int?>(null) }
    var waitingOnSlot by remember { mutableStateOf<Int?>(null) }
    var reactionWindowMs by remember { mutableDoubleStateOf(100.0) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var roundJob by remember { mutableStateOf<Job?>(null) }

    val settleDurationMs = 1_500L

    /**
     * Deliberately below human reaction time. The opening flashes are meant to
     * be unmissable in the bad sense, and the tension comes from watching the
     * window creep up towards something anyone can actually hit.
     */
    val startWindowMs = 100.0
    val windowStepMs = 50.0

    /** Nothing needs a window this wide; it only stops a stuck round crawling. */
    val maxWindowMs = 2_500.0

    /**
     * Slack for the trip from glass to callback, so a lift that really did land
     * inside the window is not thrown out by delivery lag.
     */
    val latencyGraceMs = 90L

    fun qualifies(slot: Int, windowMs: Double, flashedAt: Long): Boolean {
        if (liftedSlot != slot) return false
        val lifted = liftedAt ?: return false
        val elapsed = (lifted - flashedAt).toDouble()
        // A lift stamped before the flash is a hand that was already leaving,
        // not a reaction to anything.
        return elapsed >= 0 && elapsed <= windowMs
    }

    /**
     * Waits a little past the window before giving up, because a lift can be
     * delivered after its own timestamp. What counts is when the touch says it
     * happened, never when this loop got round to seeing it.
     *
     * @return true when the flashed slot lifted before the window closed.
     */
    suspend fun waitForLift(slot: Int, windowMs: Double, flashedAt: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + windowMs.toLong() + latencyGraceMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (qualifies(slot, windowMs, flashedAt)) return true
            delay(8)
        }
        return qualifies(slot, windowMs, flashedAt)
    }

    /**
     * Only a slot with a finger actually on it can be flashed. Someone who has
     * drifted off gets called out instead of being handed a free elimination.
     */
    suspend fun nextTarget(): Int? {
        while (coroutineContext.isActive) {
            val candidates = activeSlots.filter { arena.isSlotHeld(it) }
            val last = lastFlashSlot
            if (last != null && candidates.size > 1) {
                val others = candidates.filter { it != last }
                others.randomOrNull()?.let { return it }
            }
            candidates.randomOrNull()?.let { return it }

            // Nobody is holding: name someone and wait for a hand to come back.
            waitingOnSlot = activeSlots.firstOrNull()
            delay(200)
        }
        return null
    }

    fun markSafe(slot: Int) {
        activeSlots.remove(slot)
        safeSlots.add(slot)
        // Retiring drops the anchor, so their hand leaving for good cannot come
        // back and claim someone else's circle.
        arena.retire(slot)
        environment.cue(FeedbackType.MEDIUM, Tone.SAFE)
    }

    fun finish() {
        flashSlot = null
        waitingOnSlot = null
        loserSlot = activeSlots.firstOrNull()
        phase = ChickenPhase.FINISHED
        environment.cue(FeedbackType.HEAVY, Tone.BOOM)
    }

    suspend fun runFlashes() {
        while (phase == ChickenPhase.PLAYING && activeSlots.size > 1 && coroutineContext.isActive) {
            delay(Random.nextLong(550, 1_500))

            val target = nextTarget() ?: return
            waitingOnSlot = null

            val window = reactionWindowMs
            liftedSlot = null
            liftedAt = null
            flashSlot = target
            lastFlashSlot = target
            // Same clock as the pointer events, so the comparison in `qualifies`
            // measures the player and not the main thread.
            val flashedAt = SystemClock.uptimeMillis()
            environment.cue(FeedbackType.MEDIUM, Tone.PIP)

            val arc = scope.launch {
                flashProgress.snapTo(0f)
                flashProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(window.toInt(), easing = LinearEasing),
                )
            }
            val survived = waitForLift(target, window, flashedAt)
            arc.cancel()
            flashProgress.snapTo(0f)
            flashSlot = null

            if (survived) {
                markSafe(target)
            } else {
                // Nobody could make that one. Give the next attempt more room.
                reactionWindowMs = minOf(maxWindowMs, reactionWindowMs + windowStepMs)
                environment.cue(FeedbackType.HEAVY, Tone.MISS)
            }
        }

        // The loop above also exits because it was cancelled, and a cancelled
        // round must not declare a loser. Swift guards the same way at
        // ChickenView.swift:312.
        if (coroutineContext.isActive && phase == ChickenPhase.PLAYING) finish()
    }

    fun beginRound() {
        if (phase != ChickenPhase.GATHERING || arena.activeCount < 2) return

        activeSlots.clear()
        activeSlots.addAll(arena.occupiedSlots.sorted())
        safeSlots.clear()
        loserSlot = null
        flashSlot = null
        lastFlashSlot = null
        liftedSlot = null
        liftedAt = null
        waitingOnSlot = null
        reactionWindowMs = startWindowMs
        arena.acceptsNewSlots = false
        phase = ChickenPhase.PLAYING
        environment.cue(FeedbackType.HEAVY, Tone.PIP)

        roundJob = scope.launch { runFlashes() }
    }

    fun scheduleStart() {
        settleJob?.cancel()
        settleJob = null
        if (phase != ChickenPhase.GATHERING || arena.activeCount < 2) return

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
        phase = ChickenPhase.GATHERING
        activeSlots.clear()
        safeSlots.clear()
        flashSlot = null
        lastFlashSlot = null
        liftedSlot = null
        liftedAt = null
        loserSlot = null
        waitingOnSlot = null
        reactionWindowMs = startWindowMs
        scope.launch { flashProgress.snapTo(0f) }
    }

    DisposableEffect(arena) {
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(90.dp)
        arena.reset()

        arena.onBegan = {
            if (phase == ChickenPhase.GATHERING) {
                environment.cue(FeedbackType.LIGHT, Tone.PLACE)
                scheduleStart()
            }
        }

        arena.onEnded = { finger ->
            when (phase) {
                ChickenPhase.GATHERING -> scheduleStart()

                // Recorded rather than acted on: the flash loop is what decides
                // whether this lift landed inside the window.
                ChickenPhase.PLAYING -> {
                    liftedSlot = finger.slot
                    liftedAt = finger.endTime
                }

                ChickenPhase.FINISHED -> Unit
            }
        }

        onDispose {
            arena.onBegan = null
            arena.onEnded = null
            teardown()
        }
    }

    val statusText = when (phase) {
        ChickenPhase.GATHERING -> when (arena.activeCount) {
            0 -> "Everyone hold a finger down"
            1 -> "One finger down — needs at least two"
            else -> "Hold still…"
        }

        ChickenPhase.PLAYING -> {
            val waiting = waitingOnSlot
            when {
                waiting != null -> "Player ${waiting + 1}, finger back on the glass"
                flashSlot != null -> "Let go!"
                else -> "${activeSlots.size} still in. Hold."
            }
        }

        ChickenPhase.FINISHED -> loserSlot
            ?.let { "Player ${it + 1} is last in and loses." }
            ?: "Round complete."
    }

    val statusTint = when (phase) {
        ChickenPhase.FINISHED -> theme.danger
        ChickenPhase.PLAYING -> if (flashSlot == null) null else theme.warning
        else -> null
    }

    KeepScreenOn()

    CasterScreen(title = GameMode.CHICKEN.title, onBack = onBack) {
        TouchSurface(arena) {
            if (phase == ChickenPhase.GATHERING && arena.activeCount == 0) {
                EmptyPlayHint(
                    glyph = "🔥",
                    title = "Hold on",
                    detail = "Everyone puts a finger anywhere on the screen and keeps it there.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (phase == ChickenPhase.GATHERING) {
                for (finger in arena.activeFingers) {
                    PositionedFingerRing(
                        location = finger.location,
                        color = theme.playerColor(finger.slot),
                        diameter = 78.dp,
                    )
                }
            } else {
                for (slot in activeSlots) {
                    val anchor = arena.anchor(slot) ?: continue
                    val ringColor = when {
                        slot == loserSlot -> theme.danger
                        slot == flashSlot -> theme.warning
                        else -> theme.playerColor(slot)
                    }
                    PositionedFingerRing(
                        location = anchor,
                        color = ringColor,
                        diameter = 78.dp,
                        progress = if (slot == flashSlot) flashProgress.value else 0f,
                        isHighlighted = slot == flashSlot,
                        isDimmed = waitingOnSlot == slot,
                        badge = if (slot == loserSlot) "✕" else null,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (phase == ChickenPhase.PLAYING) {
                WindowChip(windowMs = reactionWindowMs, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.weight(1f))

            if (safeSlots.isNotEmpty()) {
                SafeStrip(safeSlots, Modifier.padding(horizontal = 16.dp))
            }

            StatusLine(text = statusText, emphasis = statusTint)

            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
            ) {
                when (phase) {
                    ChickenPhase.GATHERING -> Unit
                    ChickenPhase.PLAYING -> SecondaryButton(title = "Reset") { resetRound() }
                    ChickenPhase.FINISHED -> PrimaryButton(title = "Play Again") { resetRound() }
                }
            }
        }
    }
}

/** Shows the window widening, so the ramp is legible rather than a mystery. */
@Composable
private fun WindowChip(windowMs: Double, modifier: Modifier = Modifier) {
    val theme = LocalTheme.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(theme.surfaceRaised)
            .padding(vertical = 7.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.Text(text = "⏱", style = TextStyle(fontSize = 12.sp))
        androidx.compose.material3.Text(
            text = "${windowMs.roundToInt()} ms to let go",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textSecondary,
            ),
        )
    }
}

@Composable
private fun SafeStrip(slots: List<Int>, modifier: Modifier = Modifier) {
    val theme = LocalTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surfaceRaised)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Text(
            text = "Out safe",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textSecondary,
            ),
        )
        for (slot in slots) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(theme.playerColor(slot)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    text = "${slot + 1}",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                )
            }
        }
    }
}
