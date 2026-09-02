package com.jesperhaafkes.caster.touch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single live finger on the glass.
 */
data class ArenaFinger(
    /**
     * Unique for the lifetime of this touch. Compose mints a fresh `PointerId`
     * per gesture, so unlike UIKit's recycled `UITouch` there is nothing to map
     * around here.
     */
    val id: Long,
    /**
     * The seat this finger drives. Survives a lift-and-replace when the policy
     * is [SlotPolicy.Sticky].
     */
    val slot: Int,
    val location: Offset,
    val startLocation: Offset,
    /**
     * Both stamped from the pointer event's own `uptimeMillis`, which is the
     * time the event actually happened rather than the time this code got to
     * look at it. A reaction game measured off a clock read inside a callback
     * is measuring the main thread, not the player.
     */
    val startTime: Long,
    val endTime: Long = 0L,
) {
    /** How far this finger has drifted from where it landed, in pixels. */
    val displacement: Float
        get() = (location - startLocation).getDistance()
}

/**
 * Owns the set of live touches and maps each one to a *slot* — the thing the
 * games actually score. Two policies cover every mode in the app:
 *
 * - [SlotPolicy.Sequential] hands out the lowest free slot and forgets it the
 *   moment the finger lifts. A Chwazi-style picker wants exactly that: lift a
 *   finger and its colour is genuinely gone.
 * - [SlotPolicy.Sticky] remembers where each slot's finger was, so someone
 *   putting their finger back down in roughly the same place gets their own
 *   slot back rather than stealing a neighbour's. The reaction games need this,
 *   because fingers come and go mid-round.
 */
class TouchArena {

    sealed interface SlotPolicy {
        data object Sequential : SlotPolicy

        /**
         * @param radius how far a returning finger may land from a slot's last
         *   known position and still reclaim it.
         */
        data class Sticky(val radius: Dp) : SlotPolicy
    }

    var slotPolicy: SlotPolicy by mutableStateOf(SlotPolicy.Sequential)

    /**
     * When false, a touch that cannot reclaim an existing slot is dropped
     * instead of opening a new one — which is how a round stops accepting
     * latecomers once it is under way.
     */
    var acceptsNewSlots: Boolean by mutableStateOf(true)

    /**
     * Set by [TouchSurface] before any touch is delivered. Pointer positions
     * arrive in pixels while a sticky radius is authored in dp, and this is
     * what converts between them.
     */
    var density: Density? = null

    private val _fingers = mutableStateMapOf<Long, ArenaFinger>()

    /** Last known position per slot. Under a sticky policy this outlives the finger. */
    private val _anchors = mutableStateMapOf<Int, Offset>()

    /** Slots taken out of play for good — a Chicken player who got out safe. */
    private var _retiredSlots: Set<Int> by mutableStateOf(emptySet())

    var onBegan: ((ArenaFinger) -> Unit)? = null
    var onMoved: ((ArenaFinger) -> Unit)? = null
    var onEnded: ((ArenaFinger) -> Unit)? = null

    /**
     * A touch that could not be given a slot, reported so a game can say
     * "hands off" instead of silently swallowing it.
     */
    var onRejected: ((Offset) -> Unit)? = null

    val activeCount: Int get() = _fingers.size

    /** Live touches in a stable order — oldest finger first. */
    val activeFingers: List<ArenaFinger>
        get() = _fingers.values.sortedWith(compareBy({ it.startTime }, { it.id }))

    val occupiedSlots: Set<Int>
        get() = _fingers.values.mapTo(mutableSetOf()) { it.slot }

    val retiredSlots: Set<Int> get() = _retiredSlots

    /**
     * Every slot that still counts: one with a finger on it, or one holding an
     * anchor it can be reclaimed from. Retired slots are excluded.
     */
    val slotsInPlay: List<Int>
        get() = (occupiedSlots + _anchors.keys - _retiredSlots).sorted()

    fun finger(inSlot: Int): ArenaFinger? = _fingers.values.firstOrNull { it.slot == inSlot }

    fun anchor(slot: Int): Offset? = _anchors[slot]

    fun isSlotHeld(slot: Int): Boolean = _fingers.values.any { it.slot == slot }

    // region Touch intake

    fun touchBegan(id: Long, location: Offset, timestamp: Long) {
        if (_fingers.containsKey(id)) return
        val slot = assignSlot(location)
        if (slot == null) {
            onRejected?.invoke(location)
            return
        }

        val finger = ArenaFinger(
            id = id,
            slot = slot,
            location = location,
            startLocation = location,
            startTime = timestamp,
        )
        _fingers[id] = finger
        _anchors[slot] = location
        onBegan?.invoke(finger)
    }

