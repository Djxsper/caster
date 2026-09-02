package com.jesperhaafkes.caster

import com.jesperhaafkes.caster.domain.DrawEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The draw is the part of the app people are trusting, so it is the part worth
 * pinning down. These run on the JVM — no device, no UI.
 */
class DrawEngineTest {

    @Test
    fun `a zero weight is never drawn`() {
        // Slot 1 carries all the weight; 0 and 2 carry none.
        repeat(500) {
            assertEquals(1, DrawEngine.drawIndexWithWeights(listOf(0, 5, 0), limit = 3))
        }
    }

    @Test
    fun `an all-zero set still returns somebody`() {
        val seen = HashSet<Int>()
        repeat(500) {
            val index = DrawEngine.drawIndexWithWeights(listOf(0, 0, 0), limit = 3)
            assertTrue("index out of range: $index", index in 0..2)
            seen.add(index!!)
        }
        // Uniform fallback, not a constant.
        assertEquals(setOf(0, 1, 2), seen)
    }

    @Test
    fun `weights bias the draw in proportion`() {
        // 90 vs 10 should land near nine to one; the bound is loose enough that
        // a correct implementation will not flake.
        var heavy = 0
        val runs = 4_000
        repeat(runs) {
            if (DrawEngine.drawIndexWithWeights(listOf(90, 10), limit = 2) == 0) heavy += 1
        }
        val share = heavy.toDouble() / runs
        assertTrue("heavy slot won $share of the time", share > 0.85 && share < 0.95)
    }

    @Test
    fun `an empty set has nothing to draw`() {
        assertNull(DrawEngine.drawIndexWithWeights(emptyList(), limit = 0))
        assertNull(DrawEngine.drawIndexWithWeights(listOf(1, 2, 3), limit = 0))
    }

    @Test
    fun `limit caps the draw to the live slots`() {
        repeat(200) {
            val index = DrawEngine.drawIndexWithWeights(listOf(1, 1, 1, 1, 1), limit = 2)
            assertTrue("index $index escaped the limit", index in 0..1)
        }
    }

    @Test
    fun `teams come out balanced to within one`() {
        val pool = (1..7).toList()
        repeat(200) {
            val assignments = DrawEngine.splitIntoTeams(pool, teams = 3)
            assertEquals(pool.size, assignments.size)
            val sizes = assignments.groupingBy { it }.eachCount().values
            // Seven people across three teams: 3/2/2 in some order.
            assertEquals(1, sizes.max() - sizes.min())
            assertEquals(setOf(0, 1, 2), assignments.toSet())
        }
    }

    @Test
    fun `an order is a permutation, never a repeat`() {
        repeat(200) {
            val order = DrawEngine.randomOrder(count = 6)
            assertEquals(listOf(1, 2, 3, 4, 5, 6), order.sorted())
        }
    }

    @Test
    fun `picking more than the pool holds returns the whole pool`() {
        val pool = listOf("a", "b", "c")
        assertEquals(pool.toSet(), DrawEngine.pick(10, pool).toSet())
        assertEquals(emptyList<String>(), DrawEngine.pick(0, pool))
    }
}
