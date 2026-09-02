package com.jesperhaafkes.caster.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
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
import com.jesperhaafkes.caster.ui.theme.LocalTheme

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

    when (val route = path.lastOrNull()) {
        null -> LaunchContent {
            environment.hapticEngine.playFeedback(FeedbackType.MEDIUM)
            path.add(Route.ModeSelect)
        }

        Route.ModeSelect -> ModeSelectScreen(onBack = pop, onAdvance = { path.add(it) })

        Route.PlayerSetup -> PlayerSetupScreen(onBack = pop, onStart = { path.add(it) })

        Route.WheelSetup -> WheelSetupScreen(onBack = pop, onSpin = { path.add(it) })

        is Route.Game -> GameHost(mode = route.mode, onBack = pop)
    }
}

@Composable
private fun LaunchContent(onBegin: () -> Unit) {
    val theme = LocalTheme.current

    val pulse = rememberInfiniteTransition(label = "launch-pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "launch-scale",
    )

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
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(theme.accent, radius = 40.dp.toPx(), center = centre)
                    drawCircle(
                        color = theme.accent.copy(alpha = 0.3f),
                        radius = 55.dp.toPx() - 2f,
                        center = centre,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10.dp.toPx(), 12.dp.toPx())
                            ),
                        ),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Caster",
                    style = TextStyle(
                        fontFamily = CasterFontFamily,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                    ),
                )
                Text(
                    text = "Ready to play",
                    style = TextStyle(fontFamily = CasterFontFamily, fontSize = 20.sp, color = theme.textSecondary),
                )
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
 * Routes a mode to its screen. One place to add a game, rather than a `when`
 * buried in the navigation stack.
 */
@Composable
private fun GameHost(mode: GameMode, onBack: () -> Unit) {
    when (mode) {
        GameMode.FINGER_PICKER -> FingerPickerScreen(onBack)
        GameMode.PINWHEEL -> PinwheelScreen(onBack)
        GameMode.HOT_POTATO -> HotPotatoScreen(onBack)
        GameMode.UPPERCUT -> UppercutScreen(onBack)
        GameMode.TAP_FRENZY -> TapFrenzyScreen(onBack)
        GameMode.CHICKEN -> ChickenScreen(onBack)
    }
}
