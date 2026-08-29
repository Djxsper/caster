import Foundation
import Observation

/// Seat limits for the name-based modes. Kept off `GameState` so they can be
/// read from non-isolated contexts such as a SwiftUI property's default value.
enum PlayerLimits {
    static let minimum = 2
    static let maximum = 12
}

/// Session-wide state shared by every screen: which mode is selected and, for
/// the modes that need them, the named players. Created once by `CasterApp`
/// and injected into the view tree.
@Observable
@MainActor
final class GameState {
    var currentMode: GameMode = .fingerPicker
    private(set) var players: [Player] = []

    func configurePlayers(count: Int, names: [String]) {
        let clampedCount = min(max(count, PlayerLimits.minimum), PlayerLimits.maximum)
        players = (0..<clampedCount).map { seat in
            let trimmed = seat < names.count
                ? names[seat].trimmingCharacters(in: .whitespacesAndNewlines)
                : ""
            return Player(seat: seat, name: trimmed.isEmpty ? nil : trimmed)
        }
    }

    func recordLoss(for player: Player) {
        player.lossCount += 1
        for other in players where other !== player {
            other.winCount += 1
        }
    }
}
