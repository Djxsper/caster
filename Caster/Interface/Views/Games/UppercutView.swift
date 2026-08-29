import SwiftUI

/// Everyone holds a finger down. The light in the middle flips and a tone
/// fires; lift as fast as you can. Fastest wins, slowest loses, and lifting
/// before the cue ends the round on the spot.
///
/// Reaction times come from `UITouch.timestamp`, not from a `Date()` read
/// inside a callback — otherwise the game is partly timing the main thread.
struct UppercutView: View {
    private enum Phase {
        case gathering
        case armed
        case cued
        case finished
    }

    @Environment(AppEnvironment.self) private var environment
    @Environment(\.theme) private var theme

    @State private var arena = TouchArena()
    @State private var phase: Phase = .gathering
    @State private var roundSlots: [Int] = []
    @State private var reactions: [Int: TimeInterval] = [:]
    @State private var cueTimestamp: TimeInterval?
    @State private var falseStartSlot: Int?
    @State private var armTask: Task<Void, Never>?
    @State private var cueTask: Task<Void, Never>?
    @State private var timeoutTask: Task<Void, Never>?

    /// How long everybody has to be settled before the round arms itself.
    private let settleDuration: Double = 1.2
    /// Nobody gets to wait forever after the cue.
    private let reactionWindow: Double = 3.0

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            TouchSurface(arena: arena) { size in
                ZStack {
                    centreLight
                        .position(x: size.width / 2, y: size.height / 2)
                    ringLayer
                }
            }

