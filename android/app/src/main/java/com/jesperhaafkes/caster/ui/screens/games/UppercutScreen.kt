package com.jesperhaafkes.caster.ui.screens.games

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.domain.GameTuning
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.LocalGameState
import com.jesperhaafkes.caster.LocalRosterStore
import com.jesperhaafkes.caster.domain.GameMode
import com.jesperhaafkes.caster.touch.TouchArena
import com.jesperhaafkes.caster.ui.audio.Tone
import com.jesperhaafkes.caster.ui.components.KeepScreenOn
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.PositionedFingerRing
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.components.ResultTable
import com.jesperhaafkes.caster.ui.components.SecondaryButton
import com.jesperhaafkes.caster.ui.components.StatusLine
import com.jesperhaafkes.caster.ui.components.TouchSurface
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.screens.RosterBarAction
import com.jesperhaafkes.caster.ui.screens.RosterDialog
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class UppercutPhase { GATHERING, ARMED, CUED, FINISHED }

/**
 * Everyone who took part, fastest first. A player who never lifted sorts to the
 * back — they are slower than any real reaction.
 */
private data class RankEntry(val slot: Int, val timeMs: Long?, val rank: Int = 0)

/**
 * Everyone holds a finger down. The light in the middle flips and a tone fires;
 * lift as fast as you can. Fastest wins, slowest loses, and lifting before the
 * cue ends the round on the spot.
 *
 * Reaction times come from the pointer event's own timestamp, not from a clock
 * read inside a callback — otherwise the game is partly timing the main thread.
 */
