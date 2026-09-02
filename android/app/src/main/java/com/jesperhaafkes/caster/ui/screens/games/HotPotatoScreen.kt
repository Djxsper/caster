package com.jesperhaafkes.caster.ui.screens.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.LocalGameState
import com.jesperhaafkes.caster.LocalRosterStore
import com.jesperhaafkes.caster.domain.GameMode
import com.jesperhaafkes.caster.ui.audio.Tone
import com.jesperhaafkes.caster.ui.components.KeepScreenOn
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.components.SecondaryButton
import com.jesperhaafkes.caster.ui.components.StatusLine
import com.jesperhaafkes.caster.ui.components.ToggleRow
import com.jesperhaafkes.caster.ui.components.silentTap
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.screens.RosterBarAction
import com.jesperhaafkes.caster.ui.screens.RosterDialog
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class PotatoPhase { IDLE, RUNNING, EXPLODED }

/**
 * A hidden fuse burns while the phone goes round the table. Tap to pass it on.
 * Whoever is holding it when it goes off loses.
 *
 * The tick speeds up, because a fuse that ticks flat is not tense — but the
 * acceleration is timed against a *decoy* length drawn separately from the real
 * fuse, so a frantic tick is not a reliable signal that the end is near.
 * Sometimes it blows while the ticking is still lazy.
 */
