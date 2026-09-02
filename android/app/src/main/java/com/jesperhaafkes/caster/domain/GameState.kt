package com.jesperhaafkes.caster.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Seat limits for the name-based modes. Kept off [GameState] so they can be
 * read from anywhere, including a composable's default parameter value.
 */
object PlayerLimits {
    const val MINIMUM = 2
    const val MAXIMUM = 12
}

/**
 * Session-wide state shared by every screen: which mode is selected and, for
 * the modes that need them, the named players. Created once by the activity
 * and handed down through a composition local.
 */
class GameState {
    var currentMode: GameMode by mutableStateOf(GameMode.FINGER_PICKER)

    private val _players = mutableStateListOf<Player>()
    val players: List<Player> get() = _players

    fun configurePlayers(count: Int, names: List<String>) {
        val clamped = count.coerceIn(PlayerLimits.MINIMUM, PlayerLimits.MAXIMUM)
        val seated = (0 until clamped).map { seat ->
            val trimmed = names.getOrNull(seat)?.trim().orEmpty()
            Player(seat = seat, name = trimmed.ifEmpty { null })
        }
        _players.clear()
        _players.addAll(seated)
    }

    /**
     * Seats the saved roster. Idempotent: when the names already match, the
     * existing [Player] objects are left alone, so a game can call this on
     * every appearance without resetting the round it is in the middle of.
     *
     * When they differ, anyone still on the roster carries their win/loss tally
     * over to their new seat — editing one name should not wipe the running
     * score for everybody else.
     */
    fun adoptRoster(names: List<String>) {
        val seated = names
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(PlayerLimits.MAXIMUM)

        if (seated == _players.map { it.name }) return

        // Keyed by name rather than by seat: the point is to follow a person
        // through a reorder, and a repeated name can only match one of them.
        val previous = HashMap<String, Player>()
        for (player in _players) previous.putIfAbsent(player.name, player)

        val next = seated.mapIndexed { seat, name ->
            Player(seat = seat, name = name).also { player ->
                previous[name]?.let { earlier ->
                    player.winCount = earlier.winCount
                    player.lossCount = earlier.lossCount
                }
            }
        }
        _players.clear()
        _players.addAll(next)
    }

    /**
     * The name for a touch slot. Falls back to the seat number when more
     * fingers are on the glass than there are names on the roster, so the touch
     * games stay playable without anyone having typed anything.
     */
    fun nameForSlot(slot: Int): String =
        _players.getOrNull(slot)?.name ?: "Player ${slot + 1}"

    fun recordLoss(player: Player) {
        player.lossCount += 1
        for (other in _players) {
            if (other !== player) other.winCount += 1
        }
    }
}
