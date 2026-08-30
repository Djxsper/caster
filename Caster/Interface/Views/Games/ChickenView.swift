import SwiftUI

/// Everyone holds a finger down. One circle lights up at a time; let go before
/// it goes out and you are safe and out of the game. Too slow and you stay in.
/// The last one still holding loses.
///
/// The window opens at 100 ms — quicker than anyone can actually move — and
/// widens by 50 ms every time a flash is missed. The round therefore calibrates
/// itself to whoever is playing: it keeps easing until it crosses the fastest
/// pair of reflexes in the room, that player escapes, and it goes on easing
/// past everyone else in turn. Whoever it reaches last is the slowest, and they
/// are the one left holding.
struct ChickenView: View {
    private enum Phase {
        case gathering
        case playing
        case finished
    }

    @Environment(AppEnvironment.self) private var environment
    @Environment(\.theme) private var theme

    @State private var arena = TouchArena()
    @State private var phase: Phase = .gathering
    @State private var activeSlots: [Int] = []
    @State private var safeSlots: [Int] = []
    @State private var flashSlot: Int?
    /// Kept past the end of a flash so the next pick can avoid an immediate repeat.
    @State private var lastFlashSlot: Int?
    @State private var flashProgress: Double = 0
    @State private var liftedSlot: Int?
    /// When that lift happened, from `UITouch.timestamp`. At a 100 ms window,
    /// noticing a lift in a callback is far too coarse to judge it by.
    @State private var liftedAt: TimeInterval?
    @State private var loserSlot: Int?
    @State private var waitingOnSlot: Int?
    @State private var reactionWindow: Double = 0.10
    @State private var settleTask: Task<Void, Never>?
    @State private var roundTask: Task<Void, Never>?

    private let settleDuration: Double = 1.5
    /// Deliberately below human reaction time. The opening flashes are meant to
    /// be unmissable in the bad sense, and the tension comes from watching the
    /// window creep up towards something anyone can actually hit.
    private let startWindow: Double = 0.10
    private let windowStep: Double = 0.05
    /// Nothing needs a window this wide; it only stops a stuck round crawling.
    private let maxWindow: Double = 2.5
    /// Slack for the trip from glass to callback, so a lift that really did
    /// land inside the window is not thrown out by delivery lag.
    private let latencyGrace: Double = 0.09

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            TouchSurface(arena: arena) { size in
                ZStack {
                    if phase == .gathering, arena.activeCount == 0 {
                        EmptyPlayHint(
                            systemImage: "flame",
                            title: "Hold on",
                            detail: "Everyone puts a finger anywhere on the screen and keeps it there."
                        )
                        .position(x: size.width / 2, y: size.height / 2)
                        .transition(.opacity)
                    }
                    ringLayer
                }
            }

