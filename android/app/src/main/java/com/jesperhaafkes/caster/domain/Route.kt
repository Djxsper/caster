package com.jesperhaafkes.caster.domain

/**
 * Screens reachable from the launch screen. A single list of routes replaces a
 * chain of dialogs-presenting-dialogs, which stacked modals on top of each
 * other and gave each screen its own dead-end state.
 */
sealed interface Route {
    data object ModeSelect : Route

    /** Name entry, for the modes that address people by name. */
    data object PlayerSetup : Route

    /** The pinwheel's entry list. */
    data object WheelSetup : Route

    data class Game(val mode: GameMode) : Route
}
