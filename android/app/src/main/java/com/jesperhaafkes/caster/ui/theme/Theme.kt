package com.jesperhaafkes.caster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * A colour palette. [LightTheme] and [DarkTheme] are the two concrete values;
 * screens read [LocalTheme] rather than hard-coding either one, which is what
 * keeps the app honest in dark mode.
 */
data class Theme(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val playerColors: List<Color>,
) {
    /** Colour for a seat, wrapping around when there are more players than colours. */
    fun playerColor(index: Int): Color {
        if (playerColors.isEmpty()) return accent
        // A negative index would otherwise walk off the front of the list.
        return playerColors[abs(index) % playerColors.size]
    }
}

val LightTheme = Theme(
    background = Color(1.0f, 1.0f, 1.0f),
    surface = Color(1.0f, 1.0f, 1.0f),
    surfaceRaised = Color(0.949f, 0.949f, 0.949f),
    border = Color(0.902f, 0.902f, 0.902f),
    textPrimary = Color(0.063f, 0.063f, 0.063f),
    textSecondary = Color(0.404f, 0.404f, 0.404f),
    accent = Color(0.235f, 0.506f, 0.882f),
    success = Color(0.047f, 0.639f, 0.427f),
    danger = Color(0.855f, 0.204f, 0.263f),
    warning = Color(0.918f, 0.639f, 0.145f),
    playerColors = PlayerPalette.colors,
)

val DarkTheme = Theme(
    background = Color(0.027f, 0.027f, 0.030f),
    surface = Color(0.047f, 0.047f, 0.047f),
    surfaceRaised = Color(0.106f, 0.106f, 0.106f),
    border = Color(0.182f, 0.182f, 0.182f),
    textPrimary = Color(0.965f, 0.965f, 0.965f),
    textSecondary = Color(0.569f, 0.569f, 0.569f),
    accent = Color(0.235f, 0.506f, 0.882f),
    success = Color(0.157f, 0.780f, 0.549f),
    danger = Color(0.937f, 0.325f, 0.376f),
    warning = Color(0.976f, 0.749f, 0.290f),
    playerColors = PlayerPalette.colors,
)

/** The eight seat colours, shared by both palettes. */
object PlayerPalette {
    val colors: List<Color> = listOf(
        Color(0.235f, 0.506f, 0.882f), // 1 — Blue
        Color(0.063f, 0.690f, 0.498f), // 2 — Green
        Color(0.859f, 0.780f, 0.384f), // 3 — Amber
        Color(0.560f, 0.365f, 0.250f), // 4 — Brown
        Color(0.533f, 0.373f, 0.831f), // 5 — Purple
        Color(0.961f, 0.298f, 0.486f), // 6 — Pink
        Color(0.063f, 0.690f, 0.859f), // 7 — Cyan
        Color(0.392f, 0.447f, 0.509f), // 8 — Slate
    )

    /**
     * A colour for slice [index] of [total]. The pinwheel is unbounded, so it
     * cannot use the fixed eight: past that the hue is spread evenly around the
     * wheel instead, which keeps neighbours distinguishable at any count.
     */
    fun spread(index: Int, total: Int): Color {
        if (total <= colors.size) return colors[abs(index) % colors.size]

        val position = (abs(index) % maxOf(1, total)).toFloat() / maxOf(1, total).toFloat()
        // Walk the hue with a large stride so adjacent slices are far apart on
        // the wheel rather than a slow gradient that reads as one blur.
        val hue = ((position + (index % 3) * 0.11f) % 1.0f) * 360f
        val saturation = if (index % 2 == 0) 0.62f else 0.74f
        val brightness = if (index % 3 == 0) 0.92f else 0.80f
        return Color.hsv(hue, saturation, brightness)
    }
}

val LocalTheme = staticCompositionLocalOf { LightTheme }

/** The palette matching the system colour scheme, tracked live. */
@Composable
@ReadOnlyComposable
fun themeForScheme(): Theme = if (isSystemInDarkTheme()) DarkTheme else LightTheme
