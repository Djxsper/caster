import Foundation
import Observation

/// A named participant. Only the modes that need names up front — Hot Potato
/// and (optionally) Pinwheel — use these; the touch games identify people by
/// where their finger landed instead.
@Observable
final class Player: Identifiable {
    let id = UUID()
    /// Seat index, 0-based. Drives the player's colour so the colour survives
    /// re-ordering of the `players` array.
    let seat: Int

    var name: String
    var winCount = 0
    var lossCount = 0

    init(seat: Int, name: String? = nil) {
        self.seat = seat
        self.name = name ?? "Player \(seat + 1)"
    }
}
