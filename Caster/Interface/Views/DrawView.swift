import SwiftUI

struct DrawView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(GameState.self) private var gameState
    @Environment(\.theme) private var theme
    @Binding var path: [Route]

    @State private var tracker = TouchTracker()
    /// Held so the round can be cancelled if the view goes away mid-countdown.
    @State private var roundTask: Task<Void, Never>?
    @State private var cueDate: Date?

    private let circleDiameter: CGFloat = 60

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            VStack(spacing: 20) {
                header

                GeometryReader { geometry in
                    ZStack {
                        seatRing(in: geometry.size)

                        Image(systemName: gameState.currentMode.iconName)
                            .font(.system(size: 48))
                            .foregroundStyle(theme.accent)
                            .animation(.easeInOut(duration: 0.2), value: gameState.currentMode)

                        // Only capture touches while a round is live, so the
                        // overlay cannot eat taps meant for the buttons.
                        if gameState.phase == .drawing {
                            MultiTouchView(tracker: tracker)
                        }
                    }
                    .frame(width: geometry.size.width, height: geometry.size.height)
                }
                .padding()

                Text(statusText)
                    .font(.caption)
                    .foregroundStyle(theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 8)

                primaryButton
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
            }
        }
        .navigationTitle(gameState.currentMode.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: configureTracker)
        .onDisappear {
            // Without this the countdown keeps running after the screen is
            // popped and mutates state for a round nobody is playing.
            roundTask?.cancel()
            roundTask = nil
            tracker.reset()
        }
    }

    // MARK: - Pieces

    private var header: some View {
        let count = gameState.players.count
        return Text("\(count) player\(count == 1 ? "" : "s") ready")
            .font(.system(.title3, design: .rounded))
            .fontWeight(.bold)
            .foregroundStyle(theme.textPrimary)
    }

    private func seatRing(in size: CGSize) -> some View {
        let players = gameState.players
        let radius = max(0, min(size.width, size.height) / 2 - circleDiameter)

        // Iterate the players directly: `\.element.id` over `enumerated()` is a
        // key path into a tuple, which Swift does not allow. `seat` already
        // carries the position, so no index is needed.
        return ForEach(players) { player in
            // Start at -90 degrees so the first seat sits at the top of the ring.
            let angle = (Double(player.seat) / Double(max(1, players.count))) * 2 * .pi - .pi / 2
            let offsetX = radius * cos(angle)
            let offsetY = radius * sin(angle)

            Circle()
                .fill(theme.playerColor(for: player.seat))
                .frame(width: circleDiameter, height: circleDiameter)
                .overlay(
                    Circle()
                        .stroke(.white, lineWidth: player.isHolding ? 3 : 0)
                )
                .overlay(
                    Text(player.name)
                        .font(.caption2)
                        .fontWeight(.semibold)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .padding(2)
                )
                .opacity(player.isLoss ? 0.4 : 1)
                .offset(x: offsetX, y: offsetY)
                .animation(.easeInOut(duration: 0.2), value: player.isHolding)
        }
    }

    private var statusText: String {
        switch gameState.phase {
        case .idle:
            return gameState.currentMode.summary
        case .preCue:
            return "Everyone place a thumb on the screen..."
        case .drawing:
            let count = tracker.activeCount
            return "Hold. \(count) finger\(count == 1 ? "" : "s") down"
        case .result:
            if let loser = gameState.players.first(where: { $0.isLoss }) {
                return "\(loser.name) loses this round."
            }
            return "Round complete."
        }
    }

    @ViewBuilder
    private var primaryButton: some View {
        switch gameState.phase {
        case .idle, .result:
            Button(action: startRound) {
                buttonLabel(gameState.phase == .result ? "Play Again" : "Start Game")
            }
            .buttonStyle(.plain)
            .disabled(gameState.players.isEmpty)
            .opacity(gameState.players.isEmpty ? 0.5 : 1)
        case .preCue, .drawing:
            Button(action: cancelRound) {
                buttonLabel("Cancel")
            }
            .buttonStyle(.plain)
        }
    }

    private func buttonLabel(_ title: String) -> some View {
        Text(title)
            .font(.system(.title3, design: .rounded))
            .fontWeight(.bold)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding()
            .background(theme.accent, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    // MARK: - Round flow

    private func configureTracker() {
        environment.hapticEngine.startEngine()

        tracker.onBegan = { touch in
            environment.hapticEngine.playFeedback(type: .light)
            guard let cue = cueDate else { return }
            // Seats fill in touch-down order; the first finger down is seat 0.
            guard gameState.players.indices.contains(touch.seat) else { return }
            let player = gameState.players[touch.seat]
            player.reactionTime = Date().timeIntervalSince(cue)
            player.initialPosition = touch.startLocation
            player.currentPosition = touch.location
        }

        tracker.onMoved = { touch in
            guard gameState.players.indices.contains(touch.seat) else { return }
            let player = gameState.players[touch.seat]
            player.currentPosition = touch.location
            player.displacement = max(player.displacement, touch.displacement)
        }

        tracker.onEnded = { _ in
            environment.hapticEngine.playFeedback(type: .sharp)
        }
    }

    private func startRound() {
        roundTask?.cancel()
        tracker.reset()
        gameState.beginRound()
        environment.hapticEngine.playFeedback(type: .medium)

        roundTask = Task {
            // Task.sleep throws on cancellation, so a popped view stops here.
            guard (try? await Task.sleep(for: .seconds(1.5))) != nil else { return }
            cueDate = Date()
            gameState.startDrawing()
            environment.hapticEngine.playFeedback(type: .heavy)

            let hold = gameState.currentMode.holdDuration
            guard (try? await Task.sleep(for: .seconds(hold))) != nil else { return }
            gameState.resolveRound()
            environment.hapticEngine.playFeedback(type: .heavy)
            tracker.reset()
            cueDate = nil
        }
    }

    private func cancelRound() {
        roundTask?.cancel()
        roundTask = nil
        cueDate = nil
        tracker.reset()
        gameState.returnToIdle()
    }
}

#Preview {
    NavigationStack {
        DrawView(path: .constant([]))
            .environment(AppEnvironment())
            .environment(GameState())
    }
}
