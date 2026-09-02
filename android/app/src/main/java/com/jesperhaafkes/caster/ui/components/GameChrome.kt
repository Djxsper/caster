package com.jesperhaafkes.caster.ui.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
import com.jesperhaafkes.caster.touch.TouchArena
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import kotlin.math.roundToInt

/** The filled pill every screen uses for its main action. */
/**
 * Text that shrinks to fit instead of ellipsising.
 *
 * Swift reaches for `.minimumScaleFactor` in five places, and every one of them
 * is somewhere a real name ends up. The port turned them all into
 * `overflow = Ellipsis`, so the holder's name in Hot Potato - the single largest
 * element in that game - came out as "Bartholom...". [minScale] is the same
 * fraction Swift passes.
 */
@Composable
fun AutoShrinkText(
    text: String,
    style: TextStyle,
    minScale: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(
            minFontSize = style.fontSize * minScale,
            maxFontSize = style.fontSize,
        ),
    )
}

@Composable
fun PrimaryButton(
    title: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1f else 0.45f)
            .clip(RoundedCornerShape(12.dp))
            .background(tint ?: theme.accent)
            .tappable(enabled = isEnabled, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = CasterFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** A quieter, outlined button for the secondary action next to a [PrimaryButton]. */
@Composable
fun SecondaryButton(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surfaceRaised)
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .tappable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = CasterFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textPrimary,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** The one-line prompt every game keeps under its play area. */
@Composable
fun StatusLine(
    text: String,
    modifier: Modifier = Modifier,
    emphasis: Color? = null,
) {
    val theme = LocalTheme.current
    val color by animateColorAsState(
        targetValue = emphasis ?: theme.textSecondary,
        animationSpec = tween(200),
        label = "status-tint",
    )
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        style = TextStyle(
            fontFamily = CasterFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            textAlign = TextAlign.Center,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A ring drawn under someone's finger. Shared by every touch game so a finger
 * looks and behaves the same wherever it lands.
 */
@Composable
fun FingerRing(
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 84.dp,
    /** Fill of the outer countdown arc, 0…1. Hidden at 0. */
    progress: Float = 0f,
    /** Rotation of the dashed halo, in degrees. */
    spin: Float = 0f,
    isHighlighted: Boolean = false,
    isDimmed: Boolean = false,
    badge: String? = null,
) {
    val haloDiameter = diameter + 34.dp
    val arcDiameter = diameter + 18.dp

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "ring-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isDimmed) 0.16f else 1f,
        animationSpec = tween(250),
        label = "ring-alpha",
    )

    Box(
        modifier = modifier
            .size(haloDiameter)
            .scale(scale)
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val innerRadius = diameter.toPx() / 2f
            val arcRadius = arcDiameter.toPx() / 2f
            val haloRadius = haloDiameter.toPx() / 2f

            drawCircle(color.copy(alpha = 0.24f), radius = innerRadius, center = centre)
            drawCircle(color, radius = innerRadius, center = centre, style = Stroke(5.dp.toPx()))

            if (progress > 0f) {
                drawCountdownArc(centre, arcRadius, progress, color)
            }

            rotate(degrees = spin, pivot = centre) {
                drawCircle(
                    color = color.copy(alpha = 0.45f),
                    radius = haloRadius - 2f,
                    center = centre,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(9.dp.toPx(), 13.dp.toPx())
                        ),
                    ),
                )
            }
        }

        if (badge != null) {
            Text(
                text = badge,
                modifier = Modifier.widthIn(max = diameter * 0.9f),
                style = TextStyle(
                    fontFamily = CasterFontFamily,
                    fontSize = (diameter.value * 0.42f).sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun DrawScope.drawCountdownArc(
    centre: Offset,
    radius: Float,
    progress: Float,
    color: Color,
) {
    drawArc(
        color = color.copy(alpha = 0.95f),
        // Compose measures from three o'clock, SwiftUI's trim from twelve.
        startAngle = -90f,
        sweepAngle = 360f * progress.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
}

/**
 * A [FingerRing] centred on a point in the surface's own pixel coordinates, so
 * a ring rendered at a touch point lands under the finger.
 */
@Composable
fun PositionedFingerRing(
    location: Offset,
    color: Color,
    diameter: Dp = 84.dp,
    progress: Float = 0f,
    spin: Float = 0f,
    isHighlighted: Boolean = false,
    isDimmed: Boolean = false,
    badge: String? = null,
) {
    val halfPx = with(LocalDensity.current) { (diameter + 34.dp).toPx() / 2f }

    // A finger landing should pop, not blink into existence - Swift gives every
    // ring .transition(.scale.combined(with: .opacity)) at
    // FingerPickerView.swift:107. Only the entrance is reproduced here: an exit
    // needs the ring to outlive the finger in composition, and the games render
    // straight from the live arena. In Finger Picker that matches the rule
    // anyway, where lifting is meant to take the colour away at once.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 900f))
    }

    FingerRing(
        color = color,
        modifier = Modifier
            .offset {
                IntOffset((location.x - halfPx).roundToInt(), (location.y - halfPx).roundToInt())
            }
            .graphicsLayer {
                scaleX = 0.6f + 0.4f * entrance.value
                scaleY = 0.6f + 0.4f * entrance.value
                alpha = entrance.value.coerceIn(0f, 1f)
            },
        diameter = diameter,
        progress = progress,
        spin = spin,
        isHighlighted = isHighlighted,
        isDimmed = isDimmed,
        badge = badge,
    )
}

/**
 * Holds the display awake for as long as a round is on screen.
 *
 * A Hot Potato fuse burns with nobody touching the glass, Chicken can sit on a
 * widening window, and Uppercut waits for a cue that is deliberately slow to
 * arrive — all of which the default screen timeout is happy to interrupt.
 * Scoped to the composable rather than the activity so the editors and the
 * launch page still let the screen sleep normally.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Pairs a full-screen multi-touch surface with an overlay drawn in the same
 * coordinate space.
 *
 * Compose reports every pointer with its own id and its own event time, so
 * unlike the iOS build there is no UIKit view to bridge to — but the same rule
 * applies: the timestamp used is the one on the event, never a clock read
 * inside the handler.
 */
@Composable
fun TouchSurface(
    arena: TouchArena,
    modifier: Modifier = Modifier,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    arena.density = density

    Box(
        modifier = modifier
            .fillMaxSize()
            // A thumb parked near an edge is a player, not a back gesture. UIKit
            // gets this by switching off interactivePopGestureRecognizer while
            // the surface is up (MultiTouchView.swift:11-14); this is the same
            // intent, and Android honours it on 29+.
            .systemGestureExclusion()
            .pointerInput(arena) {
                try {
                    awaitPointerEventScope {
                        while (true) {
                            // The main pass, and consumed changes are skipped: a
                            // finger that landed on a button belongs to the button,
                            // exactly as a UIKit hit test would decide it.
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            for (change in event.changes) {
                                if (change.isConsumed) continue
                                when {
                                    change.changedToDownIgnoreConsumed() -> arena.touchBegan(
                                        id = change.id.value,
                                        location = change.position,
                                        timestamp = change.uptimeMillis,
                                    )

                                    change.changedToUpIgnoreConsumed() -> arena.touchEnded(
                                        id = change.id.value,
                                        location = change.position,
                                        timestamp = change.uptimeMillis,
                                    )

                                    change.pressed && change.positionChanged() -> arena.touchMoved(
                                        id = change.id.value,
                                        location = change.position,
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    // The loop also ends when Compose tears the gesture down
                    // mid-round, and then no up event is ever delivered for the
                    // fingers still on the glass. Ending them here is what
                    // `touchesCancelled` does on iOS.
                    arena.endAllFingers(SystemClock.uptimeMillis())
                }
            },
        content = overlay,
    )
}

/**
 * What a touch game shows before anyone has put a finger down. Without it a
 * full-screen touch surface is just a blank page.
 */
@Composable
fun EmptyPlayHint(
    glyph: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = glyph, style = TextStyle(fontFamily = CasterFontFamily, fontSize = 46.sp))
        Text(
            text = title,
            style = TextStyle(
                fontFamily = CasterFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
            ),
        )
        Text(
            text = detail,
            modifier = Modifier.widthIn(max = 280.dp),
            style = TextStyle(
                fontFamily = CasterFontFamily,
                fontSize = 13.sp,
                color = theme.textSecondary,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** The result card that slides in when a round resolves. */
@Composable
fun ResultBanner(
    headline: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tint: Color? = null,
) {
    val theme = LocalTheme.current

    // The reveal is the payoff of every one of these games, and in the port it
    // was a hard cut - Swift springs it in at, among others,
    // PinwheelView.swift:195 and TapFrenzyView.swift:450. Animating on first
    // composition rather than at the call sites means every banner gets it.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(headline) {
        entrance.snapTo(0f)
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // The spring overshoots past 1, which is the point: it is what
                // makes the banner land rather than simply appear.
                scaleX = 0.85f + 0.15f * entrance.value
                scaleY = 0.85f + 0.15f * entrance.value
                alpha = entrance.value.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(theme.surfaceRaised)
            .border(1.dp, (tint ?: theme.border).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = headline,
            style = TextStyle(
                fontFamily = CasterFontFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = tint ?: theme.textPrimary,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = TextStyle(
                    fontFamily = CasterFontFamily,
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

/**
 * A tap target with no chrome of its own — the Compose answer to SwiftUI's
 * `.buttonStyle(.plain)`, which every button in this app uses.
 */
fun Modifier.tappable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, onClick = onClick)

/**
 * A tap with no ripple, no click sound and no button semantics.
 *
 * For the surfaces that *are* the whole screen — passing the potato is a tap
 * anywhere — Material's press feedback fires across the entire display on every
 * pass, which reads as a rendering glitch rather than a button. Swift uses a
 * bare `.contentShape(Rectangle()).onTapGesture` here for the same reason.
 * Ordinary buttons keep [tappable] and keep their ripple, because on Android
 * that is the right answer for a button.
 */
@Composable
fun Modifier.silentTap(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}
