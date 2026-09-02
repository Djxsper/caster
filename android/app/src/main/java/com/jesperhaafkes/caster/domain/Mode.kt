package com.jesperhaafkes.caster.domain

/**
 * The six mini-games. A plain enum — the cases are immutable, so nothing here
 * needs to be observable.
 */
enum class GameMode(val key: String) {
    FINGER_PICKER("fingerPicker"),
    PINWHEEL("pinwheel"),
    HOT_POTATO("hotPotato"),
    UPPERCUT("uppercut"),
    TAP_FRENZY("tapFrenzy"),
    CHICKEN("chicken");

    val title: String
        get() = when (this) {
            FINGER_PICKER -> "Finger Picker"
            PINWHEEL -> "Pinwheel"
            HOT_POTATO -> "Hot Potato"
            UPPERCUT -> "Uppercut"
            TAP_FRENZY -> "Tap Frenzy"
            CHICKEN -> "Chicken"
        }

    val summary: String
        get() = when (this) {
            FINGER_PICKER ->
                "Everyone holds a finger down. One gets picked — or split into teams, or put in order."
            PINWHEEL ->
                "Spin a wheel of names or anything else you type in. As many entries as you like."
            HOT_POTATO ->
                "A hidden fuse burns while you pass the phone. Whoever holds it when it blows loses."
            UPPERCUT ->
                "Hold a finger. When the light flips and the tone hits, lift. Slowest reaction loses."
            TAP_FRENZY ->
                "Claim a circle and hammer it. Decide first whether taps push the draw away from you or towards you."
            CHICKEN ->
                "Circles light up one at a time. Let go in time and you are out. Last one left loses."
        }

    /**
     * The glyph shown on the mode card. Emoji rather than a vector set: it
     * keeps the app asset-free, exactly as the iOS build is.
     */
    val icon: String
        get() = when (this) {
            FINGER_PICKER -> "\uD83D\uDC46"
            PINWHEEL -> "\uD83C\uDFA1"
            HOT_POTATO -> "\u23F2\uFE0F"
            UPPERCUT -> "\u26A1"
            TAP_FRENZY -> "\uD83D\uDD28"
            CHICKEN -> "\uD83D\uDD25"
        }

    /** The screen, if any, that has to run before the game itself. */
    val setupRoute: Route?
        get() = when (this) {
            HOT_POTATO -> Route.PlayerSetup
            PINWHEEL -> Route.WheelSetup
            FINGER_PICKER, UPPERCUT, TAP_FRENZY, CHICKEN -> null
        }

    companion object {
        fun fromKey(key: String): GameMode? = entries.firstOrNull { it.key == key }
    }
}
