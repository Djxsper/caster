import Foundation
import CoreGraphics

/// One player's score for a round. A named type rather than a labelled tuple:
/// key paths cannot address tuple elements, and tuple labels do not convert
/// reliably through generic positions such as `Array`.
struct TicketResult: Identifiable {
    let player: Player
    let tickets: Int

    var id: UUID { player.id }
}

/// Pure scoring / random-selection logic. Deliberately free of UI and of
/// stored state so it can be unit-tested in isolation.
struct DrawEngine {

    /// Picks one element with probability proportional to its weight.
    /// Returns `nil` only when there is nothing selectable.
    func drawWithWeights<T>(array: [T], weights: [Int]) -> T? {
        guard !array.isEmpty, !weights.isEmpty else { return nil }

        // Ignore any trailing weights that have no matching element (and vice
        // versa) rather than reading past the end of either array.
        let count = min(array.count, weights.count)
        let normalizedWeights = weights.prefix(count).map { max(0, $0) }
        let totalWeight = normalizedWeights.reduce(0, +)

        guard totalWeight > 0 else {
            // Every weight was zero: fall back to a uniform pick so a caller
            // that hands us an all-zero set still gets a result.
            return array[Int.random(in: 0..<count)]
        }

        var accumulated = 0
        let randomNumber = Int.random(in: 0..<totalWeight)

        for (index, weight) in normalizedWeights.enumerated() {
            accumulated += weight
            if randomNumber < accumulated {
                return array[index]
            }
        }

        return array[count - 1]
    }

    /// - Parameters:
    ///   - reactionMilliseconds: time from go-cue to tap, in **milliseconds**.
    ///   - steadiness: drift from the initial touch point, in points. Lower is steadier.
    func calculateBonuses(reactionMilliseconds: Double, steadiness: Double) -> (layer1: Int, layer2: Int) {
        let layer1: Int
        switch reactionMilliseconds {
        case ..<200: layer1 = 3
        case ..<310: layer1 = 2
        case ..<440: layer1 = 1
        default:     layer1 = 0
        }

        let layer2 = max(0, Int(floor((10 - steadiness) / 2.0)))

        return (layer1, layer2)
    }

    /// Accuracy is a 0...1 fraction. The bands are contiguous, so a value such
    /// as 0.25 lands in a real band instead of falling through to the default.
    func getGlobalModifier(accuracy: Double) -> Int {
        switch accuracy {
        case ..<0.25: return -1
        case ..<0.55: return 0
        default:      return 1
        }
    }

    func computeTicketCounts(for players: [Player], mode: GameMode) -> [TicketResult] {
        var results: [TicketResult] = []
        results.reserveCapacity(players.count)

        for player in players {
            // A player who never tapped is treated as the slowest possible.
            let reactionMilliseconds = (player.reactionTime ?? .greatestFiniteMagnitude) * 1000
            let (layer1, layer2) = calculateBonuses(
                reactionMilliseconds: reactionMilliseconds,
                steadiness: Double(player.displacement)
            )

            // Fold the freshly computed bonuses back onto the player so the UI
            // can show the breakdown, then score from those same values.
            player.layer1Bonus = layer1
            player.layer2Bonus = layer2

            let tickets = Self.baseTickets + layer1 + layer2 + player.globalModifier
            results.append(TicketResult(player: player, tickets: max(0, tickets)))
        }

        return results
    }

    static let baseTickets = 10
}
