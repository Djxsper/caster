import SwiftUI

/// Everyone puts a finger on the glass, the rings count down, and the app
/// answers the question. Three answers, the same three the genre settled on:
/// pick somebody, split the room into teams, or put everybody in an order.
struct FingerPickerView: View {
    enum PickStyle: String, CaseIterable, Identifiable {
        case pick
        case teams
        case order

        var id: String { rawValue }

        var label: String {
            switch self {
            case .pick: return "Pick"
            case .teams: return "Teams"
            case .order: return "Order"
            }
        }
    }

    /// A frozen copy of the table at the moment the draw resolved. Frozen on
    /// purpose: the result has to stay on screen while people lift off, and
    /// reading live touches would erase it finger by finger.
    struct Resolution {
        let style: PickStyle
        let fingers: [ArenaFinger]
        var chosen: Set<Int> = []
        var teams: [Int: Int] = [:]
        var order: [Int: Int] = [:]
        var headline: String
    }

    @Environment(AppEnvironment.self) private var environment
    @Environment(\.theme) private var theme

    @State private var arena = TouchArena()
    @State private var style: PickStyle = .pick
    @State private var pickCount = 1
    @State private var teamCount = 2
    @State private var waitProgress: Double = 0
    @State private var spin: Double = 0
    @State private var countdownTask: Task<Void, Never>?
    @State private var resolution: Resolution?

    private let drawEngine = DrawEngine()
    private let holdDuration: Double = 3.0

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            TouchSurface(arena: arena) { _ in
                ringLayer
            }

