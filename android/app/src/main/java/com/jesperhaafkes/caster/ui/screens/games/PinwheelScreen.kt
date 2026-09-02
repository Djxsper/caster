package com.jesperhaafkes.caster.ui.screens.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.LocalWheelStore
import com.jesperhaafkes.caster.domain.GameMode
import com.jesperhaafkes.caster.ui.audio.Tone
import com.jesperhaafkes.caster.ui.components.KeepScreenOn
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.components.ResultBanner
import com.jesperhaafkes.caster.ui.components.SecondaryButton
import com.jesperhaafkes.caster.ui.components.StatusLine
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import com.jesperhaafkes.caster.ui.theme.PlayerPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A spin-the-wheel. The entry list is unbounded — the wheel sizes its own
 * slices, type and label density to whatever is on it.
 *
 * The winner is drawn uniformly *before* the animation starts and the spin is
 * then aimed at that slice. Letting friction decide would quietly bias the
 * result toward wherever the wheel happened to be resting.
 */
@Composable
fun PinwheelScreen(onBack: () -> Unit) {
    val theme = LocalTheme.current
    val environment = LocalAppEnvironment.current
    val wheelStore = LocalWheelStore.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    var rotation by remember { mutableFloatStateOf(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var winnerIndex by remember { mutableStateOf<Int?>(null) }
    var spinJob by remember { mutableStateOf<Job?>(null) }
    var lastTickBoundary by remember { mutableIntStateOf(0) }

    val entries = wheelStore.entries
    val labels = wheelStore.labels

    /**
     * @param strength 0…1. Showmanship only — it sets how long and how far the
     *   wheel travels, never where it stops.
     */
    fun spin(strength: Double) {
        if (entries.size < 2 || isSpinning) return

        spinJob?.cancel()
        winnerIndex = null
        isSpinning = true

        val winner = Random.nextInt(entries.size)
        val segment = 360.0 / entries.size
        // Land somewhere inside the slice rather than dead centre every time.
        val jitter = Random.nextDouble(-0.33, 0.33)
        val desired = -((winner + 0.5 + jitter) * segment)

        var delta = (desired - rotation) % 360.0
        if (delta < 0) delta += 360.0

        val turns = 4 + (strength * 6).roundToInt()
        val start = rotation.toDouble()
        val span = turns * 360.0 + delta
        val durationMs = (3.2 + strength * 2.4) * 1000

        lastTickBoundary = floor(start / segment).toInt()

        spinJob = scope.launch {
            val began = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val elapsed = (now - began) / 1_000_000.0
                val fraction = (elapsed / durationMs).coerceAtMost(1.0)
                // Cubic ease-out: quick off the mark, long settle at the end.
                val eased = 1 - (1 - fraction).pow(3.1)
                rotation = (start + span * eased).toFloat()

                val boundary = floor(rotation / segment).toInt()
                if (boundary != lastTickBoundary) {
                    lastTickBoundary = boundary
                    environment.cue(FeedbackType.LIGHT, Tone.TICK)
                }

                if (fraction >= 1.0) break
            }

            isSpinning = false
            // Keep the accumulated angle small so the tick maths stays exact.
            rotation %= 360f
            winnerIndex = winner
            environment.cue(FeedbackType.HEAVY, Tone.REVEAL)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            spinJob?.cancel()
            spinJob = null
        }
    }

    val settledWinner = winnerIndex
    val statusText = when {
        isSpinning -> "…"
        settledWinner != null && settledWinner in entries.indices -> entries[settledWinner].label
        else -> "Flick the wheel or press Spin"
    }

    KeepScreenOn()

    CasterScreen(title = GameMode.PINWHEEL.title, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)

                Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotation)
                            .pointerInput(entries.size, isSpinning) {
                                var travel = Offset.Zero
                                val reference = 260.dp.toPx()
                                detectDragGestures(
                                    onDragStart = { travel = Offset.Zero },
                                    onDragEnd = {
                                        if (!isSpinning) {
                                            val distance = hypot(travel.x, travel.y)
                                            spin(minOf(1.0, (distance / reference).toDouble()))
                                        }
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    travel += dragAmount
                                }
                            },
                    ) {
                        drawWheel(labels, textMeasurer)
                    }

                    // Both drawn outside the rotating canvas, so the hub stays
                    // still and the pointer stays pinned at twelve o'clock.
                    Hub(isSpinning = isSpinning)

                    Canvas(
                        modifier = Modifier
                            .size(width = 26.dp, height = 22.dp)
                            .align(Alignment.TopCenter),
                    ) {
                        val triangle = Path().apply {
                            moveTo(size.width / 2f, size.height)
                            lineTo(0f, 0f)
                            lineTo(size.width, 0f)
                            close()
                        }
                        drawPath(triangle, theme.textPrimary)
                    }
                }
            }

            StatusLine(
                text = statusText,
                emphasis = if (winnerIndex == null) null else theme.accent,
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (wheelStore.canSpin) {
                    PrimaryButton(
                        title = if (isSpinning) "Spinning…" else "Spin",
                        isEnabled = !isSpinning,
                    ) {
                        spin(Random.nextDouble(0.45, 0.9))
                    }

                    val winner = winnerIndex
                    if (winner != null && !isSpinning && winner in entries.indices) {
                        SecondaryButton(title = "Remove ${entries[winner].label}") {
                            environment.hapticEngine.playFeedback(FeedbackType.MEDIUM)
                            val id = entries[winner].id
                            winnerIndex = null
                            wheelStore.remove(id)
                        }
                    }
                } else {
                    ResultBanner(
                        headline = "The wheel needs two entries",
                        detail = "Go back and add some names.",
                    )
                }
            }
        }
    }
}

