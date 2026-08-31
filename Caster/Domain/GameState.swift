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

    /// Seats the saved roster. Idempotent: when the names already match, the
    /// existing `Player` objects are left alone, so a game can call this on
    /// every appearance without resetting the round it is in the middle of.
    ///
    /// When they differ, anyone still on the roster carries their win/loss
    /// tally over to their new seat — editing one name should not wipe the
    /// running score for everybody else.
    func adoptRoster(_ names: [String]) {
        let seated = Array(
            names
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .prefix(PlayerLimits.maximum)
        )

        guard seated != players.map(\.name) else { return }

        // Keyed by name rather than by seat: the point is to follow a person
        // through a reorder, and a repeated name can only match one of them.
        let previous = Dictionary(players.map { ($0.name, $0) }, uniquingKeysWith: { first, _ in first })
        players = seated.enumerated().map { seat, name in
            let player = Player(seat: seat, name: name)
            if let earlier = previous[name] {
                player.winCount = earlier.winCount
                player.lossCount = earlier.lossCount
            }
            return player
        }
    }

    /// The name for a touch slot. Falls back to the seat number when more
    /// fingers are on the glass than there are names on the roster, so the
    /// touch games stay playable without anyone having typed anything.
    func name(forSlot slot: Int) -> String {
        players.indices.contains(slot) ? players[slot].name : "Player \(slot + 1)"
    }

    func recordLoss(for player: Player) {
        player.lossCount += 1
        for other in players where other !== player {
            other.winCount += 1
        }
    }
}
