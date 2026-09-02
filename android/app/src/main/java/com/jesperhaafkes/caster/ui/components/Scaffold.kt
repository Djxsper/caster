package com.jesperhaafkes.caster.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.ui.theme.LocalTheme

/**
 * The frame every screen past the launch page sits in: a title bar with a back
 * chevron and optional trailing actions, over the theme's background.
 *
 * Hand-rolled rather than a Material `TopAppBar` so the bar uses the app's own
 * palette — the same reason the iOS build reaches for its own chrome instead of
 * the system's.
 */
@Composable
fun CasterScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val theme = LocalTheme.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp)) {
                if (onBack != null) {
                    BackChevron(onBack)
                }
            }

            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                // The activity is edge-to-edge, so without these the content
                // runs underneath the system bars: every footer button in the
                // app sits on a literal 20-24dp bottom padding, which is less
                // than a three-button nav bar and about the height of the
                // gesture pill. adjustResize does not resize an edge-to-edge
                // window either, so the keyboard covers the editors' add-row
                // unless imePadding asks for the room.
                .navigationBarsPadding()
                .imePadding(),
            content = content,
        )
    }
}

@Composable
private fun BackChevron(onBack: () -> Unit) {
    val theme = LocalTheme.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .tappable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 2.2.dp.toPx()
            val midY = size.height / 2f
            drawLine(
                color = theme.accent,
                start = Offset(size.width * 0.62f, midY - size.height * 0.28f),
                end = Offset(size.width * 0.30f, midY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = theme.accent,
                start = Offset(size.width * 0.30f, midY),
                end = Offset(size.width * 0.62f, midY + size.height * 0.28f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** A glyph-only action for the title bar, used for the roster shortcut. */
@Composable
fun BarAction(
    glyph: String,
    contentDescription: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(fontSize = 19.sp, color = tint ?: theme.accent),
        )
    }
}
