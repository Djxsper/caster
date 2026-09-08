package com.jesperhaafkes.caster.ui.theme

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The palette the app is wearing.
 *
 * [SYSTEM] is the free default and follows the phone's light/dark setting, as
 * the app always has. The named ones are Plus, and each is a fixed look rather
 * than a light/dark pair — picking "Midnight" is choosing a dark app, not
 * choosing how a dark app looks.
 *
 * Every one of them keeps [PlayerPalette.colors] for the seats. Seat colour is
 * identity, not decoration: it is how a person finds their own finger on a
 * crowded screen, it is pinned by `shared/parity/golden.json` so the two
 * platforms agree, and a theme that recoloured it would change how the games
 * *play*. Themes dress the chrome and nothing else.
 *
 * The twin of `ThemeSelection` in `Caster/Interface/Themes/PlusThemes.swift`.
 * The [key] values are the Swift enum's raw values, so the two apps write the
 * same string for the same palette.
 */
enum class ThemeSelection(
    val key: String,
    val title: String,
    val subtitle: String,
) {
    SYSTEM("system", "System", "Follows your phone"),
    MIDNIGHT("midnight", "Midnight", "Near-black, high contrast"),
    DUSK("dusk", "Dusk", "Warm plum and amber"),
    FOREST("forest", "Forest", "Deep green, low glare"),
    PAPER("paper", "Paper", "Off-white and ink");

    val isPlus: Boolean get() = this != SYSTEM

    /** [systemPalette] is what SYSTEM resolves to — light or dark, live. */
    fun palette(systemPalette: Theme): Theme = when (this) {
        SYSTEM -> systemPalette
        MIDNIGHT -> PlusPalettes.midnight
        DUSK -> PlusPalettes.dusk
        FOREST -> PlusPalettes.forest
        PAPER -> PlusPalettes.paper
    }

    companion object {
        fun fromKey(key: String?): ThemeSelection =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * The four Plus palettes. Each is one [Theme] value of the same eleven fields
 * as [LightTheme] and [DarkTheme] — there is no new machinery here, which is
 * what makes these cheap enough to be worth shipping.
 *
 * The numbers are the same ones in `PlusThemes.swift`. They are not checked by
 * `golden.json` because chrome colour is not game feel, but they are meant to
 * match, and a palette changed on one platform should be changed on both.
 */
object PlusPalettes {
    val midnight = Theme(
        background = Color(0.008f, 0.012f, 0.024f),
        surface = Color(0.024f, 0.031f, 0.051f),
        surfaceRaised = Color(0.055f, 0.071f, 0.106f),
        border = Color(0.129f, 0.157f, 0.212f),
        textPrimary = Color(0.925f, 0.945f, 0.980f),
        textSecondary = Color(0.529f, 0.573f, 0.647f),
        accent = Color(0.318f, 0.627f, 0.996f),
        success = Color(0.204f, 0.827f, 0.600f),
        danger = Color(0.984f, 0.404f, 0.447f),
        warning = Color(0.984f, 0.780f, 0.353f),
        playerColors = PlayerPalette.colors,
    )

    val dusk = Theme(
        background = Color(0.086f, 0.043f, 0.098f),
        surface = Color(0.129f, 0.067f, 0.145f),
        surfaceRaised = Color(0.192f, 0.102f, 0.208f),
        border = Color(0.310f, 0.180f, 0.325f),
        textPrimary = Color(0.984f, 0.949f, 0.965f),
        textSecondary = Color(0.706f, 0.588f, 0.694f),
        accent = Color(0.976f, 0.596f, 0.310f),
        success = Color(0.353f, 0.812f, 0.596f),
        danger = Color(0.965f, 0.400f, 0.482f),
        warning = Color(0.988f, 0.808f, 0.400f),
        playerColors = PlayerPalette.colors,
    )

    val forest = Theme(
        background = Color(0.031f, 0.086f, 0.067f),
        surface = Color(0.047f, 0.125f, 0.098f),
        surfaceRaised = Color(0.075f, 0.180f, 0.141f),
        border = Color(0.145f, 0.290f, 0.235f),
        textPrimary = Color(0.925f, 0.965f, 0.941f),
        textSecondary = Color(0.573f, 0.694f, 0.635f),
        accent = Color(0.443f, 0.827f, 0.502f),
        success = Color(0.325f, 0.847f, 0.588f),
        danger = Color(0.937f, 0.435f, 0.396f),
        warning = Color(0.933f, 0.780f, 0.365f),
        playerColors = PlayerPalette.colors,
    )

    val paper = Theme(
        background = Color(0.976f, 0.965f, 0.933f),
        surface = Color(0.988f, 0.980f, 0.957f),
        surfaceRaised = Color(0.937f, 0.918f, 0.871f),
        border = Color(0.859f, 0.831f, 0.769f),
        textPrimary = Color(0.114f, 0.106f, 0.086f),
        textSecondary = Color(0.396f, 0.373f, 0.325f),
        accent = Color(0.706f, 0.325f, 0.184f),
        success = Color(0.216f, 0.518f, 0.353f),
        danger = Color(0.729f, 0.204f, 0.204f),
        warning = Color(0.729f, 0.510f, 0.114f),
        playerColors = PlayerPalette.colors,
    )
}

/**
 * Remembers the chosen palette. Its own tiny store rather than a field on
 * `AppEnvironment`, for the same reason the wheels have one: a preference that
 * lives in memory is a preference that a relaunch throws away.
 *
 * Falls back to [ThemeSelection.SYSTEM] whenever Plus is not held, so a refund
 * or a reinstall on a new device can never strand somebody in a palette they no
 * longer own — without forgetting which one they had picked, in case they buy
 * it again.
 */
class ThemeStore(private val prefs: SharedPreferences) {

    var preferred: ThemeSelection by mutableStateOf(ThemeSelection.SYSTEM)
        private set

    init {
        preferred = ThemeSelection.fromKey(prefs.getString(STORAGE_KEY, null))
    }

    fun select(selection: ThemeSelection) {
        preferred = selection
        prefs.edit().putString(STORAGE_KEY, selection.key).apply()
    }

    fun effective(hasPlus: Boolean): ThemeSelection =
        if (preferred.isPlus && !hasPlus) ThemeSelection.SYSTEM else preferred

    private companion object {
        const val STORAGE_KEY = "caster.theme.selection"
    }
}
