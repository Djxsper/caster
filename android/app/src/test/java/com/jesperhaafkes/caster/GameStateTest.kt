package com.jesperhaafkes.caster

import com.jesperhaafkes.caster.domain.GameState
import com.jesperhaafkes.caster.domain.PlayerLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GameStateTest {

    @Test
    fun `adopting the same roster twice leaves the players alone`() {
        val state = GameState()
        state.adoptRoster(listOf("Ada", "Bo"))
        val ada = state.players[0]

        state.adoptRoster(listOf("Ada", "Bo"))

        // Same objects: a game can call this on every appearance without
        // resetting the round it is in the middle of.
        assertSame(ada, state.players[0])
    }

    @Test
    fun `editing one name keeps everybody else's tally`() {
        val state = GameState()
        state.adoptRoster(listOf("Ada", "Bo", "Cy"))
        state.recordLoss(state.players[0])   // Ada loses; Bo and Cy each win one.

        state.adoptRoster(listOf("Ada", "Bo", "Dee"))

        assertEquals(1, state.players[0].lossCount)  // Ada, carried over
        assertEquals(1, state.players[1].winCount)   // Bo, carried over
        assertEquals(0, state.players[2].winCount)   // Dee is new
    }

    @Test
    fun `blank names are dropped and the roster is capped`() {
        val state = GameState()
        state.adoptRoster(listOf("Ada", "  ", "", "Bo"))
        assertEquals(listOf("Ada", "Bo"), state.players.map { it.name })

        state.adoptRoster((1..20).map { "P$it" })
        assertEquals(PlayerLimits.MAXIMUM, state.players.size)
    }

    @Test
    fun `a slot past the roster still gets a name`() {
        val state = GameState()
        state.adoptRoster(listOf("Ada", "Bo"))

        assertEquals("Ada", state.nameForSlot(0))
        // The touch games seat people by where a finger lands, so they must
        // stay playable when more fingers arrive than there are names.
        assertEquals("Player 5", state.nameForSlot(4))
    }

    @Test
    fun `configurePlayers clamps to the seat limits`() {
        val state = GameState()
        state.configurePlayers(count = 1, names = emptyList())
        assertEquals(PlayerLimits.MINIMUM, state.players.size)
        assertEquals("Player 1", state.players[0].name)

        state.configurePlayers(count = 99, names = listOf("Ada"))
        assertEquals(PlayerLimits.MAXIMUM, state.players.size)
        assertEquals("Ada", state.players[0].name)
    }
}
