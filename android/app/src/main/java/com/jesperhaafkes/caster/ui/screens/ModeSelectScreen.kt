package com.jesperhaafkes.caster.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.LocalGameState
import com.jesperhaafkes.caster.domain.GameMode
import com.jesperhaafkes.caster.domain.Route
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.components.tappable
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.theme.LocalTheme

@Composable
fun ModeSelectScreen(onBack: () -> Unit, onAdvance: (Route) -> Unit) {
    val theme = LocalTheme.current
    val environment = LocalAppEnvironment.current
    val gameState = LocalGameState.current

    /**
     * The label names the next screen, because it is not always the game — two
     * modes need a setup step first.
     */
    val continueTitle = when (gameState.currentMode.setupRoute) {
        Route.PlayerSetup -> "Add Players"
        Route.WheelSetup -> "Edit the Wheel"
        else -> "Play"
    }

    CasterScreen(title = "Game Modes", onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "Pick One",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                style = TextStyle(
                    fontFamily = CasterFontFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
            )

            // Scrolls: six cards plus the button overflow a small screen.
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(GameMode.entries, key = { it.key }) { mode ->
                    ModeCard(
                        mode = mode,
                        isSelected = gameState.currentMode == mode,
                        onSelect = {
                            environment.hapticEngine.playFeedback(FeedbackType.LIGHT)
                            gameState.currentMode = mode
                        },
                    )
                }
            }

            PrimaryButton(
                title = continueTitle,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                environment.hapticEngine.playFeedback(FeedbackType.MEDIUM)
                val mode = gameState.currentMode
                onAdvance(mode.setupRoute ?: Route.Game(mode))
            }
        }
    }
}

@Composable
private fun ModeCard(mode: GameMode, isSelected: Boolean, onSelect: () -> Unit) {
    val theme = LocalTheme.current

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) theme.accent else theme.border,
        animationSpec = tween(150),
        label = "card-border",
    )
    val fillColor by animateColorAsState(
        targetValue = if (isSelected) theme.accent.copy(alpha = 0.1f) else theme.surfaceRaised,
        animationSpec = tween(150),
        label = "card-fill",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(fillColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .tappable(onClick = onSelect)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Text(text = mode.icon, style = TextStyle(fontFamily = CasterFontFamily, fontSize = 22.sp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = mode.title,
                style = TextStyle(
                    fontFamily = CasterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary,
                ),
            )
            Text(
                text = mode.summary,
                style = TextStyle(fontFamily = CasterFontFamily, fontSize = 12.sp, color = theme.textSecondary),
            )
        }

        Spacer(Modifier.width(8.dp))

        SelectionMark(isSelected = isSelected)
    }
}

@Composable
private fun SelectionMark(isSelected: Boolean) {
    val theme = LocalTheme.current
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val centre = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 1.dp.toPx()
            if (isSelected) {
                drawCircle(theme.accent, radius = radius, center = centre)
                val tick = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centre.x - radius * 0.42f, centre.y)
                    lineTo(centre.x - radius * 0.10f, centre.y + radius * 0.34f)
                    lineTo(centre.x + radius * 0.46f, centre.y - radius * 0.34f)
                }
                drawPath(
                    path = tick,
                    color = androidx.compose.ui.graphics.Color.White,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            } else {
                drawCircle(
                    color = theme.border,
                    radius = radius,
                    center = centre,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()),
                )
            }
        }
    }
}
