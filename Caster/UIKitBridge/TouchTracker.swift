import Foundation
import CoreGraphics
import Observation

/// A single live finger on screen.
struct TrackedTouch: Identifiable, Equatable {
    let id: Int
    /// Which seat this finger was assigned when it landed, by touch-down order.
    let seat: Int
    var location: CGPoint
    var startLocation: CGPoint
    var startTime: TimeInterval

    /// How far this finger has drifted from where it landed, in points.
    var displacement: CGFloat {
        hypot(location.x - startLocation.x, location.y - startLocation.y)
    }
}

/// Owns the set of active touches. Keyed by a monotonically increasing `Int`
/// rather than `ObjectIdentifier(touch)`: UIKit recycles `UITouch` objects, so
/// object identity is only unique for the lifetime of one touch and silently
/// collides across gestures.
@Observable
@MainActor
final class TouchTracker {
    private(set) var touches: [Int: TrackedTouch] = [:]

    // Explicitly main-actor closures: they are invoked from touch handling and
    // they touch view state, so the isolation belongs in the type.
    var onBegan: (@MainActor (TrackedTouch) -> Void)?
    var onMoved: (@MainActor (TrackedTouch) -> Void)?
    var onEnded: (@MainActor (TrackedTouch) -> Void)?

    /// Seats already handed out this round. Not reused until `reset()`, so a
    /// finger that lifts and lands again does not steal another player's seat.
    private var nextSeat = 0

    var activeCount: Int { touches.count }

    /// Active touches in a stable order - oldest finger first.
    var activeTouches: [TrackedTouch] {
        touches.values.sorted { lhs, rhs in
            lhs.startTime == rhs.startTime ? lhs.id < rhs.id : lhs.startTime < rhs.startTime
        }
    }

    func touchBegan(id: Int, location: CGPoint, timestamp: TimeInterval) {
        guard touches[id] == nil else { return }
        let touch = TrackedTouch(
            id: id,
            seat: nextSeat,
            location: location,
            startLocation: location,
            startTime: timestamp
        )
        nextSeat += 1
        touches[id] = touch
        onBegan?(touch)
    }

    func touchMoved(id: Int, location: CGPoint) {
        // A move for an unknown id means we missed the began event; ignore it
        // rather than fabricating a touch with a bogus start position.
        guard var touch = touches[id] else { return }
        touch.location = location
        touches[id] = touch
        onMoved?(touch)
    }

    func touchEnded(id: Int, location: CGPoint) {
        guard var touch = touches.removeValue(forKey: id) else { return }
        touch.location = location
        onEnded?(touch)
    }

    /// Drops every touch and frees the seats. Called when a round starts or
    /// ends so no finger stays "down" after the view tracking it goes away.
    func reset() {
        touches.removeAll()
        nextSeat = 0
    }
}
