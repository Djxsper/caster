import SwiftUI

/// Finger Picker with a lever. Claim a circle, then hammer it for five seconds
/// to lean the draw your way.
///
/// Which way is up to the table. Set it to **Avoid** and tapping shortens your
/// odds of being picked; set it to **Win** and tapping lengthens them, for the
/// times the thing being handed out is worth having. Either way the lever has a
/// floor and a ceiling: the top tapper still carries real weight and the laziest
/// player still has a real chance, so no amount of tapping settles it outright.
struct TapFrenzyView: View {
    /// What the draw is for, and therefore which way the taps push.
    private enum Stake: String, CaseIterable, Identifiable {
        case avoid
        case win

        var id: String { rawValue }

        var label: String {
            switch self {
            case .avoid: return "Avoid It"
            case .win: return "Win It"
            }
        }

        var caption: String {
            switch self {
            case .avoid: return "Tap the most to be safest — never safe."
            case .win: return "Tap the most to be likeliest — never certain."
            }
        }

        var verb: String {
            switch self {
            case .avoid: return "loses"
            case .win: return "wins"
            }
        }
    }

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
    @State private var stake: Stake = .avoid
    @State private var roundSlots: [Int] = []
    @State private var taps: [Int: Int] = [:]
    @State private var countdownValue = 3
    @State private var timeRemaining: Double = 0
    @State private var spotlightSlot: Int?
    /// Whoever the draw landed on. What that means to them depends on `stake`.
    @State private var pickedSlot: Int?
    @State private var settleTask: Task<Void, Never>?
    @State private var roundTask: Task<Void, Never>?

    private let drawEngine = DrawEngine()
    private let settleDuration: Double = 1.5
    private let tapWindow: Double = 5.0
    /// Everyone's floor in the draw, and the most the taps can add on top. The
    /// ratio between them is all the lever is worth: five to one, either way.
    private let baseWeight = 10
    private let swingWeight = 40

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            TouchSurface(arena: arena) { size in
                ZStack {
                    if phase == .claiming, arena.activeCount == 0 {
                        EmptyPlayHint(
                            systemImage: "hand.tap",
                            title: "Claim a circle",
                            detail: "Put a finger down where you want your circle. You will tap it later."
                        )
                        .position(x: size.width / 2, y: size.height / 2)
                        .transition(.opacity)
                    }
                    ringLayer
                }
            }

            VStack(spacing: 12) {
                stakePanel
                    .padding(.horizontal, 16)
                    .opacity(isStakePanelVisible ? 1 : 0)
                    .allowsHitTesting(isStakePanelVisible)
                    .animation(.easeInOut(duration: 0.25), value: isStakePanelVisible)

                headline

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

    // MARK: - Controls

    private var isStakePanelVisible: Bool {
        phase == .claiming && arena.activeCount == 0
    }

    private var stakePanel: some View {
        VStack(spacing: 8) {
            Picker("What the draw decides", selection: $stake) {
                ForEach(Stake.allCases) { option in
                    Text(option.label).tag(option)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: stake) { _, _ in
                environment.hapticEngine.playFeedback(type: .light)
            }

            Text(stake.caption)
                .font(.system(.caption, design: .rounded))
                .foregroundStyle(theme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(theme.surfaceRaised.opacity(0.92))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(theme.border, lineWidth: 1)
        )
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
                        isHighlighted: slot == spotlightSlot || slot == pickedSlot,
                        isDimmed: pickedSlot != nil && slot != pickedSlot,
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
                        .foregroundStyle(row.slot == pickedSlot ? outcomeTint : theme.textSecondary)
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

    /// One row per player, longest odds first. `share` is the real probability
    /// of being drawn, taken straight from the weights the draw itself uses.
    struct OddsRow: Identifiable {
        let slot: Int
        let taps: Int
        let share: Double

        var id: Int { slot }
    }

    private var oddsRows: [OddsRow] {
        let weights = drawWeights
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

    /// Weight in the draw, per player, in `roundSlots` order.
    ///
    /// Everyone keeps `baseWeight` whatever they do; the taps only decide how
    /// much of `swingWeight` sits on top. Avoiding, that swing goes to whoever
    /// tapped *least*; winning, it goes to whoever tapped most.
    private var drawWeights: [Int] {
        let counts = roundSlots.map { taps[$0] ?? 0 }
        let best = counts.max() ?? 0
        guard best > 0 else { return counts.map { _ in baseWeight } }

        return counts.map { count in
            let fraction = stake == .avoid
                ? Double(best - count) / Double(best)
                : Double(count) / Double(best)
            return baseWeight + Int((Double(swingWeight) * fraction).rounded())
        }
    }

    private var outcomeTint: Color {
        stake == .avoid ? theme.danger : theme.success
    }

    private func ringColor(for slot: Int) -> Color {
        if let pickedSlot { return slot == pickedSlot ? outcomeTint : theme.playerColor(for: slot) }
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
            guard let pickedSlot else { return "Round complete." }
            return "Player \(pickedSlot + 1) \(stake.verb)."
        }
    }

    private var statusTint: Color? {
        switch phase {
        case .tapping: return theme.accent
        case .finished: return outcomeTint
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
        pickedSlot = nil
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

    /// A short spotlight sweep before the answer, slowing as it goes. The draw
    /// happens first; the sweep is theatre laid over a decision already made.
    private func runReveal() async {
        phase = .revealing
        environment.cue(.medium, .reveal)

        guard !roundSlots.isEmpty else { return }
        let weights = drawWeights
        guard let index = drawEngine.drawIndexWithWeights(weights: weights, limit: roundSlots.count) else {
            return
        }
        let drawn = roundSlots[index]

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
            pickedSlot = drawn
            phase = .finished
        }
        environment.cue(.heavy, stake == .avoid ? .miss : .safe)
    }

    private func resetRound() {
        teardown()
        phase = .claiming
        roundSlots = []
        taps = [:]
        pickedSlot = nil
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
