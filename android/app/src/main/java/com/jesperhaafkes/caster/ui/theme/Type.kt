package com.jesperhaafkes.caster.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.R

/**
 * The app's voice.
 *
 * The iOS build asks for `design: .rounded` in twenty-nine places — it is not an
 * accent, it is the whole typographic character of the thing, and a party game
 * set in the system default reads as a settings screen. Android has no SF
 * Rounded, so this ships Nunito, which is the closest freely licensable face
 * with the same soft terminals.
 *
 * One variable font covers every weight the app uses, so the whole typeface
 * costs about 270 KB rather than four separate static files. Licence is SIL OFL
 * 1.1, kept at `android/licenses/Nunito-OFL.txt`.
 *
 * Swapping this for another face is a one-line change here; nothing else in the
 * app names a typeface.
 */
private fun nunito(weight: FontWeight) = Font(
    resId = R.font.nunito,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val CasterFontFamily = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.Medium),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold),
)

/**
 * The app's type scale.
 *
 * Every screen used to build its own `TextStyle(fontFamily = …, fontSize = …)`
 * inline, which is how the port ended up with nine different body sizes, no
 * line height anywhere, and default tracking on display text. SwiftUI hands its
 * side of the app `.largeTitle`/`.title3`/`.headline` and gets Apple's optical
 * sizing for free; this is the same idea written out, and it is most of the
 * difference between "a Compose app" and "the same app".
 *
 * Three things are deliberate:
 *
 * - **Line height is set on everything.** Compose's default leading is tight
 *   enough that two-line labels touch, which is the single most obvious tell.
 * - **Tracking goes negative as size goes up.** Large text set at default
 *   tracking looks loose; -0.5sp at 34sp is roughly what SF does on its own.
 * - **Sizes are the ones the iOS build resolves to**, so a screenshot of one
 *   next to the other lines up rather than merely rhyming.
 *
 * Call sites pass these and then `.copy(color = …)`, so colour stays the
 * screen's business and metrics stay here.
 */
object CasterType {
    private fun base(
        size: Int,
        weight: FontWeight,
        lineHeight: Int,
        tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = CasterFontFamily,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
    )

    /** The launch page's wordmark, and nothing else. */
    val display = base(36, FontWeight.ExtraBold, 42, -0.8f)

    /** A screen's own heading, below the title bar. */
    val title = base(28, FontWeight.Bold, 34, -0.5f)

    /** The launch page's subtitle, and a paywall's headline. */
    val subtitle = base(19, FontWeight.Medium, 26, -0.2f)

    /** Button text. */
    val button = base(19, FontWeight.Bold, 24, -0.2f)
    val buttonQuiet = base(15, FontWeight.SemiBold, 20)

    /** The title bar. */
    val navTitle = base(17, FontWeight.SemiBold, 22, -0.1f)

    /** A card or list row's first line. */
    val rowTitle = base(16, FontWeight.SemiBold, 21, -0.1f)

    /** Editable names, and anything the user typed. */
    val field = base(16, FontWeight.Normal, 21)

    /** A card or list row's second line. */
    val rowDetail = base(13, FontWeight.Normal, 18)

    /** The line under a game's play area. */
    val status = base(15, FontWeight.Medium, 20)

    /** Section headers in Settings. */
    val sectionHeader = base(13, FontWeight.Bold, 17, 0.6f)

    /** Footnotes, prices, the smallest thing that is still prose. */
    val caption = base(12, FontWeight.Normal, 16)

    /** The TEST BUILD badge. Monospaced-feeling by weight rather than by face. */
    val badge = base(11, FontWeight.ExtraBold, 14, 1.2f)
}
