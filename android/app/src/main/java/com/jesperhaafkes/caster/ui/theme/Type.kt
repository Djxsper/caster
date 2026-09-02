package com.jesperhaafkes.caster.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
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