            VStack(spacing: 0) {
                controlPanel
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .opacity(isControlPanelVisible ? 1 : 0)
                    .allowsHitTesting(isControlPanelVisible)
                    .animation(.easeInOut(duration: 0.25), value: isControlPanelVisible)

                Spacer(minLength: 0)

                StatusLine(text: statusText, emphasis: resolution == nil ? nil : theme.textPrimary)
                    .padding(.bottom, 28)
            }
        }
        .navigationTitle(GameMode.fingerPicker.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: configure)
        .onDisappear(perform: teardown)
    }

    // MARK: - Layers

    @ViewBuilder
    private var ringLayer: some View {
        if let resolution {
            ForEach(resolution.fingers) { finger in
                resolvedRing(for: finger, in: resolution)
                    .position(finger.location)
            }
        } else {
            ForEach(arena.activeFingers) { finger in
                FingerRing(
                    color: color(for: finger.slot, total: arena.activeCount),
                    progress: waitProgress,
                    spin: spin
                )
                .position(finger.location)
                .transition(.scale.combined(with: .opacity))
            }
        }
    }

    private func resolvedRing(for finger: ArenaFinger, in resolution: Resolution) -> some View {
        let slot = finger.slot
        let total = resolution.fingers.count

        switch resolution.style {
        case .pick:
            let isChosen = resolution.chosen.contains(slot)
            return AnyView(
                FingerRing(
                    color: color(for: slot, total: total),
                    progress: isChosen ? 1 : 0,
                    spin: spin,
                    isHighlighted: isChosen,
                    isDimmed: !isChosen
                )
            )
        case .teams:
            let team = resolution.teams[slot] ?? 0
            return AnyView(
                FingerRing(
                    color: theme.playerColor(for: team),
                    progress: 1,
                    spin: spin,
                    isHighlighted: true,
                    badge: "\(team + 1)"
                )
            )
        case .order:
            let rank = resolution.order[slot] ?? 0
            return AnyView(
                FingerRing(
                    color: color(for: slot, total: total),
                    progress: 1,
                    spin: spin,
                    isHighlighted: rank == 1,
                    badge: "\(rank)"
                )
            )
        }
    }

    private var controlPanel: some View {
        VStack(spacing: 10) {
            Picker("Result", selection: $style) {
                ForEach(PickStyle.allCases) { option in
                    Text(option.label).tag(option)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: style) { _, _ in
                environment.hapticEngine.playFeedback(type: .light)
                restartCountdown()
            }

            if style != .order {
                Stepper(value: stepperBinding, in: stepperRange) {
                    Text(stepperTitle)
                        .font(.system(.subheadline, design: .rounded))
                        .foregroundStyle(theme.textSecondary)
                }
                .tint(theme.accent)
            }
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

    // MARK: - Control panel plumbing

    private var isControlPanelVisible: Bool {
        arena.activeCount == 0 && resolution == nil
    }

    private var stepperBinding: Binding<Int> {
        style == .pick ? $pickCount : $teamCount
    }

    private var stepperRange: ClosedRange<Int> {
        style == .pick ? 1...5 : 2...6
    }

    private var stepperTitle: String {
        if style == .pick {
            return pickCount == 1 ? "Pick 1 finger" : "Pick \(pickCount) fingers"
        }
        return "\(teamCount) teams"
    }

    private var statusText: String {
        if let resolution { return resolution.headline }

        let count = arena.activeCount
        switch count {
        case 0:
            return "Everyone put a finger on the screen"
        case 1:
            return "One finger down — needs at least two"
        default:
            if isReadyToResolve { return "Hold still…" }
            return shortfallText(for: count)
        }
    }

    private func shortfallText(for count: Int) -> String {
        switch style {
        case .pick:
            return "Picking \(pickCount) needs more than \(pickCount) fingers"
        case .teams:
            return "\(teamCount) teams needs at least \(teamCount) fingers"
        case .order:
            return "\(count) fingers down"
        }
    }

    // MARK: - Round flow

    private func configure() {
        environment.hapticEngine.startEngine()
        environment.soundEngine.start()
        arena.slotPolicy = .sequential
        arena.reset()

        arena.onBegan = { _ in
            environment.cue(.light, .place)
            restartCountdown()
        }
        arena.onEnded = { _ in
            if resolution != nil {
                // The answer stays up until the last hand is off the glass.
                if arena.activeCount == 0 { clearResolution() }
            } else {
                restartCountdown()
            }
        }

        withAnimation(.linear(duration: 6).repeatForever(autoreverses: false)) {
            spin = 360
        }
    }

    private func teardown() {
        countdownTask?.cancel()
        countdownTask = nil
        arena.reset()
    }

    private var isReadyToResolve: Bool {
        let count = arena.activeCount
        guard count >= 2 else { return false }
        switch style {
        case .pick: return count > pickCount
        case .teams: return count >= teamCount
        case .order: return true
        }
    }

    /// Any change in the number of fingers restarts the wait, so latecomers are
    /// never shut out of a round that is already counting down.
    private func restartCountdown() {
        countdownTask?.cancel()
        countdownTask = nil
        setProgressWithoutAnimation(0)

        guard resolution == nil, isReadyToResolve else { return }

        withAnimation(.linear(duration: holdDuration)) {
            waitProgress = 1
        }

        countdownTask = Task {
            // Task.sleep throws on cancellation, so a changed table stops here.
            guard (try? await Task.sleep(for: .seconds(holdDuration))) != nil else { return }
            resolve()
        }
    }

    private func setProgressWithoutAnimation(_ value: Double) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) { waitProgress = value }
    }

    private func resolve() {
        let fingers = arena.activeFingers
        guard fingers.count >= 2 else { return }

        let slots = fingers.map(\.slot)
        let outcome: Resolution

        switch style {
        case .pick:
            let winners = drawEngine.pick(min(pickCount, slots.count - 1), from: slots)
            outcome = Resolution(
                style: .pick,
                fingers: fingers,
                chosen: Set(winners),
                headline: winners.count == 1 ? "Picked" : "Picked \(winners.count)"
            )
        case .teams:
            let assignments = drawEngine.splitIntoTeams(slots, teams: teamCount)
            var teams: [Int: Int] = [:]
            for (index, slot) in slots.enumerated() where index < assignments.count {
                teams[slot] = assignments[index]
            }
            outcome = Resolution(
                style: .teams,
                fingers: fingers,
                teams: teams,
                headline: "\(teamCount) teams"
            )
        case .order:
            let ranks = drawEngine.randomOrder(count: slots.count)
            var order: [Int: Int] = [:]
            for (index, slot) in slots.enumerated() where index < ranks.count {
                order[slot] = ranks[index]
            }
            outcome = Resolution(
                style: .order,
                fingers: fingers,
                order: order,
                headline: "Order set — lift to play again"
            )
        }

        // Stop taking new fingers so a stray touch cannot join a finished draw.
        arena.acceptsNewSlots = false
        countdownTask = nil
        setProgressWithoutAnimation(0)
        withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
            resolution = outcome
        }
        environment.cue(.heavy, .reveal)
    }

    private func clearResolution() {
        withAnimation(.easeOut(duration: 0.2)) {
            resolution = nil
        }
        arena.reset()
    }

    /// Past the eight fixed seat colours the hues spread out instead, so ten
    /// fingers still read as ten different people.
    ///
    /// `total` is passed in rather than read from the arena: a resolved draw
    /// keeps its own count, or the colours would shift under the answer as
    /// people lift their fingers off.
    private func color(for slot: Int, total: Int) -> Color {
        PlayerPalette.spread(index: slot, total: max(8, total))
    }
}

#Preview {
    NavigationStack {
        FingerPickerView()
            .environment(AppEnvironment())
            .environment(GameState())
    }
}