@Composable
fun UppercutScreen(onBack: () -> Unit) {
    val theme = LocalTheme.current
    val environment = LocalAppEnvironment.current
    val gameState = LocalGameState.current
    val rosterStore = LocalRosterStore.current
    val scope = rememberCoroutineScope()

    val arena = remember { TouchArena() }
    var phase by remember { mutableStateOf(UppercutPhase.GATHERING) }
    val roundSlots = remember { mutableStateListOf<Int>() }
    val reactions = remember { mutableStateMapOf<Int, Long>() }
    var cueTimestamp by remember { mutableStateOf<Long?>(null) }
    var falseStartSlot by remember { mutableStateOf<Int?>(null) }
    var armJob by remember { mutableStateOf<Job?>(null) }
    var cueJob by remember { mutableStateOf<Job?>(null) }
    var timeoutJob by remember { mutableStateOf<Job?>(null) }
    var isRosterShown by remember { mutableStateOf(false) }

    /** How long everybody has to be settled before the round arms itself. */
    val settleDurationMs = GameTuning.Uppercut.SETTLE_DURATION_MS

    /** Nobody gets to wait forever after the cue. */
    val reactionWindowMs = 3_000L

    val names = rosterStore.names
    // Seats are handed out by where a finger lands, so the roster is only a
    // source of names here — there is no setup step to get through first.
    LaunchedEffect(names) { gameState.adoptRoster(names) }

    fun finish() {
        if (phase != UppercutPhase.CUED) return
        timeoutJob?.cancel()
        timeoutJob = null
        phase = UppercutPhase.FINISHED
        environment.cue(FeedbackType.HEAVY, Tone.REVEAL)
    }

    fun fireCue() {
        if (phase != UppercutPhase.ARMED) return
        // Stamped from the same clock as the pointer events, so the subtraction
        // in the lift handler is apples to apples.
        cueTimestamp = SystemClock.uptimeMillis()
        phase = UppercutPhase.CUED
        environment.cue(FeedbackType.HEAVY, Tone.CUE)

        timeoutJob = scope.launch {
            delay(reactionWindowMs)
            finish()
        }
    }

    fun arm() {
        if (phase != UppercutPhase.GATHERING || arena.activeCount < 2) return

        roundSlots.clear()
        roundSlots.addAll(arena.occupiedSlots.sorted())
        reactions.clear()
        falseStartSlot = null
        cueTimestamp = null
        // No latecomers once the fuse is lit.
        arena.acceptsNewSlots = false
        phase = UppercutPhase.ARMED

        val delayMs = Random.nextLong(2_000L, 6_500L)
        cueJob = scope.launch {
            delay(delayMs)
            fireCue()
        }
    }

    /**
     * Any change to the table restarts the settle timer, so the round only arms
     * once the hands have stopped moving.
     */
    fun scheduleArm() {
        armJob?.cancel()
        armJob = null
        if (phase != UppercutPhase.GATHERING || arena.activeCount < 2) return

        armJob = scope.launch {
            delay(settleDurationMs)
            arm()
        }
    }

    fun teardown() {
        armJob?.cancel()
        cueJob?.cancel()
        timeoutJob?.cancel()
        armJob = null
        cueJob = null
        timeoutJob = null
        arena.reset()
    }

    fun resetRound() {
        teardown()
        roundSlots.clear()
        reactions.clear()
        falseStartSlot = null
        cueTimestamp = null
        phase = UppercutPhase.GATHERING
    }

    DisposableEffect(arena) {
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(90.dp)
        arena.reset()

        arena.onBegan = {
            if (phase == UppercutPhase.GATHERING) {
                environment.cue(FeedbackType.LIGHT, Tone.PLACE)
                scheduleArm()
            }
        }

        arena.onEnded = { finger ->
            when (phase) {
                UppercutPhase.GATHERING -> scheduleArm()

                UppercutPhase.ARMED -> {
                    cueJob?.cancel()
                    cueJob = null
                    falseStartSlot = finger.slot
                    phase = UppercutPhase.FINISHED
                    environment.cue(FeedbackType.HEAVY, Tone.MISS)
                }

                UppercutPhase.CUED -> {
                    val cued = cueTimestamp
                    if (cued != null &&
                        finger.slot in roundSlots &&
                        reactions[finger.slot] == null
                    ) {
                        reactions[finger.slot] = maxOf(0L, finger.endTime - cued)
                        environment.cue(FeedbackType.LIGHT, Tone.PIP)
                        if (reactions.size >= roundSlots.size) finish()
                    }
                }

                UppercutPhase.FINISHED -> Unit
            }
        }

        onDispose {
            arena.onBegan = null
            arena.onEnded = null
            teardown()
        }
    }

    val ranking = remember(roundSlots.toList(), reactions.toMap()) {
        roundSlots
            .map { RankEntry(slot = it, timeMs = reactions[it]) }
            .sortedWith(
                compareBy(
                    { it.timeMs == null },
                    { it.timeMs ?: Long.MAX_VALUE },
                    { it.slot },
                )
            )
            .mapIndexed { position, entry -> entry.copy(rank = position + 1) }
    }

    val fastestSlot = if (phase == UppercutPhase.FINISHED && falseStartSlot == null) {
        ranking.firstOrNull()?.slot
    } else {
        null
    }

    val slowestSlot = when {
        phase != UppercutPhase.FINISHED -> falseStartSlot
        falseStartSlot != null -> falseStartSlot
        ranking.size > 1 -> ranking.lastOrNull()?.slot
        else -> null
    }

    fun resultTint(slot: Int): Color = when (slot) {
        falseStartSlot -> theme.danger
        fastestSlot -> theme.success
        slowestSlot -> theme.danger
        else -> theme.textSecondary
    }

    val lightColor = when (phase) {
        // Not surfaceRaised: that is near-white in light mode, and this circle
        // carries white glyphs on top of it.
        UppercutPhase.GATHERING -> theme.textSecondary
        UppercutPhase.ARMED -> theme.danger
        UppercutPhase.CUED -> theme.success
        UppercutPhase.FINISHED -> if (falseStartSlot == null) theme.accent else theme.danger
    }

    val lightGlyph = when (phase) {
        UppercutPhase.GATHERING -> "👆"
        UppercutPhase.ARMED -> "⏳"
        UppercutPhase.CUED -> "⚡"
        UppercutPhase.FINISHED -> if (falseStartSlot == null) "🏁" else "✕"
    }

    val statusText = when (phase) {
        UppercutPhase.GATHERING -> when (arena.activeCount) {
            0 -> "Everyone hold a finger down"
            1 -> "One finger down — needs at least two"
            else -> "Hold still…"
        }

        UppercutPhase.ARMED -> "Wait for it. Lift early and you lose."
        UppercutPhase.CUED -> "LIFT!"
        UppercutPhase.FINISHED -> {
            val early = falseStartSlot
            when {
                early != null -> "${gameState.nameForSlot(early)} went early and loses."
                fastestSlot == null -> "Nobody reacted."
                slowestSlot != null && slowestSlot != fastestSlot ->
                    "${gameState.nameForSlot(fastestSlot)} wins. " +
                        "${gameState.nameForSlot(slowestSlot)} loses."

                else -> "${gameState.nameForSlot(fastestSlot)} wins."
            }
        }
    }

    val statusTint = when (phase) {
        UppercutPhase.CUED -> theme.success
        UppercutPhase.FINISHED -> if (falseStartSlot == null) theme.textPrimary else theme.danger
        else -> null
    }

    KeepScreenOn()

    CasterScreen(
        title = GameMode.UPPERCUT.title,
        onBack = onBack,
        actions = { RosterBarAction { isRosterShown = true } },
    ) {
        TouchSurface(arena) {
            CentreLight(
                color = lightColor,
                glyph = lightGlyph,
                isCued = phase == UppercutPhase.CUED,
                modifier = Modifier.align(Alignment.Center),
            )

            if (phase == UppercutPhase.GATHERING) {
                for (finger in arena.activeFingers) {
                    PositionedFingerRing(
                        location = finger.location,
                        color = theme.playerColor(finger.slot),
                        diameter = 74.dp,
                    )
                }
            } else {
                for (slot in roundSlots) {
                    val anchor = arena.anchor(slot) ?: continue
                    val ringColor = when {
                        slot == falseStartSlot -> theme.danger
                        phase != UppercutPhase.FINISHED ->
                            if (reactions[slot] == null) theme.playerColor(slot) else theme.success

                        else -> resultTint(slot)
                    }
                    PositionedFingerRing(
                        location = anchor,
                        color = ringColor,
                        diameter = 74.dp,
                        isHighlighted = slot == fastestSlot,
                        badge = when {
                            slot == falseStartSlot -> "!"
                            reactions[slot] != null -> "${reactions[slot]}"
                            else -> null
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.weight(1f))

            if (phase == UppercutPhase.FINISHED) {
                ResultTable(Modifier.padding(horizontal = 16.dp)) {
                    for (entry in ranking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            androidx.compose.material3.Text(
                                text = "${entry.rank}",
                                modifier = Modifier.width(18.dp),
                                style = TextStyle(
                                    fontFamily = CasterFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textSecondary,
                                ),
                            )
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(theme.playerColor(entry.slot))
                            )
                            androidx.compose.material3.Text(
                                text = gameState.nameForSlot(entry.slot),
                                modifier = Modifier.weight(1f),
                                style = TextStyle(fontFamily = CasterFontFamily, fontSize = 14.sp, color = theme.textPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            androidx.compose.material3.Text(
                                text = entry.timeMs?.let { "$it ms" } ?: "no lift",
                                style = TextStyle(
                                    fontFamily = CasterFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = resultTint(entry.slot),
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
                    UppercutPhase.GATHERING -> Unit
                    UppercutPhase.ARMED, UppercutPhase.CUED ->
                        SecondaryButton(title = "Reset") { resetRound() }

                    UppercutPhase.FINISHED ->
                        PrimaryButton(title = "Play Again") { resetRound() }
                }
            }
        }
    }

    if (isRosterShown) {
        RosterDialog { isRosterShown = false }
    }
}

@Composable
private fun CentreLight(
    color: Color,
    glyph: String,
    isCued: Boolean,
    modifier: Modifier = Modifier,
) {
    // The one moment this whole game is about. Swift eases the flip at
    // UppercutView.swift:103 (.easeOut, 0.08s); without it the disc changes
    // colour between two frames and the cue reads as a glitch.
    val lit by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "centre-light",
    )
    Box(modifier.size(190.dp), contentAlignment = Alignment.Center) {
        // Modifier.blur needs RenderEffect, so it is a silent no-op below
        // Android 12 — and minSdk here is 26. Left as-is, every phone on
        // 8.0-11 gets a hard-edged disc where the bloom should be, which is
        // the one moment of this game that has to read at a glance. On those
        // devices the glow is painted as a radial gradient instead.
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Canvas(
            Modifier
                .fillMaxSize()
                .then(if (canBlur) Modifier.blur(if (isCued) 18.dp else 0.dp) else Modifier)
        ) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            val haloAlpha = if (isCued) 0.85f else 0.22f
            if (canBlur) {
                drawCircle(
                    color = lit.copy(alpha = haloAlpha),
                    radius = radius,
                    center = centre,
                )
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        // Solid out to the edge of the inner disc, then faded —
                        // roughly what an 18dp blur of a hard circle looks like.
                        0.0f to lit.copy(alpha = haloAlpha),
                        0.62f to lit.copy(alpha = haloAlpha),
                        1.0f to Color.Transparent,
                        center = centre,
                        radius = radius,
                    ),
                    radius = radius,
                    center = centre,
                )
            }
        }
        Canvas(Modifier.size(150.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(lit, radius = size.minDimension / 2f, center = centre)
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = size.minDimension / 2f - 1.5.dp.toPx(),
                center = centre,
                style = Stroke(3.dp.toPx()),
            )
        }
        androidx.compose.material3.Text(text = glyph, style = TextStyle(fontFamily = CasterFontFamily, fontSize = 40.sp))
    }
}
