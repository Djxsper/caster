package com.jesperhaafkes.caster.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
import com.jesperhaafkes.caster.ui.theme.LocalTheme

/** The panel a game floats over its play area to hold its pre-round settings. */
@Composable
fun ControlPanel(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val theme = LocalTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surfaceRaised.copy(alpha = 0.92f))
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** The stand-in for a SwiftUI segmented `Picker`. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    val theme = LocalTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(theme.background.copy(alpha = 0.55f))
            .border(1.dp, theme.border, RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (option in options) {
            val isSelected = option == selected
            val fill by animateColorAsState(
                targetValue = if (isSelected) theme.accent else Color.Transparent,
                animationSpec = tween(150),
                label = "segment-fill",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(fill)
                    .tappable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = TextStyle(
                        fontFamily = CasterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else theme.textPrimary,
                    ),
                )
            }
        }
    }
}

/** The stand-in for a SwiftUI `Stepper`. */
@Composable
fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    val theme = LocalTheme.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = CasterFontFamily, fontSize = 14.sp, color = theme.textSecondary),
        )
        StepButton(isPlus = false, enabled = value > range.first) { onValueChange(value - 1) }
        StepButton(isPlus = true, enabled = value < range.last) { onValueChange(value + 1) }
    }
}

@Composable
private fun StepButton(isPlus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.accent.copy(alpha = 0.15f))
            .tappable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 2.2.dp.toPx()
            drawLine(
                theme.accent,
                Offset(0f, size.height / 2f),
                Offset(size.width, size.height / 2f),
                stroke,
                StrokeCap.Round,
            )
            if (isPlus) {
                drawLine(
                    theme.accent,
                    Offset(size.width / 2f, 0f),
                    Offset(size.width / 2f, size.height),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

/** The switch Hot Potato uses for its pass order. */
@Composable
fun ToggleRow(
    title: String,
    caption: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    val theme = LocalTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surfaceRaised)
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .tappable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = CasterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary,
                ),
            )
            Text(
                text = caption,
                style = TextStyle(fontFamily = CasterFontFamily, fontSize = 12.sp, color = theme.textSecondary),
            )
        }

        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** A row of the scoreboards the reaction games put up when a round resolves. */
@Composable
fun ResultTable(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val theme = LocalTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.surfaceRaised)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