@Composable
fun HotPotatoScreen(onBack: () -> Unit) {
    val theme = LocalTheme.current
    val environment = LocalAppEnvironment.current
    val gameState = LocalGameState.current
    val rosterStore = LocalRosterStore.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(PotatoPhase.IDLE) }
    var holderIndex by remember { mutableIntStateOf(0) }
    var loserIndex by remember { mutableStateOf<Int?>(null) }
    var fuseJob by remember { mutableStateOf<Job?>(null) }
    var pulse by remember { mutableStateOf(false) }
    var passCount by remember { mutableIntStateOf(0) }

    /**
     * Real hot potato does not go round the circle in turn — you throw it at
     * whoever is not looking. Off by default so the fair rotation stays there.
     */
    var isRandomOrder by remember { mutableStateOf(false) }
    var isRosterShown by remember { mutableStateOf(false) }

    val players = gameState.players
    val names = rosterStore.names

    // Pulled from the store rather than trusted from the setup screen, so the
    // names are right however this screen was reached. Editing the roster
    // mid-session — someone arrives, someone leaves — lands here too, without
    // ending the round.
    LaunchedEffect(names) {
        gameState.adoptRoster(names)
        // A roster that shrank can leave the potato with nobody holding it.
        val count = gameState.players.size
        if (count > 0 && holderIndex >= count) holderIndex = count - 1
        loserIndex?.let { if (it >= count) loserIndex = null }
    }

    DisposableEffect(Unit) {
        onDispose {
            fuseJob?.cancel()
            fuseJob = null
        }
    }

    fun nameAt(index: Int): String = players.getOrNull(index)?.name ?: "Someone"

    /**
     * Uniform over everyone *except* whoever is holding it: handing the potato
     * back to yourself is not a pass.
     */
    fun randomHolder(current: Int, count: Int): Int {
        if (count <= 1) return current
        var next = Random.nextInt(count - 1)
        if (next >= current) next += 1
        return next
    }

    fun pass() {
        val count = players.size
        if (count == 0) return
        holderIndex = if (isRandomOrder) {
            randomHolder(holderIndex, count)
        } else {
            (holderIndex + 1) % count
        }
        passCount += 1
        environment.cue(FeedbackType.MEDIUM, Tone.PLACE)
    }

    fun explode() {
        fuseJob = null
        loserIndex = holderIndex
        phase = PotatoPhase.EXPLODED
        environment.cue(FeedbackType.HEAVY, Tone.BOOM)
        players.getOrNull(holderIndex)?.let { gameState.recordLoss(it) }
    }

    fun startRound() {
        if (players.isEmpty()) return

        fuseJob?.cancel()
        loserIndex = null
        passCount = 0
        holderIndex = Random.nextInt(players.size)
        phase = PotatoPhase.RUNNING
        environment.cue(FeedbackType.HEAVY, Tone.PIP)

        // Two independent draws: what actually ends the round, and what the
        // ticking pretends to be counting down.
        val fuse = Random.nextDouble(12.0, 32.0)
        val decoyLength = fuse * Random.nextDouble(0.72, 1.55)

        fuseJob = scope.launch {
            val started = System.nanoTime()
            while (true) {
                val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
                if (elapsed >= fuse) break

                pulse = !pulse
                environment.cue(FeedbackType.LIGHT, Tone.TICK)

                val decoyProgress = minOf(1.0, elapsed / decoyLength)
                val interval = 0.62 - 0.5 * decoyProgress
                // Never sleep past the fuse, or the bang lands late.
                val wait = minOf(interval, maxOf(0.02, fuse - elapsed))
                delay((wait * 1000).toLong())
            }
            // A cancelled fuse must not go off: cancelRound() can land between
            // the last delay and here, and the bang would then fire on a round
            // that no longer exists.
            if (isActive) explode()
        }
    }

    fun cancelRound() {
        fuseJob?.cancel()
        fuseJob = null
        phase = PotatoPhase.IDLE
        loserIndex = null
        passCount = 0
    }

    val holderColor = when (phase) {
        PotatoPhase.EXPLODED -> theme.danger
        PotatoPhase.IDLE -> theme.accent
        PotatoPhase.RUNNING -> theme.playerColor(holderIndex)
    }

    val headlineName = when {
        players.isEmpty() -> "No players"
        phase == PotatoPhase.IDLE -> "Ready"
        phase == PotatoPhase.RUNNING -> nameAt(holderIndex)
        else -> nameAt(loserIndex ?: holderIndex)
    }

    val statusText = when {
        players.isEmpty() -> "Tap the people button up top to add some players."
        phase == PotatoPhase.IDLE -> "Pass the phone around. Do not be holding it at the end."
        phase == PotatoPhase.RUNNING ->
            if (isRandomOrder) "Tap to throw it at someone" else "Tap anywhere to pass it on"

        else -> "Boom. ${nameAt(loserIndex ?: holderIndex)} loses."
    }

    // The screen itself reddens as the round runs, which is atmosphere only —
    // it tracks the pass count, never the real fuse.
    val heat = minOf(0.16f, passCount * 0.012f)

    KeepScreenOn()

    CasterScreen(
        title = GameMode.HOT_POTATO.title,
        onBack = onBack,
        actions = { RosterBarAction { isRosterShown = true } },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (phase == PotatoPhase.RUNNING) {
                        theme.danger.copy(alpha = heat).compositeOver(theme.background)
                    } else {
                        theme.background
                    }
                )
                .silentTap(enabled = phase == PotatoPhase.RUNNING) { pass() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.weight(1f))

                Potato(
                    color = holderColor,
                    headline = headlineName,
                    isExploded = phase == PotatoPhase.EXPLODED,
                    pulse = pulse,
                )

                StatusLine(
                    text = statusText,
                    emphasis = if (phase == PotatoPhase.EXPLODED) theme.danger else null,
                )

                Spacer(Modifier.weight(1f))

                if (phase != PotatoPhase.RUNNING) {
                    ToggleRow(
                        title = "Random order",
                        caption = if (isRandomOrder) {
                            "Throw it at anyone"
                        } else {
                            "Round the circle in turn"
                        },
                        checked = isRandomOrder,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onCheckedChange = { isRandomOrder = it },
                    )
                }

                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp)
                ) {
                    when (phase) {
                        PotatoPhase.IDLE -> PrimaryButton(
                            title = "Light the Fuse",
                            isEnabled = players.isNotEmpty(),
                            onClick = { startRound() },
                        )

                        PotatoPhase.RUNNING -> SecondaryButton(title = "Stop") { cancelRound() }

                        PotatoPhase.EXPLODED -> PrimaryButton(title = "Play Again") { startRound() }
                    }
                }
            }
        }
    }

    if (isRosterShown) {
        RosterDialog { isRosterShown = false }
    }
}

@Composable
private fun Potato(color: Color, headline: String, isExploded: Boolean, pulse: Boolean) {
    val theme = LocalTheme.current
    val ringScale by animateFloatAsState(
        targetValue = if (pulse) 1.045f else 1f,
        animationSpec = tween(120),
        label = "potato-pulse",
    )
    val tint by animateColorAsState(
        targetValue = color,
        animationSpec = tween(250),
        label = "potato-tint",
    )

    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(tint.copy(alpha = 0.18f), radius = size.minDimension / 2f, center = centre)
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .scale(ringScale)
        ) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = tint,
                radius = size.minDimension / 2f - 3.dp.toPx(),
                center = centre,
                style = Stroke(6.dp.toPx()),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Text(
                text = if (isExploded) "💥" else "🔥",
                style = TextStyle(fontFamily = CasterFontFamily, fontSize = 38.sp),
            )
            androidx.compose.material3.Text(
                text = headline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                style = TextStyle(
                    fontFamily = CasterFontFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.textPrimary,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
