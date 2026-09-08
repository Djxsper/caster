package com.jesperhaafkes.caster.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.jesperhaafkes.caster.BuildConfig
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.domain.GameMode
import com.jesperhaafkes.caster.domain.Route
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.screens.games.ChickenScreen
import com.jesperhaafkes.caster.ui.screens.games.FingerPickerScreen
import com.jesperhaafkes.caster.ui.screens.games.HotPotatoScreen
import com.jesperhaafkes.caster.ui.screens.games.PinwheelScreen
import com.jesperhaafkes.caster.ui.screens.games.TapFrenzyScreen
import com.jesperhaafkes.caster.ui.screens.games.UppercutScreen
import com.jesperhaafkes.caster.ui.theme.CasterType
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * The launch page and the navigation stack rooted at it.
 *
 * A single list of routes replaces a chain of dialogs presenting dialogs — the
 * same call the iOS build makes with its `NavigationStack` path. One place
 * knows where you are, and the back gesture always means "up one".
 */
@Composable
fun LaunchScreen() {
    val environment = LocalAppEnvironment.current
    val path = remember { mutableStateListOf<Route>() }

    // Popping the stack is what runs a game's teardown. Disabled at the root so
    // back leaves the app as usual rather than trapping anyone on the launch page.
    BackHandler(enabled = path.isNotEmpty()) {
        path.removeAt(path.lastIndex)
    }

    val pop: () -> Unit = { path.removeAt(path.lastIndex) }

    // Screens used to swap with a hard cut, which is the other half of why this
    // build felt less finished than the iOS one — a NavigationStack push is
    // never instantaneous there, and the movement is what tells you which way
    // you went. The stack's depth decides the direction, so going back reverses
    // the same animation rather than playing a second forward one.
    AnimatedContent(
        targetState = path.toList(),
        transitionSpec = {
            val direction = if (targetState.size >= initialState.size) 1 else -1
            val entering = slideInHorizontally(tween(260)) { width -> direction * width / 8 } +
                fadeIn(tween(200))
            val leaving = slideOutHorizontally(tween(260)) { width -> -direction * width / 8 } +
                fadeOut(tween(180))
            // clip = false: a game draws to the edges, and letting the container
            // crop it mid-transition looks like a rendering fault.
            (entering togetherWith leaving).using(SizeTransform(clip = false))
        },
        label = "route",
    ) { stack ->
        when (val route = stack.lastOrNull()) {
            null -> LaunchContent {
                environment.hapticEngine.playFeedback(FeedbackType.MEDIUM)
                path.add(Route.ModeSelect)
            }

            Route.ModeSelect -> ModeSelectScreen(
                onBack = pop,
                onAdvance = { path.add(it) },
                onSettings = { path.add(Route.Settings) },
            )

            Route.PlayerSetup -> PlayerSetupScreen(onBack = pop, onStart = { path.add(it) })

            Route.WheelSetup -> WheelSetupScreen(onBack = pop, onSpin = { path.add(it) })

            Route.Settings -> SettingsScreen(onBack = pop)

            is Route.Game -> GameHost(mode = route.mode, onBack = pop)
        }
    }
}

@Composable
private fun LaunchContent(onBegin: () -> Unit) {
    val theme = LocalTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CasterMark()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Caster", style = CasterType.display.copy(color = theme.textPrimary))
                Text(
                    text = "Ready to play",
                    style = CasterType.subtitle.copy(color = theme.textSecondary),
                )
                TestBuildBadge()
            }

            PrimaryButton(
                title = "Begin",
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .padding(top = 32.dp),
                onClick = onBegin,
            )
        }
    }
}

/**
 * The launch page's mark: a solid core that breathes, inside a dashed ring that
 * turns.
 *
 * The twin of `CasterMark` in `Caster/Interface/Views/LaunchView.swift`, and the
 * periods and amplitudes below are the same numbers. The iOS one used to flip a
 * `@State` flag in `onAppear` and drive a `repeatForever` off it, which stalled
 * on the way back to the root of the navigation stack and left the mark frozen
 * mid-scale; both platforms now read the animation out of a clock instead, so
 * there is no state to get stuck in.
 *
 * The pulse is a sine rather than an eased ramp. A ramp has two endpoints where
 * the motion stops dead, and stopping dead twice a second is what reads as
 * "zooming in and out" instead of "breathing".
 */
@Composable
private fun CasterMark() {
    val theme = LocalTheme.current

    val clock = rememberInfiniteTransition(label = "caster-mark")

    /** Seconds for one full turn of the ring, as milliseconds. */
    val turn by clock.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            // Linear, and never reversed: a ring that eased would look like it
            // was being wound rather than turning.
            animation = tween(9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "turn",
    )

    /** Walks one full cycle of the sine below, once every 3.2 seconds. */
    val breathPhase by clock.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breath",
    )

    // 0.92…1.0. Shallow deliberately: the old one travelled 0.85…1.0, which on
    // an 80dp circle is a 12dp swing and far too much movement for something
    // that never stops.
    val breath = 0.96f + 0.04f * sin(breathPhase)

    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        // Sits the mark *in* the page rather than on top of it. The blur is what
        // keeps it from reading as a third circle.
        Canvas(
            Modifier
                .size(132.dp)
                .blur(14.dp)
        ) {
            drawCircle(theme.accent.copy(alpha = 0.12f), radius = size.minDimension / 2f)
        }

        Canvas(Modifier.size(132.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)

            rotate(degrees = turn, pivot = centre) {
                drawCircle(
                    color = theme.accent.copy(alpha = 0.35f),
                    radius = 55.dp.toPx(),
                    center = centre,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 12.dp.toPx())
                        ),
                    ),
                )
            }

            drawCircle(
                color = theme.accent,
                radius = 40.dp.toPx() * breath,
                center = centre,
            )
        }
    }
}

/**
 * So a test build is never mistaken for the real one — they share an
 * application id, which means installing one replaces the other.
 */
@Composable
private fun TestBuildBadge() {
    if (!BuildConfig.DEBUG) return
    val theme = LocalTheme.current
    Text(
        text = "TEST BUILD",
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(theme.warning)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = CasterType.badge.copy(color = theme.background),
    )
}

/**
 * Routes a mode to its screen. One place to add a game, rather than a `when`
 * buried in the navigation stack.
 *
 * Also the only place that arms the interstitial. Arming on a game appearing —
 * rather than letting the mode list guess from its own composition — is what
 * keeps the single placement single: the mode list is reached on the way in, on
 * the way back from the wheel editor and on the way back from a game, and only
 * the last of those is allowed to show anything.
 */
@Composable
private fun GameHost(mode: GameMode, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current

    LaunchedEffect(mode) {
        environment.pacing.armForInterstitial()
        // Fetched now so that if one is shown on the way out, it is already in
        // memory and the transition does not stutter.
        environment.ads.preload()
    }

    when (mode) {
        GameMode.FINGER_PICKER -> FingerPickerScreen(onBack)
        GameMode.PINWHEEL -> PinwheelScreen(onBack)
        GameMode.HOT_POTATO -> HotPotatoScreen(onBack)
        GameMode.UPPERCUT -> UppercutScreen(onBack)
        GameMode.TAP_FRENZY -> TapFrenzyScreen(onBack)
        GameMode.CHICKEN -> ChickenScreen(onBack)
    }
}
