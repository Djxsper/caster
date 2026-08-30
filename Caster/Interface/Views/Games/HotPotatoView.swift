import SwiftUI

/// A hidden fuse burns while the phone goes round the table. Tap to pass it on.
/// Whoever is holding it when it goes off loses.
///
/// The tick speeds up, because a fuse that ticks flat is not tense — but the
/// acceleration is timed against a *decoy* length drawn separately from the
/// real fuse, so a frantic tick is not a reliable signal that the end is near.
/// Sometimes it blows while the ticking is still lazy.
struct HotPotatoView: View {
    private enum Phase {
        case idle
        case running
        case exploded
    }

    @Environment(AppEnvironment.self) private var environment
    @Environment(GameState.self) private var gameState
    @Environment(\.theme) private var theme

    @State private var phase: Phase = .idle
    @State private var holderIndex = 0
    @State private var loserIndex: Int?
    @State private var fuseTask: Task<Void, Never>?
    @State private var pulse = false
    @State private var passCount = 0
    /// Real hot potato does not go round the circle in turn — you throw it at
    /// whoever is not looking. Off by default so the fair rotation stays there.
    @State private var isRandomOrder = false

    var body: some View {
        ZStack {
            backdrop
                .ignoresSafeArea()

            VStack(spacing: 20) {
                Spacer(minLength: 0)

                potato

                StatusLine(text: statusText, emphasis: statusTint)

                Spacer(minLength: 0)

                if phase != .running {
                    orderToggle
                        .padding(.horizontal, 16)
                }

                footer
                    .padding(.horizontal, 16)
                    .padding(.bottom, 20)
            }
            .padding(.top, 12)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: handleScreenTap)
        .navigationTitle(GameMode.hotPotato.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            environment.hapticEngine.startEngine()
            environment.soundEngine.start()
        }
        .onDisappear {
            fuseTask?.cancel()
            fuseTask = nil
        }
    }

    // MARK: - Pieces

    private var backdrop: some View {
        // The screen itself reddens as the round runs, which is atmosphere
        // only — it tracks the pass count, never the real fuse.
        let heat = min(0.16, Double(passCount) * 0.012)
        return theme.background
            .overlay {
                if phase == .running {
                    theme.danger.opacity(heat)
                }
            }
    }

