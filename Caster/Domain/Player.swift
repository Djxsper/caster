import Foundation
import CoreGraphics
import Observation

@Observable
final class Player: Identifiable {
    let id = UUID()
    /// Seat index, 0-based. Drives the player's colour so the colour survives
    /// re-ordering of the `players` array.
    let seat: Int

    var name: String
    var isReady = false
    var isHolding = false

    var winCount = 0
    var lossCount = 0
    var totalTickets = 0

    var layer1Bonus = 0
    var layer2Bonus = 0
    var globalModifier = 0

    /// Seconds between the go-cue and this player's tap. `nil` until they tap.
    var reactionTime: TimeInterval?
    var currentPosition: CGPoint = .zero
    var initialPosition: CGPoint = .zero
    /// Points of drift from the initial touch-down position.
    var displacement: CGFloat = 0

    var isDead = false
    var ticketDisplayValue = 0
    var isWin = false
    var isLoss = false

    init(seat: Int, name: String? = nil) {
        self.seat = seat
        self.name = name ?? "Player \(seat + 1)"
    }

    /// Clears per-round state while keeping the running score.
    func resetForNewRound() {
        isReady = false
        isHolding = false
        reactionTime = nil
        currentPosition = .zero
        initialPosition = .zero
        displacement = 0
        isDead = false
        ticketDisplayValue = 0
        isWin = false
        isLoss = false
        layer1Bonus = 0
        layer2Bonus = 0
    }
}