            VStack(spacing: 14) {
                Spacer(minLength: 0)

                if phase == .finished {
                    scoreboard
                        .padding(.horizontal, 16)
                }

                StatusLine(text: statusText, emphasis: statusTint)

                footer
                    .padding(.horizontal, 16)
                    .padding(.bottom, 20)
            }
            .padding(.top, 12)
        }
        .navigationTitle(GameMode.uppercut.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: configure)
        .onDisappear(perform: teardown)
    }

    // MARK: - Play area

    private var centreLight: some View {
        ZStack {
            Circle()
                .fill(lightColor.opacity(phase == .cued ? 0.85 : 0.22))
                .frame(width: 190, height: 190)
                .blur(radius: phase == .cued ? 18 : 0)

            Circle()
                .fill(lightColor)
                .frame(width: 150, height: 150)

            Circle()
                .stroke(.white.opacity(0.35), lineWidth: 3)
                .frame(width: 150, height: 150)

            Image(systemName: lightSymbol)
                .font(.system(size: 46, weight: .heavy))
                .foregroundStyle(.white)
        }
        .animation(.easeOut(duration: 0.08), value: phase)
    }

    @ViewBuilder
    private var ringLayer: some View {
        if phase == .gathering {
            ForEach(arena.activeFingers) { finger in
                FingerRing(color: theme.playerColor(for: finger.slot), diameter: 74)
                    .position(finger.location)
            }
        } else {
            ForEach(roundSlots, id: \.self) { slot in
                if let anchor = arena.anchor(for: slot) {
                    FingerRing(
                        color: ringColor(for: slot),
                        diameter: 74,
                        isHighlighted: slot == fastestSlot,
                        badge: badge(for: slot)
                    )
                    .position(anchor)
                }
            }
        }
    }

    private var scoreboard: some View {
        VStack(spacing: 6) {
            ForEach(ranking) { entry in
                HStack(spacing: 10) {
                    Text("\(entry.rank)")
                        .font(.system(.footnote, design: .rounded).weight(.bold))
                        .foregroundStyle(theme.textSecondary)
                        .frame(width: 18)

                    Circle()
                        .fill(theme.playerColor(for: entry.slot))
                        .frame(width: 14, height: 14)

                    Text("Player \(entry.slot + 1)")
                        .font(.system(.subheadline, design: .rounded))
                        .foregroundStyle(theme.textPrimary)

                    Spacer(minLength: 8)

                    Text(entry.time.map { "\(Int(($0 * 1000).rounded())) ms" } ?? "no lift")
                        .font(.system(.subheadline, design: .rounded).weight(.semibold))
                        .foregroundStyle(resultTint(for: entry.slot))
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
        case .gathering:
            EmptyView()
        case .armed, .cued:
            SecondaryButton(title: "Reset", action: resetRound)
        case .finished:
            PrimaryButton(title: "Play Again", action: resetRound)
        }
    }

    // MARK: - Appearance

    private var lightColor: Color {
        switch phase {
        case .gathering: return theme.surfaceRaised
        case .armed: return theme.danger
        case .cued: return theme.success
        case .finished: return falseStartSlot == nil ? theme.accent : theme.danger
        }
    }

    private var lightSymbol: String {
        switch phase {
        case .gathering: return "hand.point.up.left.fill"
        case .armed: return "hourglass"
        case .cued: return "bolt.fill"
        case .finished: return falseStartSlot == nil ? "flag.checkered" : "xmark"
        }
    }

    private func ringColor(for slot: Int) -> Color {
        if slot == falseStartSlot { return theme.danger }
        guard phase == .finished else {
            return reactions[slot] == nil ? theme.playerColor(for: slot) : theme.success
        }
        return resultTint(for: slot)
    }

    private func resultTint(for slot: Int) -> Color {
        if slot == falseStartSlot { return theme.danger }
        if slot == fastestSlot { return theme.success }
        if slot == slowestSlot { return theme.danger }
        return theme.textSecondary
    }

    private func badge(for slot: Int) -> String? {
        if slot == falseStartSlot { return "!" }
        guard let time = reactions[slot] else { return nil }
        return "\(Int((time * 1000).rounded()))"
    }

    // MARK: - Results

    /// Everyone who took part, fastest first. A player who never lifted sorts
    /// to the back — they are slower than any real reaction.
    ///
    /// A named type rather than a labelled tuple, because `ForEach` needs a key
    /// path for identity and Swift has no key paths into tuple elements.
    struct RankEntry: Identifiable {
        let slot: Int
        let time: TimeInterval?
        var rank = 0

        var id: Int { slot }
    }

    private var ranking: [RankEntry] {
        let entries = roundSlots.map { RankEntry(slot: $0, time: reactions[$0]) }
        let sorted = entries.sorted(by: Self.isFaster)
        return sorted.enumerated().map { position, entry in
            RankEntry(slot: entry.slot, time: entry.time, rank: position + 1)
        }
    }

    private static func isFaster(_ lhs: RankEntry, _ rhs: RankEntry) -> Bool {
        switch (lhs.time, rhs.time) {
        case let (left?, right?): return left < right
        case (nil, _?): return false
        case (_?, nil): return true
        case (nil, nil): return lhs.slot < rhs.slot
        }
    }

    private var fastestSlot: Int? {
        guard phase == .finished, falseStartSlot == nil else { return nil }
        return ranking.first?.slot
    }

    private var slowestSlot: Int? {
        guard phase == .finished else { return falseStartSlot }
        if let falseStartSlot { return falseStartSlot }
        return ranking.count > 1 ? ranking.last?.slot : nil
    }

    private var statusText: String {
        switch phase {
        case .gathering:
            let count = arena.activeCount
            if count == 0 { return "Everyone hold a finger down" }
            if count == 1 { return "One finger down — needs at least two" }
            return "Hold still…"
        case .armed:
            return "Wait for it. Lift early and you lose."
        case .cued:
            return "LIFT!"
        case .finished:
            if let falseStartSlot {
                return "Player \(falseStartSlot + 1) went early and loses."
            }
            guard let winner = fastestSlot else { return "Nobody reacted." }
            if let loser = slowestSlot, loser != winner {
                return "Player \(winner + 1) wins. Player \(loser + 1) loses."
            }
            return "Player \(winner + 1) wins."
        }
    }

    private var statusTint: Color? {
        switch phase {
        case .cued: return theme.success
        case .finished: return falseStartSlot == nil ? theme.textPrimary : theme.danger
        default: return nil
        }
    }

    // MARK: - Round flow

    private func configure() {
        environment.hapticEngine.startEngine()
        environment.soundEngine.start()
        arena.slotPolicy = .sticky(radius: 90)
        arena.reset()

        arena.onBegan = { _ in
            guard phase == .gathering else { return }
            environment.cue(.light, .place)
            scheduleArm()
        }

        arena.onEnded = { finger in
            switch phase {
            case .gathering:
                scheduleArm()
            case .armed:
                registerFalseStart(slot: finger.slot)
            case .cued:
                registerLift(finger)
            case .finished:
                break
            }
        }
    }

    private func teardown() {
        armTask?.cancel()
        cueTask?.cancel()
        timeoutTask?.cancel()
        armTask = nil
        cueTask = nil
        timeoutTask = nil
        arena.reset()
    }

    /// Any change to the table restarts the settle timer, so the round only
    /// arms once the hands have stopped moving.
    private func scheduleArm() {
        armTask?.cancel()
        armTask = nil
        guard phase == .gathering, arena.activeCount >= 2 else { return }

        armTask = Task {
            guard (try? await Task.sleep(for: .seconds(settleDuration))) != nil else { return }
            arm()
        }
    }

    private func arm() {
        guard phase == .gathering, arena.activeCount >= 2 else { return }

        roundSlots = arena.occupiedSlots.sorted()
        reactions = [:]
        falseStartSlot = nil
        cueTimestamp = nil
        // No latecomers once the fuse is lit.
        arena.acceptsNewSlots = false
        phase = .armed

        let delay = Double.random(in: 2.0...6.5)
        cueTask = Task {
            guard (try? await Task.sleep(for: .seconds(delay))) != nil else { return }
            fireCue()
        }
    }

    private func fireCue() {
        guard phase == .armed else { return }
        // Stamped from the same clock as `UITouch.timestamp`, so the subtraction
        // in `registerLift` is apples to apples.
        cueTimestamp = ProcessInfo.processInfo.systemUptime
        phase = .cued
        environment.cue(.heavy, .cue)

        timeoutTask = Task {
            guard (try? await Task.sleep(for: .seconds(reactionWindow))) != nil else { return }
            finish()
        }
    }

    private func registerLift(_ finger: ArenaFinger) {
        guard let cueTimestamp, roundSlots.contains(finger.slot) else { return }
        guard reactions[finger.slot] == nil else { return }

        reactions[finger.slot] = max(0, finger.endTime - cueTimestamp)
        environment.cue(.light, .pip)

        if reactions.count >= roundSlots.count { finish() }
    }

    private func registerFalseStart(slot: Int) {
        guard phase == .armed else { return }
        cueTask?.cancel()
        cueTask = nil
        falseStartSlot = slot
        phase = .finished
        environment.cue(.heavy, .miss)
    }

    private func finish() {
        guard phase == .cued else { return }
        timeoutTask?.cancel()
        timeoutTask = nil
        withAnimation(.easeOut(duration: 0.2)) {
            phase = .finished
        }
        environment.cue(.heavy, .reveal)
    }

    private func resetRound() {
        teardown()
        roundSlots = []
        reactions = [:]
        falseStartSlot = nil
        cueTimestamp = nil
        phase = .gathering
    }
}

#Preview {
    NavigationStack {
        UppercutView()
            .environment(AppEnvironment())
    }
}
