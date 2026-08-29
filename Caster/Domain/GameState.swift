import Foundation
import Observation

/// Seat limits. Kept off `GameState` so they can be read from non-isolated
/// contexts such as a SwiftUI property's default value.
enum PlayerLimits {
    static let minimum = 1
    static let maximum = 8
}

/// The single source of truth for a session. Created once by `CasterApp` and
/// injected into the view tree, so every screen reads and writes the same
/// instance instead of each view holding its own private copy.
@Observable
@MainActor
final class GameState {
    enum Phase {
        case idle
        case preCue
        case drawing
        case result
    }

    private(set) var phase: Phase = .idle
    var currentMode: GameMode = .fingerPicker
    private(set) var players: [Player] = []
    var currentPlayerIndex = 0
    var isDrawn = false
    var timeRemaining: TimeInterval = 0
    var isReducedMotion = false

    /// Ticket totals for the round that just resolved, highest first.
    private(set) var lastResults: [TicketResult] = []

    private let drawEngine = DrawEngine()

    // MARK: - Setup

    func configurePlayers(count: Int, names: [String]) {
        let clampedCount = min(max(count, PlayerLimits.minimum), PlayerLimits.maximum)
        players = (0..<clampedCount).map { seat in
            let trimmed = seat < names.count
                ? names[seat].trimmingCharacters(in: .whitespacesAndNewlines)
                : ""
            return Player(seat: seat, name: trimmed.isEmpty ? nil : trimmed)
        }
        phase = .idle
        lastResults = []
    }

    // MARK: - Round lifecycle

    func beginRound() {
        guard !players.isEmpty, phase == .idle || phase == .result else { return }
        for player in players { player.resetForNewRound() }
        lastResults = []
        isDrawn = false
        phase = .preCue
    }

    func startDrawing() {
        guard phase == .preCue else { return }
        for player in players {
            player.isReady = true
            player.isHolding = true
        }
        phase = .drawing
    }

    /// Resolves the round: scores every player, then picks a loser with a
    /// weighted draw where fewer tickets means a higher chance of losing.
    func resolveRound() {
        guard phase == .drawing else { return }

        let scored = drawEngine.computeTicketCounts(for: players, mode: currentMode)
        for entry in scored {
            entry.player.totalTickets += entry.tickets
            entry.player.ticketDisplayValue = entry.tickets
            entry.player.isHolding = false
        }

        let maxTickets = scored.map(\.tickets).max() ?? 0
        // Invert so the lowest ticket count carries the most weight to lose.
        let lossWeights = scored.map { (maxTickets - $0.tickets) + 1 }
        if let loser = drawEngine.drawWithWeights(array: scored.map(\.player), weights: lossWeights) {
            loser.isLoss = true
            loser.lossCount += 1
            for player in players where player !== loser {
                player.isWin = true
                player.winCount += 1
            }
        }

        lastResults = scored.sorted { $0.tickets > $1.tickets }
        isDrawn = true
        phase = .result
    }

    func returnToIdle() {
        phase = .idle
        isDrawn = false
    }
}