    fun touchMoved(id: Long, location: Offset) {
        // A move for an unknown id means we missed the down event; ignore it
        // rather than fabricating a touch with a bogus start position.
        val existing = _fingers[id] ?: return
        val finger = existing.copy(location = location)
        _fingers[id] = finger
        _anchors[finger.slot] = location
        onMoved?.invoke(finger)
    }

    fun touchEnded(id: Long, location: Offset, timestamp: Long) {
        val existing = _fingers.remove(id) ?: return
        val finger = existing.copy(location = location, endTime = timestamp)
        if (slotPolicy is SlotPolicy.Sequential) {
            // Sequential slots have no memory: free the colour immediately.
            _anchors.remove(finger.slot)
        } else {
            _anchors[finger.slot] = location
        }
        onEnded?.invoke(finger)
    }

    // endregion

    // region Slot bookkeeping

    /**
     * Takes a slot out of play. Its anchor is dropped so a stray touch in the
     * same spot cannot reclaim it.
     */
    fun retire(slot: Int) {
        _retiredSlots = _retiredSlots + slot
        _anchors.remove(slot)
        val doomed = _fingers.entries.filter { it.value.slot == slot }.map { it.key }
        for (id in doomed) _fingers.remove(id)
    }

    /**
     * Drops every touch, anchor and retirement. Called when a round starts or
     * ends so no finger stays "down" after the screen tracking it goes away.
     */
    fun reset() {
        _fingers.clear()
        _anchors.clear()
        _retiredSlots = emptySet()
        acceptsNewSlots = true
    }

    /**
     * Keeps the slots and their anchors but forgets the live fingers — for the
     * phase changes where everyone lifts off and comes back.
     */
    fun clearFingers() {
        _fingers.clear()
    }

    /**
     * Ends every live finger, as though each had lifted at [timestamp]. This is
     * the cancellation path: the system takes the pointer stream away mid-round
     * (a notification shade, an incoming call, a gesture the parent claims) and
     * no up event is ever delivered for the fingers still down.
     *
     * UIKit gets `touchesCancelled` for exactly this and routes it into the same
     * `touchEnded` as a real lift — see `MultiTouchView.swift:91-95`, whose
     * comment notes that handling only `ended` "leaks a stuck finger down". A
     * leaked finger is not cosmetic: it holds a slot that reports
     * [isSlotHeld] forever, so Chicken can never flash it and Uppercut waits for
     * a reaction that cannot arrive.
     */
    fun endAllFingers(timestamp: Long) {
        // Snapshot first: touchEnded mutates _fingers as it goes.
        for (finger in _fingers.values.toList()) {
            touchEnded(id = finger.id, location = finger.location, timestamp = timestamp)
        }
    }

    private fun assignSlot(location: Offset): Int? = when (val policy = slotPolicy) {
        is SlotPolicy.Sequential -> if (acceptsNewSlots) lowestFreeSlot() else null
        is SlotPolicy.Sticky -> {
            nearestFreeAnchor(location, policy.radius)
                ?: if (acceptsNewSlots) lowestFreeSlot() else null
        }
    }

    private fun lowestFreeSlot(): Int {
        val occupied = occupiedSlots
        var candidate = 0
        while (candidate in occupied || _anchors.containsKey(candidate) || candidate in _retiredSlots) {
            candidate += 1
        }
        return candidate
    }

    /**
     * The nearest slot that has an anchor, no finger on it, and has not been
     * retired — provided the new touch landed close enough to claim it.
     */
    private fun nearestFreeAnchor(location: Offset, radius: Dp): Int? {
        val radiusPx = density?.run { radius.toPx() } ?: (radius.value * DEFAULT_DENSITY)
        val occupied = occupiedSlots
        var bestSlot: Int? = null
        var bestDistance = Float.MAX_VALUE

        for ((slot, anchor) in _anchors) {
            if (slot in occupied || slot in _retiredSlots) continue
            val distance = (anchor - location).getDistance()
            if (distance < bestDistance) {
                bestDistance = distance
                bestSlot = slot
            }
        }

        return bestSlot?.takeIf { bestDistance <= radiusPx }
    }

    // endregion

    private companion object {
        /** Only reached if a touch beats [density] being set, which it should not. */
        const val DEFAULT_DENSITY = 2.75f
    }
}

/** Convenience for the games, which all author their radius in dp. */
fun stickyRadius(dp: Int): TouchArena.SlotPolicy.Sticky = TouchArena.SlotPolicy.Sticky(dp.dp)
