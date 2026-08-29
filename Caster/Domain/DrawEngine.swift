import Foundation

/// Pure selection logic. Deliberately free of UI and of stored state so it can
/// be reasoned about — and unit-tested — in isolation.
struct DrawEngine {

    /// Picks one element with probability proportional to its weight.
    /// Returns `nil` only when there is nothing selectable.
    func drawWithWeights<T>(array: [T], weights: [Int]) -> T? {
        guard let index = drawIndexWithWeights(weights: weights, limit: array.count) else { return nil }
        return array[index]
    }

    /// The index form of `drawWithWeights`, for callers that need to know
    /// *which* slot won rather than just its value.
    func drawIndexWithWeights(weights: [Int], limit: Int) -> Int? {
        let count = min(limit, weights.count)
        guard count > 0 else { return nil }

        let normalizedWeights = weights.prefix(count).map { max(0, $0) }
        let totalWeight = normalizedWeights.reduce(0, +)

        guard totalWeight > 0 else {
            // Every weight was zero: fall back to a uniform pick so a caller
            // that hands us an all-zero set still gets a result.
            return Int.random(in: 0..<count)
        }

        var accumulated = 0
        let randomNumber = Int.random(in: 0..<totalWeight)

        for (index, weight) in normalizedWeights.enumerated() {
            accumulated += weight
            if randomNumber < accumulated {
                return index
            }
        }

        return count - 1
    }

    /// `count` distinct members of `pool`, chosen uniformly. Returns the whole
    /// pool (shuffled) when `count` meets or exceeds its size.
    func pick<T>(_ count: Int, from pool: [T]) -> [T] {
        guard count > 0 else { return [] }
        return Array(pool.shuffled().prefix(count))
    }

    /// Assigns each member of `pool` a team index, balanced to within one
    /// member per team, with the membership shuffled.
    ///
    /// Returned in the same order as `pool`, so element *i* of the result is
    /// the team for element *i* of the input.
    func splitIntoTeams<T>(_ pool: [T], teams: Int) -> [Int] {
        let teamCount = max(1, teams)
        guard !pool.isEmpty else { return [] }

        // Build a balanced bag of team numbers, then shuffle *that* — dealing
        // round-robin from a shuffled pool would bias the last team small.
        var bag: [Int] = (0..<pool.count).map { $0 % teamCount }
        bag.shuffle()
        return bag
    }

    /// A shuffled 1-based ranking, one entry per member of `pool`.
    func randomOrder(count: Int) -> [Int] {
        guard count > 0 else { return [] }
        return Array(1...count).shuffled()
    }
}
