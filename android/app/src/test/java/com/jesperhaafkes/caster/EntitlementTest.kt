package com.jesperhaafkes.caster

import com.jesperhaafkes.caster.domain.EntitlementStore
import com.jesperhaafkes.caster.domain.FreeLimits
import com.jesperhaafkes.caster.domain.RosterStore
import com.jesperhaafkes.caster.domain.WheelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the free app allows, what Plus lifts, and what neither of them is
 * allowed to take away.
 *
 * The twin of `CasterTests/EntitlementTests.swift`.
 */
class EntitlementTest {

    @Test
    fun `a fresh install is free and capped`() {
        val entitlements = EntitlementStore(FakePrefs())

        assertFalse(entitlements.hasPlus)
        assertFalse(entitlements.isLegacy)
        assertEquals(FreeLimits.SAVED_WHEELS, entitlements.savedWheelCapacity)
        assertEquals(FreeLimits.SAVED_ROSTERS, entitlements.savedRosterCapacity)
        assertTrue(entitlements.showsAds)
        assertFalse(entitlements.hasThemes)
        assertFalse(entitlements.hasActiveMemberToggle)
    }

    @Test
    fun `Plus lifts every cap and stops the ads`() {
        val entitlements = EntitlementStore(FakePrefs())
        entitlements.setPlus(true)

        assertEquals(Int.MAX_VALUE, entitlements.savedWheelCapacity)
        assertEquals(Int.MAX_VALUE, entitlements.savedRosterCapacity)
        assertFalse(entitlements.showsAds)
        assertTrue(entitlements.hasThemes)
        assertTrue(entitlements.hasActiveMemberToggle)
    }

    @Test
    fun `the entitlement survives a relaunch`() {
        // The cache is authoritative offline on purpose: this app is played in
        // pubs and on trains, and a paying user with no signal must not lose
        // what they bought.
        val prefs = FakePrefs()
        EntitlementStore(prefs).setPlus(true)

        assertTrue(EntitlementStore(prefs).hasPlus)
    }

    @Test
    fun `a refund revokes Plus`() {
        val prefs = FakePrefs()
        val entitlements = EntitlementStore(prefs)
        entitlements.setPlus(true)

        entitlements.setPlus(false)

        assertFalse(entitlements.hasPlus)
        assertFalse("and it stays revoked", EntitlementStore(prefs).hasPlus)
        assertEquals(FreeLimits.SAVED_WHEELS, entitlements.savedWheelCapacity)
    }

    // region Grandfathering

    @Test
    fun `a library built before the cap keeps its size forever`() {
        val entitlements = EntitlementStore(FakePrefs())

        entitlements.grandfatherIfNeeded(
            wheelCount = FreeLimits.SAVED_WHEELS + 2,
            rosterCount = 1,
        )

        assertTrue(entitlements.isLegacy)
        assertEquals(Int.MAX_VALUE, entitlements.savedWheelCapacity)
        assertEquals(Int.MAX_VALUE, entitlements.savedRosterCapacity)
    }

    @Test
    fun `legacy does not hand out the paid product`() {
        val entitlements = EntitlementStore(FakePrefs())
        entitlements.grandfatherIfNeeded(wheelCount = 99, rosterCount = 99)

        // Legacy exists to avoid taking something away, not to give away Plus
        // to anybody who once installed the app.
        assertFalse(entitlements.hasPlus)
        assertTrue(entitlements.showsAds)
        assertFalse(entitlements.hasThemes)
    }

    @Test
    fun `everybody arriving from the store is treated as new`() {
        val entitlements = EntitlementStore(FakePrefs())
        entitlements.grandfatherIfNeeded(wheelCount = 1, rosterCount = 1)

        assertFalse(entitlements.isLegacy)
        assertEquals(FreeLimits.SAVED_WHEELS, entitlements.savedWheelCapacity)
    }

    @Test
    fun `grandfathering runs once and never again`() {
        val prefs = FakePrefs()
        EntitlementStore(prefs).grandfatherIfNeeded(wheelCount = 1, rosterCount = 1)

        // Somebody who later reaches the cap the ordinary way must not be
        // handed an unlimited library for it.
        val later = EntitlementStore(prefs)
        later.grandfatherIfNeeded(wheelCount = 99, rosterCount = 99)

        assertFalse(later.isLegacy)
    }

    // endregion

    // region What the caps actually do

    @Test
    fun `the free cap refuses a fourth wheel and keeps the first three`() {
        val store = WheelStore(FakePrefs())
        store.capacity = FreeLimits.SAVED_WHEELS

        // The store opens on one starter wheel.
        while (store.canCreateWheel) {
            assertNotNull(store.createWheel("Wheel ${store.wheels.size + 1}"))
        }

        assertEquals(FreeLimits.SAVED_WHEELS, store.wheels.size)
        assertNull("the fourth is refused", store.createWheel("One too many"))
        assertFalse("and so is a copy", store.duplicateSelected())
        assertEquals(FreeLimits.SAVED_WHEELS, store.wheels.size)
    }

    @Test
    fun `dropping back to free never deletes anything`() {
        val store = WheelStore(FakePrefs())
        store.capacity = Int.MAX_VALUE
        repeat(5) { store.createWheel("Wheel $it") }
        val built = store.wheels.size

        // A refund, or a restore that found nothing.
        store.capacity = FreeLimits.SAVED_WHEELS

        assertEquals("every wheel is still there", built, store.wheels.size)
        assertFalse("the library is simply frozen at its size", store.canCreateWheel)
    }

    @Test
    fun `sitting somebody out is ignored without Plus`() {
        val store = RosterStore(FakePrefs())
        store.honoursActiveFlags = false
        val victim = store.members.first()

        store.setActive(victim.id, false)

        // The flag is written either way — it stays on disk and starts applying
        // again the moment Plus comes back — but it must not apply now.
        assertFalse(store.members.first().isActive)
        assertEquals(store.members.size, store.activeMembers.size)
        assertTrue(store.names.contains(victim.name))
    }

    @Test
    fun `sitting somebody out takes them out of the game with Plus`() {
        val store = RosterStore(FakePrefs())
        store.honoursActiveFlags = true
        val seated = store.members.size
        val victim = store.members.first()

        store.setActive(victim.id, false)

        assertEquals(seated - 1, store.activeMembers.size)
        assertFalse(store.names.contains(victim.name))
        assertEquals("but nobody was deleted", seated, store.members.size)
    }

    @Test
    fun `sitting somebody out moves everybody below them up a seat`() {
        // What the editor's colour swatches depend on. The games seat from
        // activeMembers, so a row's own position stops being its seat the moment
        // anyone above it is switched off — and a swatch read from the row index
        // would then name the wrong ring.
        val store = RosterStore(FakePrefs())
        store.honoursActiveFlags = true
        val (first, second, third) = Triple(
            store.members[0],
            store.members[1],
            store.members[2],
        )

        store.setActive(first.id, false)

        assertEquals("nobody sits in a seat that was given up", -1, seatOf(store, first))
        assertEquals("the second player takes the first seat", 0, seatOf(store, second))
        assertEquals(1, seatOf(store, third))
    }

    private fun seatOf(store: RosterStore, member: com.jesperhaafkes.caster.domain.RosterMember) =
        store.activeMembers.indexOfFirst { it.id == member.id }

    @Test
    fun `who is sitting out survives a relaunch`() {
        val prefs = FakePrefs()
        val first = RosterStore(prefs)
        val victimId = first.members.first().id
        first.setActive(victimId, false)

        val second = RosterStore(prefs)
        assertFalse(second.members.first { it.id == victimId }.isActive)
    }

    // endregion
}
