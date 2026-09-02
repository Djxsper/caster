package com.jesperhaafkes.caster.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * A named participant. Only the modes that need names up front — Hot Potato
 * and (optionally) Pinwheel — use these; the touch games identify people by
 * where their finger landed instead.
 */
class Player(
    /**
     * Seat index, 0-based. Drives the player's colour so the colour survives
     * re-ordering of the players list.
     */
    val seat: Int,
    name: String? = null,
) {
    val id: UUID = UUID.randomUUID()

    var name: String by mutableStateOf(name ?: "Player ${seat + 1}")
    var winCount: Int by mutableIntStateOf(0)
    var lossCount: Int by mutableIntStateOf(0)
}
