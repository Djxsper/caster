package com.jesperhaafkes.caster

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jesperhaafkes.caster.touch.ArenaFinger
import com.jesperhaafkes.caster.touch.TouchArena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Slot bookkeeping is what every touch game scores against, and it is the one
 * part an emulator cannot exercise — synthetic events give you one finger.
 * So it is pinned down here instead.
 */
class TouchArenaTest {

    private lateinit var arena: TouchArena

    @Before
    fun setUp() {
        arena = TouchArena()
        // 1 px per dp, so a radius in dp reads as a radius in test coordinates.
        arena.density = Density(1f)
    }

    private fun down(id: Long, x: Float, y: Float, at: Long = 0L) =
        arena.touchBegan(id, Offset(x, y), at)

    private fun up(id: Long, x: Float, y: Float, at: Long = 0L) =
        arena.touchEnded(id, Offset(x, y), at)

    @Test
    fun `sequential slots are handed out in order and freed on lift`() {
        down(1, 10f, 10f)
        down(2, 200f, 200f)
        assertEquals(setOf(0, 1), arena.occupiedSlots)

        // Lifting frees the colour immediately — the Chwazi behaviour.
        up(1, 10f, 10f)
        assertEquals(setOf(1), arena.occupiedSlots)
        assertNull(arena.anchor(0))

        // The freed slot is the next one handed out.
        down(3, 500f, 500f)
        assertEquals(3, arena.finger(inSlot = 0)?.id?.toInt())
    }

    @Test
    fun `a sticky finger reclaims its own slot when it lands back nearby`() {
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(90.dp)
        down(1, 100f, 100f)
        down(2, 600f, 600f)
        assertEquals(setOf(0, 1), arena.occupiedSlots)

        up(1, 100f, 100f)
        assertFalse(arena.isSlotHeld(0))
        // The anchor outlives the finger under a sticky policy.
        assertEquals(Offset(100f, 100f), arena.anchor(0))

        // Back down 40px away: inside the radius, so slot 0 comes back.
        down(3, 140f, 100f)
        assertEquals(3, arena.finger(inSlot = 0)?.id?.toInt())
        assertEquals(setOf(0, 1), arena.occupiedSlots)
    }

    @Test
    fun `a sticky finger landing far away opens a new slot instead`() {
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(90.dp)
        down(1, 100f, 100f)
        up(1, 100f, 100f)

        // 300px away is well outside the 90dp radius.
        down(2, 400f, 100f)
        assertTrue("should not have reclaimed slot 0", arena.finger(inSlot = 0) == null)
        assertEquals(setOf(1), arena.occupiedSlots)
    }

    @Test
    fun `a sticky touch cannot steal a slot somebody is holding`() {
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(200.dp)
        down(1, 100f, 100f)
        // Lands right on top of slot 0's anchor, but slot 0 is occupied.
        down(2, 105f, 105f)
        assertEquals(setOf(0, 1), arena.occupiedSlots)
        assertEquals(1, arena.finger(inSlot = 0)?.id?.toInt())
    }

    @Test
    fun `a closed round refuses latecomers but still lets its own fingers back`() {
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(90.dp)
        down(1, 100f, 100f)
        down(2, 600f, 600f)

        arena.acceptsNewSlots = false

        var rejectedAt: Offset? = null
        arena.onRejected = { rejectedAt = it }

        // A stranger far from any anchor is turned away, and reported.
        down(3, 900f, 900f)
        assertEquals(setOf(0, 1), arena.occupiedSlots)
        assertEquals(Offset(900f, 900f), rejectedAt)

        // Someone already in the round can still lift and land again.
        up(1, 100f, 100f)
        down(4, 120f, 100f)
        assertEquals(setOf(0, 1), arena.occupiedSlots)
    }

    @Test
    fun `retiring a slot drops its anchor so nobody can claim it`() {
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(200.dp)
        down(1, 100f, 100f)
        down(2, 600f, 600f)

        arena.retire(0)
        assertNull(arena.anchor(0))
        assertFalse(arena.isSlotHeld(0))
        assertEquals(listOf(1), arena.slotsInPlay)

        // A touch in the retired slot's old spot opens a fresh slot instead.
        down(3, 100f, 100f)
        assertNull(arena.finger(inSlot = 0))
        assertEquals(setOf(1, 2), arena.occupiedSlots)
    }

    @Test
    fun `end times come from the event, not from a clock read later`() {
        var endedAt = -1L
        arena.onEnded = { endedAt = it.endTime }

        down(1, 10f, 10f, at = 1_000L)
        up(1, 10f, 10f, at = 1_137L)

        // 137 ms is what the event said, and what a reaction game must score.
        assertEquals(1_137L, endedAt)
    }

    @Test
    fun `a move for an unknown touch is ignored rather than invented`() {
        arena.touchMoved(99, Offset(50f, 50f))
        assertEquals(0, arena.activeCount)
        assertTrue(arena.occupiedSlots.isEmpty())
    }

    @Test
    fun `reset clears fingers, anchors and retirements`() {
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(90.dp)
        down(1, 100f, 100f)
        down(2, 600f, 600f)
        arena.retire(1)
        arena.acceptsNewSlots = false

        arena.reset()

        assertEquals(0, arena.activeCount)
        assertTrue(arena.slotsInPlay.isEmpty())
        assertTrue(arena.retiredSlots.isEmpty())
        assertTrue(arena.acceptsNewSlots)
    }

    @Test
    fun `cancelling ends every live finger and reports the end time`() {
        val ended = mutableListOf<ArenaFinger>()
        arena.onEnded = { ended += it }
        down(1, 100f, 100f, at = 10L)
        down(2, 600f, 600f, at = 20L)

        arena.endAllFingers(timestamp = 99L)

        // No up event ever arrives for a cancelled stream, so without this the
        // slots stay held and the round can never finish.
        assertEquals(0, arena.activeCount)
        assertTrue(arena.occupiedSlots.isEmpty())
        assertEquals(2, ended.size)
        assertTrue("end times come from the cancel, not a later clock read",
            ended.all { it.endTime == 99L })
    }

    @Test
    fun `cancelling under a sticky policy keeps the anchors so seats survive`() {
        arena.slotPolicy = TouchArena.SlotPolicy.Sticky(90.dp)
        down(1, 100f, 100f)
        down(2, 600f, 600f)

        arena.endAllFingers(timestamp = 50L)

        assertEquals(0, arena.activeCount)
        // A shade pull mid-round must not cost everyone their seat: the same
        // fingers coming back down nearby reclaim the slots they had.
        assertEquals(Offset(100f, 100f), arena.anchor(0))
        assertEquals(Offset(600f, 600f), arena.anchor(1))
    }
}
