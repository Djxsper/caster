import SwiftUI

/// Finger Picker with a lever. Claim a circle, then hammer it for five seconds:
/// every tap drags your odds down. It never drags them to zero — the top tapper
/// still carries real weight in the draw, so nobody buys their way out.
struct TapFrenzyView: View {
    private enum Phase {
        case claiming
        case countdown
        case tapping
        case revealing
        case finished
    }

    @Environment(AppEnvironment.self) private var environment
    @Environment(\.theme) private var theme

    @State private var arena = TouchArena()
    @State private var phase: Phase = .claiming
    @State private var roundSlots: [Int] = []
    @State private var taps: [Int: Int] = [:]
    @State private var countdownValue = 3
    @State private var timeRemaining: Double = 0
    @State private var spotlightSlot: Int?
    @State private var loserSlot: Int?
    @State private var settleTask: Task<Void, Never>?
    @State private var roundTask: Task<Void, Never>?

    private let drawEngine = DrawEngine()
    private let settleDuration: Double = 1.5
    private let tapWindow: Double = 5.0
    /// The floor on a top tapper's weight, and the extra weight a player with
    /// no taps at all carries. The ratio between them is the most tapping can
    /// ever buy you: five to one.
    private let baseWeight = 10
    private let deficitWeight = 40

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            TouchSurface(arena: arena) { _ in
                ringLayer
            }