            VStack(spacing: 12) {
                if phase == .playing {
                    windowChip
                        .padding(.top, 4)
                }

                Spacer(minLength: 0)

                if !safeSlots.isEmpty {
                    safeStrip
                        .padding(.horizontal, 16)
                }

                StatusLine(text: statusText, emphasis: statusTint)

                footer
                    .padding(.horizontal, 16)
                    .padding(.bottom, 20)
            }
            .padding(.top, 12)
        }
        .navigationTitle(GameMode.chicken.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: configure)
        .onDisappear(perform: teardown)
    }

    // MARK: - Play area

    @ViewBuilder
    private var ringLayer: some View {
        if phase == .gathering {
            ForEach(arena.activeFingers) { finger in
                FingerRing(color: theme.playerColor(for: finger.slot), diameter: 78)
                    .position(finger.location)
            }
        } else {
            ForEach(activeSlots, id: \.self) { slot in
                if let anchor = arena.anchor(for: slot) {
                    FingerRing(
                        color: ringColor(for: slot),
                        diameter: 78,
                        progress: slot == flashSlot ? flashProgress : 0,
                        isHighlighted: slot == flashSlot,
                        isDimmed: waitingOnSlot == slot,
                        badge: slot == loserSlot ? "✕" : nil
                    )
                    .position(anchor)
                }
            }
        }
    }

    /// Shows the window widening, so the ramp is legible rather than a mystery.
    private var windowChip: some View {
        HStack(spacing: 6) {
            Image(systemName: "stopwatch")
                .font(.caption)

            Text("\(Int((reactionWindow * 1000).rounded())) ms to let go")
                .font(.system(.caption, design: .rounded).weight(.semibold))
                .monospacedDigit()
        }
        .foregroundStyle(theme.textSecondary)
        .padding(.vertical, 7)
        .padding(.horizontal, 12)
        .background(Capsule().fill(theme.surfaceRaised))
        .animation(.easeInOut(duration: 0.2), value: reactionWindow)
    }

    private var safeStrip: some View {
        HStack(spacing: 8) {
            Text("Out safe")
                .font(.system(.caption, design: .rounded).weight(.semibold))
                .foregroundStyle(theme.textSecondary)

            ForEach(safeSlots, id: \.self) { slot in
                Text("\(slot + 1)")
                    .font(.system(.caption, design: .rounded).weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 24, height: 24)
                    .background(theme.playerColor(for: slot), in: Circle())
            }

            Spacer(minLength: 0)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 14)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(theme.surfaceRaised)
        )
    }

    @ViewBuilder
    private var footer: some View {
        switch phase {
        case .gathering:
            EmptyView()
        case .playing:
            SecondaryButton(title: "Reset", action: resetRound)
        case .finished:
            PrimaryButton(title: "Play Again", action: resetRound)
        }
    }

    private func ringColor(for slot: Int) -> Color {
        if slot == loserSlot { return theme.danger }
        if slot == flashSlot { return theme.warning }
        return theme.playerColor(for: slot)
    }

    private var statusText: String {
        switch phase {
        case .gathering:
            let count = arena.activeCount
            if count == 0 { return "Everyone hold a finger down" }
            if count == 1 { return "One finger down — needs at least two" }
            return "Hold still…"
        case .playing:
            if let waitingOnSlot {
                return "Player \(waitingOnSlot + 1), finger back on the glass"
            }
            if flashSlot != nil { return "Let go!" }
            return "\(activeSlots.count) still in. Hold."
        case .finished:
            guard let loserSlot else { return "Round complete." }
            return "Player \(loserSlot + 1) is last in and loses."
        }
    }

    private var statusTint: Color? {
        switch phase {
        case .finished: return theme.danger
        case .playing: return flashSlot == nil ? nil : theme.warning
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
            if phase == .gathering {
                environment.cue(.light, .place)
                scheduleStart()
            }
        }

        arena.onEnded = { finger in
            switch phase {
            case .gathering:
                scheduleStart()
            case .playing:
                // Recorded rather than acted on: the flash loop is what decides
                // whether this lift landed inside the window.
                liftedSlot = finger.slot
                liftedAt = finger.endTime
            case .finished:
                break
            }
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
        guard phase == .gathering, arena.activeCount >= 2 else { return }

        settleTask = Task {
            guard (try? await Task.sleep(for: .seconds(settleDuration))) != nil else { return }
            beginRound()
        }
    }

    private func beginRound() {
        guard phase == .gathering, arena.activeCount >= 2 else { return }

        activeSlots = arena.occupiedSlots.sorted()
        safeSlots = []
        loserSlot = nil
        flashSlot = nil
        lastFlashSlot = nil
        liftedSlot = nil
        liftedAt = nil
        waitingOnSlot = nil
        reactionWindow = startWindow
        arena.acceptsNewSlots = false
        phase = .playing
        environment.cue(.heavy, .pip)

        roundTask = Task { await runFlashes() }
    }

    private func runFlashes() async {
        while phase == .playing, activeSlots.count > 1, !Task.isCancelled {
            guard (try? await Task.sleep(for: .seconds(Double.random(in: 0.55...1.5)))) != nil else { return }
            guard !Task.isCancelled else { return }

            guard let target = await nextTarget() else { return }
            waitingOnSlot = nil

            let window = reactionWindow
            liftedSlot = nil
            liftedAt = nil
            flashSlot = target
            lastFlashSlot = target
            // Same clock as `UITouch.timestamp`, so the comparison in
            // `qualifies` measures the player and not the main thread.
            let flashedAt = ProcessInfo.processInfo.systemUptime
            environment.cue(.medium, .pip)

            withAnimation(.linear(duration: window)) { flashProgress = 1 }
            let survived = await waitForLift(of: target, within: window, flashedAt: flashedAt)
            setFlashProgressWithoutAnimation(0)
            flashSlot = nil

            if survived {
                markSafe(target)
            } else {
                // Nobody could make that one. Give the next attempt more room.
                reactionWindow = min(maxWindow, reactionWindow + windowStep)
                environment.cue(.heavy, .miss)
            }
        }

        guard !Task.isCancelled, phase == .playing else { return }
        finish()
    }

    /// Only a slot with a finger actually on it can be flashed. Someone who has
    /// drifted off gets called out instead of being handed a free elimination.
    private func nextTarget() async -> Int? {
        while !Task.isCancelled {
            let candidates = activeSlots.filter { arena.isSlotHeld($0) }
            if let last = lastFlashSlot, candidates.count > 1 {
                let others = candidates.filter { $0 != last }
                if let pick = others.randomElement() { return pick }
            }
            if let pick = candidates.randomElement() { return pick }

            // Nobody is holding: name someone and wait for a hand to come back.
            waitingOnSlot = activeSlots.first
            guard (try? await Task.sleep(for: .milliseconds(200))) != nil else { return nil }
        }
        return nil
    }

    /// - Returns: true when the flashed slot lifted before the window closed.
    ///
    /// Waits a little past the window before giving up, because a lift can be
    /// delivered after its own timestamp. What counts is when the touch says it
    /// happened, never when this loop got round to seeing it.
    private func waitForLift(
        of slot: Int,
        within window: Double,
        flashedAt: TimeInterval
    ) async -> Bool {
        let deadline = Date().addingTimeInterval(window + latencyGrace)
        while Date() < deadline {
            if qualifies(slot: slot, window: window, flashedAt: flashedAt) { return true }
            guard (try? await Task.sleep(for: .milliseconds(8))) != nil else { return false }
        }
        return qualifies(slot: slot, window: window, flashedAt: flashedAt)
    }

    private func qualifies(slot: Int, window: Double, flashedAt: TimeInterval) -> Bool {
        guard liftedSlot == slot, let liftedAt else { return false }
        let elapsed = liftedAt - flashedAt
        // A lift stamped before the flash is a hand that was already leaving,
        // not a reaction to anything.
        return elapsed >= 0 && elapsed <= window
    }

    private func markSafe(_ slot: Int) {
        withAnimation(.easeOut(duration: 0.25)) {
            activeSlots.removeAll { $0 == slot }
            safeSlots.append(slot)
        }
        // Retiring drops the anchor, so their hand leaving for good cannot come
        // back and claim someone else's circle.
        arena.retire(slot: slot)
        environment.cue(.medium, .safe)
    }

    private func finish() {
        flashSlot = nil
        waitingOnSlot = nil
        withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
            loserSlot = activeSlots.first
            phase = .finished
        }
        environment.cue(.heavy, .boom)
    }

    private func setFlashProgressWithoutAnimation(_ value: Double) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) { flashProgress = value }
    }

    private func resetRound() {
        teardown()
        phase = .gathering
        activeSlots = []
        safeSlots = []
        flashSlot = nil
        lastFlashSlot = nil
        liftedSlot = nil
        liftedAt = nil
        loserSlot = nil
        waitingOnSlot = nil
        reactionWindow = startWindow
        setFlashProgressWithoutAnimation(0)
    }
}

#Preview {
    NavigationStack {
        ChickenView()
            .environment(AppEnvironment())
    }
}