@Composable
private fun Hub(isSpinning: Boolean) {
    val theme = LocalTheme.current
    Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(theme.background, radius = size.minDimension / 2f, center = centre)
            drawCircle(
                color = theme.border,
                radius = size.minDimension / 2f,
                center = centre,
                style = Stroke(1.dp.toPx()),
            )
        }
        androidx.compose.material3.Text(
            text = if (isSpinning) "🎡" else "👋",
            style = TextStyle(fontSize = 20.sp),
        )
    }
}

/**
 * The wheel's drawing, deliberately kept out of the composable: this owns every
 * decision about slice colour, type size and truncation, and gets nothing but a
 * plain list of labels.
 */
private fun DrawScope.drawWheel(labels: List<String>, textMeasurer: TextMeasurer) {
    val count = labels.size
    if (count == 0) return

    val radius = size.minDimension / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)
    val segment = 360f / count

    for (index in 0 until count) {
        val start = -90f + index * segment
        val path = Path().apply {
            moveTo(centre.x, centre.y)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    center = centre,
                    radius = radius,
                ),
                startAngleDegrees = start,
                sweepAngleDegrees = segment,
                forceMoveTo = false,
            )
            close()
        }

        val fill = PlayerPalette.spread(index, count)
        drawPath(path, fill)
        drawPath(path, Color.White.copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))

        drawSliceLabel(
            label = labels[index],
            textMeasurer = textMeasurer,
            centre = centre,
            radius = radius,
            midAngle = start + segment / 2f,
            segment = segment,
            ink = inkFor(fill),
        )
    }
}

private fun DrawScope.drawSliceLabel(
    label: String,
    textMeasurer: TextMeasurer,
    centre: Offset,
    radius: Float,
    midAngle: Float,
    segment: Float,
    ink: Color,
) {
    // Tangential room where the text sits, which is what actually limits the
    // type size once the wheel gets busy.
    val labelRadius = radius * 0.58f
    val tangential = (segment * PI / 180.0).toFloat() * labelRadius
    // Below this a label is unreadable anyway, so the slice colour and the
    // result line carry the identity instead of a smear of pixels.
    if (tangential < 11.dp.toPx()) return

    val fontSizePx = (tangential * 0.72f).coerceAtMost(radius * 0.14f)
        .coerceIn(9.dp.toPx(), 19.dp.toPx())
    val available = radius * 0.74f
    val maximumCharacters = maxOf(2, (available / (fontSizePx * 0.52f)).toInt())
    val text = if (label.length > maximumCharacters) {
        label.take(maxOf(1, maximumCharacters - 1)) + "…"
    } else {
        label
    }

    val layout = textMeasurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(
            fontSize = (fontSizePx / density).sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        ),
    )

    // A slice on the left half puts its label past vertical, where the text
    // ends up upside down. Turn the layer the other way and draw down the
    // opposite radius, which lands in the same place the right way up.
    val isFlipped = cos(midAngle * PI / 180.0) < 0
    val drawAngle = if (isFlipped) midAngle + 180f else midAngle
    val offsetX = if (isFlipped) -labelRadius else labelRadius

    rotate(degrees = drawAngle, pivot = centre) {
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                centre.x + offsetX - layout.size.width / 2f,
                centre.y - layout.size.height / 2f,
            ),
        )
    }
}

/**
 * An ink colour that stays legible on the slice, picked from the fill's
 * luminance so a pale amber slice does not get white type.
 */
private fun inkFor(fill: Color): Color {
    val luminance = 0.299f * fill.red + 0.587f * fill.green + 0.114f * fill.blue
    return if (luminance > 0.6f) Color.Black.copy(alpha = 0.82f) else Color.White
}
