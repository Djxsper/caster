import SwiftUI

/// Name entry for the modes that address people by name. The list itself is
/// `RosterEditor`, which reads and writes `RosterStore` — so leaving this
/// screen, by the button or by a back-swipe, keeps every name that was typed.
struct PlayerSetupView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(GameState.self) private var gameState
    @Environment(RosterStore.self) private var rosterStore
    @Environment(\.theme) private var theme
    @Binding var path: [Route]

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            VStack(spacing: 12) {
                RosterEditor()

                PrimaryButton(
                    title: "Start Game",
                    isEnabled: rosterStore.canPlay,
                    action: startGame
                )
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .padding(.top, 12)
        }
        .navigationTitle("Players")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func startGame() {
        gameState.adoptRoster(rosterStore.names)
        environment.hapticEngine.playFeedback(type: .heavy)
        path.append(.game(gameState.currentMode))
    }
}

#Preview {
    NavigationStack {
        PlayerSetupView(path: .constant([]))
            .environment(AppEnvironment())
            .environment(GameState())
            .environment(RosterStore())
            .environment(WheelStore())
            .environment(EntitlementStore())
            .environment(StoreService(entitlements: EntitlementStore()))
    }
}