    private var potato: some View {
        ZStack {
            Circle()
                .fill(holderColor.opacity(0.18))
                .frame(width: 250, height: 250)

            Circle()
                .stroke(holderColor, lineWidth: 6)
                .frame(width: 250, height: 250)
                .scaleEffect(pulse ? 1.045 : 1)

            VStack(spacing: 8) {
                Image(systemName: phase == .exploded ? "exclamationmark.triangle.fill" : "flame.fill")
                    .font(.system(size: 42))
                    .foregroundStyle(holderColor)

                Text(headlineName)
                    .font(.system(.title, design: .rounded))
                    .fontWeight(.heavy)
                    .foregroundStyle(theme.textPrimary)
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.5)
                    .lineLimit(2)
                    .padding(.horizontal, 24)
            }
        }
        .animation(.easeOut(duration: 0.12), value: pulse)
        .animation(.easeInOut(duration: 0.25), value: holderIndex)
    }

    private var orderToggle: some View {
        Toggle(isOn: $isRandomOrder) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Random order")
                    .font(.system(.subheadline, design: .rounded).weight(.semibold))
                    .foregroundStyle(theme.textPrimary)

                Text(isRandomOrder ? "Throw it at anyone" : "Round the circle in turn")
                    .font(.caption)
                    .foregroundStyle(theme.textSecondary)
            }
        }
        .tint(theme.accent)
        .padding(.vertical, 10)
        .padding(.horizontal, 14)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(theme.surfaceRaised)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(theme.border, lineWidth: 1)
        )
    }

    @ViewBuilder
    private var footer: some View {
        switch phase {
        case .idle:
            PrimaryButton(
                title: "Light the Fuse",
                isEnabled: !gameState.players.isEmpty,
                action: startRound
            )
        case .running:
            SecondaryButton(title: "Stop", action: cancelRound)
        case .exploded:
            PrimaryButton(title: "Play Again", action: startRound)
        }
    }

    // MARK: - Copy

    private var headlineName: String {
        guard !gameState.players.isEmpty else { return "No players" }
        switch phase {
        case .idle:
            return "Ready"
        case .running:
            return name(at: holderIndex)
        case .exploded:
            return name(at: loserIndex ?? holderIndex)
        }
    }

    private var statusText: String {
        guard !gameState.players.isEmpty else {
            return "Go back and add some players first."
        }
        switch phase {
        case .idle:
            return "Pass the phone around. Do not be holding it at the end."
        case .running:
            return isRandomOrder
                ? "Tap to throw it at someone"
                : "Tap anywhere to pass it on"
        case .exploded:
            return "Boom. \(name(at: loserIndex ?? holderIndex)) loses."
        }
    }

    private var statusTint: Color? {
        phase == .exploded ? theme.danger : nil
    }

    private var holderColor: Color {
        switch phase {
        case .exploded:
            return theme.danger
        case .idle:
            return theme.accent
        case .running:
            return theme.playerColor(for: holderIndex)
        }
    }

    private func name(at index: Int) -> String {
        guard gameState.players.indices.contains(index) else { return "Someone" }
        return gameState.players[index].name
    }

    // MARK: - Round flow

    private func handleScreenTap() {
        guard phase == .running else { return }
        pass()
    }

    private func pass() {
        let count = gameState.players.count
        guard count > 0 else { return }
        holderIndex = isRandomOrder ? randomHolder(besides: holderIndex, of: count)
                                    : (holderIndex + 1) % count
        passCount += 1
        environment.cue(.medium, .place)
    }

    /// Uniform over everyone *except* whoever is holding it: handing the potato
    /// back to yourself is not a pass.
    private func randomHolder(besides current: Int, of count: Int) -> Int {
        guard count > 1 else { return current }
        var next = Int.random(in: 0..<(count - 1))
        if next >= current { next += 1 }
        return next
    }

    private func startRound() {
        guard !gameState.players.isEmpty else { return }

        fuseTask?.cancel()
        loserIndex = nil
        passCount = 0
        holderIndex = Int.random(in: 0..<gameState.players.count)
        phase = .running
        environment.cue(.heavy, .pip)

        // Two independent draws: what actually ends the round, and what the
        // ticking pretends to be counting down.
        let fuse = Double.random(in: 12...32)
        let decoyLength = fuse * Double.random(in: 0.72...1.55)

        fuseTask = Task {
            let started = Date()
            while !Task.isCancelled {
                let elapsed = Date().timeIntervalSince(started)
                if elapsed >= fuse { break }

                pulse.toggle()
                environment.cue(.light, .tick)

                let decoyProgress = min(1, elapsed / decoyLength)
                let interval = 0.62 - 0.5 * decoyProgress
                // Never sleep past the fuse, or the bang lands late.
                let wait = min(interval, max(0.02, fuse - elapsed))
                guard (try? await Task.sleep(for: .seconds(wait))) != nil else { return }
            }
            guard !Task.isCancelled else { return }
            explode()
        }
    }

    private func explode() {
        fuseTask = nil
        loserIndex = holderIndex
        withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) {
            phase = .exploded
        }
        environment.cue(.heavy, .boom)

        if gameState.players.indices.contains(holderIndex) {
            gameState.recordLoss(for: gameState.players[holderIndex])
        }
    }

    private func cancelRound() {
        fuseTask?.cancel()
        fuseTask = nil
        phase = .idle
        loserIndex = nil
        passCount = 0
    }
}

#Preview {
    NavigationStack {
        HotPotatoView()
            .environment(AppEnvironment())
            .environment(GameState())
    }
}