            VStack(spacing: 12) {
                headline
                    .padding(.top, 6)

                Spacer(minLength: 0)

                if phase == .revealing || phase == .finished {
                    oddsTable
                        .padding(.horizontal, 16)
                }

                StatusLine(text: statusText, emphasis: statusTint)

                footer
                    .padding(.horizontal, 16)
                    .padding(.bottom, 20)
            }
            .padding(.top, 6)
        }
        .navigationTitle(GameMode.tapFrenzy.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: configure)
        .onDisappear(perform: teardown)
    }

    // MARK: - Play area

    @ViewBuilder
    private var headline: some View {
        switch phase {
        case .countdown:
            Text("\(countdownValue)")
                .font(.system(size: 84, weight: .heavy, design: .rounded))
                .foregroundStyle(theme.accent)
                .contentTransition(.numericText(countsDown: true))
                .animation(.snappy, value: countdownValue)
        case .tapping:
            Text(String(format: "%.1f", max(0, timeRemaining)))
                .font(.system(size: 64, weight: .heavy, design: .rounded))
                .foregroundStyle(timeRemaining < 1.5 ? theme.danger : theme.textPrimary)
                .monospacedDigit()
        default:
            Color.clear.frame(height: 1)
        }
    }

    @ViewBuilder
    private var ringLayer: some View {
        if phase == .claiming {
            ForEach(arena.activeFingers) { finger in
                FingerRing(color: theme.playerColor(for: finger.slot), diameter: 80)
                    .position(finger.location)
            }
        } else {
            ForEach(roundSlots, id: \.self) { slot in
                if let anchor = arena.anchor(for: slot) {
                    FingerRing(
                        color: ringColor(for: slot),
                        diameter: 80,
                        isHighlighted: slot == spotlightSlot || slot == loserSlot,
                        isDimmed: loserSlot != nil && slot != loserSlot,
                        badge: badge(for: slot)
                    )
                    .position(anchor)
                }
            }
        }
    }

    private var oddsTable: some View {
        VStack(spacing: 6) {
            ForEach(oddsRows) { row in
                HStack(spacing: 10) {
                    Circle()
                        .fill(theme.playerColor(for: row.slot))
                        .frame(width: 14, height: 14)

                    Text("Player \(row.slot + 1)")
                        .font(.system(.subheadline, design: .rounded))
                        .foregroundStyle(theme.textPrimary)

                    Text("\(row.taps) taps")
                        .font(.system(.caption, design: .rounded))
                        .foregroundStyle(theme.textSecondary)

                    Spacer(minLength: 8)

                    Text("\(Int((row.share * 100).rounded()))%")
                        .font(.system(.subheadline, design: .rounded).weight(.bold))
                        .foregroundStyle(row.slot == loserSlot ? theme.danger : theme.textSecondary)
                }
            }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 16)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(theme.surfaceRaised)
        )
    }

    @ViewBuilder
    private var footer: some View {
        switch phase {
        case .claiming:
            EmptyView()
        case .countdown, .tapping, .revealing:
            SecondaryButton(title: "Reset", action: resetRound)
        case .finished:
            PrimaryButton(title: "Play Again", action: resetRound)
        }
    }

    // MARK: - Derived

    /// One row per player, worst odds first. `share` is the real probability of
    /// being picked, straight from the weights the draw uses.
    struct OddsRow: Identifiable {
        let slot: Int
        let taps: Int
        let share: Double

        var id: Int { slot }
    }

    private var oddsRows: [OddsRow] {
        let weights = lossWeights
        let total = max(1, weights.reduce(0, +))
        let rows = roundSlots.enumerated().map { index, slot in
            OddsRow(
                slot: slot,
                taps: taps[slot] ?? 0,
                share: Double(weights[index]) / Double(total)
            )
        }
        return rows.sorted { $0.share > $1.share }
    }

    /// Fewer taps means more weight in the draw for the loss. The top tapper
    /// keeps `baseWeight`, so their odds shrink but never vanish.
    private var lossWeights: [Int] {
        let counts = roundSlots.map { taps[$0] ?? 0 }
        let best = counts.max() ?? 0
        guard best > 0 else { return counts.map { _ in baseWeight } }
        return counts.map { count in
            let deficit = Double(best - count) / Double(best)
            return baseWeight + Int((Double(deficitWeight) * deficit).rounded())
        }
    }

    private func ringColor(for slot: Int) -> Color {
        if let loserSlot { return slot == loserSlot ? theme.danger : theme.playerColor(for: slot) }
        if slot == spotlightSlot { return theme.warning }
        return theme.playerColor(for: slot)
    }

    private func badge(for slot: Int) -> String? {
        switch phase {
        case .claiming, .countdown:
            return nil
        default:
            return "\(taps[slot] ?? 0)"
        }
    }

    private var statusText: String {
        switch phase {
        case .claiming:
            let count = arena.activeCount
            if count == 0 { return "Everyone place a finger to claim a circle" }
            if count == 1 { return "One circle claimed — needs at least two" }
            return "Hold still…"
        case .countdown:
            return "Lift off. Tap your own circle when it hits zero."
        case .tapping:
            return "TAP!"
        case .revealing:
            return "Drawing…"
        case .finished:
            guard let loserSlot else { return "Round complete." }
            return "Player \(loserSlot + 1) loses."
        }
    }

    private var statusTint: Color? {
        switch phase {
        case .tapping: return theme.accent
        case .finished: return theme.danger
        default: return nil
        }
    }

    // MARK: - Round flow

    private func configure() {
        environment.hapticEngine.startEngine()
        environment.soundEngine.start()
        // Generous radius: taps land near a circle, not on it. Nearest anchor
        // still wins, so neighbours sitting close together stay separable.
        arena.slotPolicy = .sticky(radius: 130)
        arena.reset()

        arena.onBegan = { finger in
            switch phase {
            case .claiming:
                environment.cue(.light, .place)
                scheduleStart()
            case .tapping:
                registerTap(slot: finger.slot)
            default:
                break
            }
        }

        arena.onEnded = { _ in
            if phase == .claiming { scheduleStart() }
        }
    }

    private func teardown() {
        settleTask?.cancel()
        roundTask?.cancel()
        settleTask = nil
        roundTask = nil
        arena.reset()
    }

    private func scheduleStart() {
        settleTask?.cancel()
        settleTask = nil
        guard phase == .claiming, arena.activeCount >= 2 else { return }

        settleTask = Task {
            guard (try? await Task.sleep(for: .seconds(settleDuration))) != nil else { return }
            beginRound()
        }
    }

    private func beginRound() {
        guard phase == .claiming, arena.activeCount >= 2 else { return }

        roundSlots = arena.occupiedSlots.sorted()
        taps = [:]
        loserSlot = nil
        spotlightSlot = nil
        countdownValue = 3
        // From here every touch is a tap for an existing circle, never a new one.
        arena.acceptsNewSlots = false
        phase = .countdown

        roundTask = Task {
            for pip in stride(from: 3, through: 1, by: -1) {
                countdownValue = pip
                environment.cue(.medium, .pip)
                guard (try? await Task.sleep(for: .seconds(1))) != nil else { return }
            }

            phase = .tapping
            timeRemaining = tapWindow
            environment.cue(.heavy, .cue)

            let started = Date()
            while !Task.isCancelled {
                let elapsed = Date().timeIntervalSince(started)
                timeRemaining = max(0, tapWindow - elapsed)
                if elapsed >= tapWindow { break }
                guard (try? await Task.sleep(for: .milliseconds(50))) != nil else { return }
            }

            guard !Task.isCancelled else { return }
            await runReveal()
        }
    }

    private func registerTap(slot: Int) {
        guard roundSlots.contains(slot) else { return }
        taps[slot, default: 0] += 1
        environment.hapticEngine.playFeedback(type: .light)
    }

    /// A short spotlight sweep before the answer, slowing as it goes. The loser
    /// is drawn first; the sweep is theatre laid over a decision already made.
    private func runReveal() async {
        phase = .revealing
        environment.cue(.medium, .reveal)

        guard !roundSlots.isEmpty else { return }
        let weights = lossWeights
        guard let index = drawEngine.drawIndexWithWeights(weights: weights, limit: roundSlots.count) else {
            return
        }
        let loser = roundSlots[index]

        var delay: Double = 0.06
        var elapsed: Double = 0
        var cursor = 0
        while elapsed < 1.8, !Task.isCancelled {
            spotlightSlot = roundSlots[cursor % roundSlots.count]
            cursor += 1
            environment.hapticEngine.playFeedback(type: .light)
            guard (try? await Task.sleep(for: .seconds(delay))) != nil else { return }
            elapsed += delay
            delay = min(0.26, delay * 1.16)
        }

        guard !Task.isCancelled else { return }
        spotlightSlot = nil
        withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
            loserSlot = loser
            phase = .finished
        }
        environment.cue(.heavy, .miss)
    }

    private func resetRound() {
        teardown()
        phase = .claiming
        roundSlots = []
        taps = [:]
        loserSlot = nil
        spotlightSlot = nil
        timeRemaining = 0
        countdownValue = 3
    }
}

#Preview {
    NavigationStack {
        TapFrenzyView()
            .environment(AppEnvironment())
    }
}
