import Foundation
import CoreGraphics
import Observation

/// A single live finger on the glass.
struct ArenaFinger: Identifiable, Equatable {
    /// Unique for the lifetime of this touch. Minted by `MultiTouchWrapper`,
    /// because UIKit recycles `UITouch` objects and object identity therefore
    /// collides across gestures.
    let id: Int
    /// The seat this finger drives. Survives a lift-and-replace when the
    /// policy is `.sticky`.
    let slot: Int
    var location: CGPoint
    var startLocation: CGPoint
    /// Both stamped from `UITouch.timestamp`, which is the time the event
    /// actually happened rather than the time this code got to look at it.
    /// A reaction game measured off `Date()` in a callback is measuring the
    /// main thread, not the player.
    var startTime: TimeInterval
    var endTime: TimeInterval = 0

    /// How far this finger has drifted from where it landed, in points.
    var displacement: CGFloat {
        hypot(location.x - startLocation.x, location.y - startLocation.y)
    }
}

/// Owns the set of live touches and maps each one to a *slot* — the thing the
/// games actually score. Two policies cover every mode in the app:
///
/// - `.sequential` hands out the lowest free slot and forgets it the moment the
///   finger lifts. A Chwazi-style picker wants exactly that: lift a finger and
///   its colour is genuinely gone.
/// - `.sticky` remembers where each slot's finger was, so someone putting their
///   finger back down in roughly the same place gets their own slot back rather
///   than stealing a neighbour's. The reaction games need this, because fingers
///   come and go mid-round.
@Observable
@MainActor
final class TouchArena {
    enum SlotPolicy: Equatable {
        case sequential
        /// - Parameter radius: how far, in points, a returning finger may land
        ///   from a slot's last known position and still reclaim it.
        case sticky(radius: CGFloat)
    }

    var slotPolicy: SlotPolicy = .sequential
    /// When false, a touch that cannot reclaim an existing slot is dropped
    /// instead of opening a new one — which is how a round stops accepting
    /// latecomers once it is under way.
    var acceptsNewSlots = true

    private(set) var fingers: [Int: ArenaFinger] = [:]
    /// Last known position per slot. Under `.sticky` this outlives the finger.
    private(set) var anchors: [Int: CGPoint] = [:]
    /// Slots taken out of play for good — a Chicken player who got out safe.
    private(set) var retiredSlots: Set<Int> = []

    // Explicitly main-actor closures: they are invoked from touch handling and
    // they touch view state, so the isolation belongs in the type.
    var onBegan: (@MainActor (ArenaFinger) -> Void)?
    var onMoved: (@MainActor (ArenaFinger) -> Void)?
    var onEnded: (@MainActor (ArenaFinger) -> Void)?
    /// A touch that could not be given a slot, reported so a game can say
    /// "hands off" instead of silently swallowing it.
    var onRejected: (@MainActor (CGPoint) -> Void)?

    var activeCount: Int { fingers.count }

    /// Live touches in a stable order — oldest finger first.
    var activeFingers: [ArenaFinger] {
        fingers.values.sorted { lhs, rhs in
            lhs.startTime == rhs.startTime ? lhs.id < rhs.id : lhs.startTime < rhs.startTime
        }
    }

    var occupiedSlots: Set<Int> {
        Set(fingers.values.map(\.slot))
    }

    /// Every slot that still counts: one with a finger on it, or one holding an
    /// anchor it can be reclaimed from. Retired slots are excluded.
    var slotsInPlay: [Int] {
        var slots = occupiedSlots
        slots.formUnion(anchors.keys)
        slots.subtract(retiredSlots)
        return slots.sorted()
    }

    func finger(inSlot slot: Int) -> ArenaFinger? {
        fingers.values.first { $0.slot == slot }
    }

    func anchor(for slot: Int) -> CGPoint? {
        anchors[slot]
    }

    func isSlotHeld(_ slot: Int) -> Bool {
        fingers.values.contains { $0.slot == slot }
    }

    // MARK: - Touch intake

    func touchBegan(id: Int, location: CGPoint, timestamp: TimeInterval) {
        guard fingers[id] == nil else { return }
        guard let slot = assignSlot(for: location) else {
            onRejected?(location)
            return
        }

        let finger = ArenaFinger(
            id: id,
            slot: slot,
            location: location,
            startLocation: location,
            startTime: timestamp
        )
        fingers[id] = finger
        anchors[slot] = location
        onBegan?(finger)
    }

    func touchMoved(id: Int, location: CGPoint) {
        // A move for an unknown id means we missed the began event; ignore it
        // rather than fabricating a touch with a bogus start position.
        guard var finger = fingers[id] else { return }
        finger.location = location
        fingers[id] = finger
        anchors[finger.slot] = location
        onMoved?(finger)
    }

    func touchEnded(id: Int, location: CGPoint, timestamp: TimeInterval) {
        guard var finger = fingers.removeValue(forKey: id) else { return }
        finger.location = location
        finger.endTime = timestamp
        if case .sequential = slotPolicy {
            // Sequential slots have no memory: free the colour immediately.
            anchors.removeValue(forKey: finger.slot)
        } else {
            anchors[finger.slot] = location
        }
        onEnded?(finger)
    }

    // MARK: - Slot bookkeeping

    /// Takes a slot out of play. Its anchor is dropped so a stray touch in the
    /// same spot cannot reclaim it.
    func retire(slot: Int) {
        retiredSlots.insert(slot)
        anchors.removeValue(forKey: slot)
        for (id, finger) in fingers where finger.slot == slot {
            fingers.removeValue(forKey: id)
        }
    }

    /// Drops every touch, anchor and retirement. Called when a round starts or
    /// ends so no finger stays "down" after the view tracking it goes away.
    func reset() {
        fingers.removeAll()
        anchors.removeAll()
        retiredSlots.removeAll()
        acceptsNewSlots = true
    }

    /// Keeps the slots and their anchors but forgets the live fingers — for the
    /// phase changes where everyone lifts off and comes back.
    func clearFingers() {
        fingers.removeAll()
    }

    private func assignSlot(for location: CGPoint) -> Int? {
        switch slotPolicy {
        case .sequential:
            return acceptsNewSlots ? lowestFreeSlot() : nil
        case .sticky(let radius):
            if let reclaimed = nearestFreeAnchor(to: location, within: radius) {
                return reclaimed
            }
            return acceptsNewSlots ? lowestFreeSlot() : nil
        }
    }

    private func lowestFreeSlot() -> Int {
        let occupied = occupiedSlots
        var candidate = 0
        while occupied.contains(candidate)
                || anchors[candidate] != nil
                || retiredSlots.contains(candidate) {
            candidate += 1
        }
        return candidate
    }

    /// The nearest slot that has an anchor, no finger on it, and has not been
    /// retired — provided the new touch landed close enough to claim it.
    private func nearestFreeAnchor(to location: CGPoint, within radius: CGFloat) -> Int? {
        let occupied = occupiedSlots
        var bestSlot: Int?
        var bestDistance = CGFloat.greatestFiniteMagnitude

        for (slot, anchor) in anchors {
            guard !occupied.contains(slot), !retiredSlots.contains(slot) else { continue }
            let distance = hypot(anchor.x - location.x, anchor.y - location.y)
            if distance < bestDistance {
                bestDistance = distance
                bestSlot = slot
            }
        }

        guard let slot = bestSlot, bestDistance <= radius else { return nil }
        return slot
    }
}
