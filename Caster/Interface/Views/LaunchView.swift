import SwiftUI

/// Screens reachable from the launch screen. A single `NavigationStack` path
/// replaces a chain of sheets-presenting-sheets, which stacked modals on top of
/// each other and gave each screen its own dead-end state.
enum Route: Hashable {
    case modeSelect
    /// Name entry, for the modes that address people by name.
    case playerSetup
    /// The pinwheel's entry list.
    case wheelSetup
    case game(GameMode)
}

struct LaunchView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(GameState.self) private var gameState
    @Environment(\.theme) private var theme
    @State private var path: [Route] = []
    @State private var isPulsing = false

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                theme.background
                    .ignoresSafeArea()

                VStack(spacing: 24) {
                    imageIcon
                        .scaleEffect(isPulsing ? 1.0 : 0.85)
                        .animation(
                            .easeInOut(duration: 1.2).repeatForever(autoreverses: true),
                            value: isPulsing
                        )

                    VStack(spacing: 16) {
                        Text("Caster")
                            .font(.system(.largeTitle, design: .rounded))
                            .fontWeight(.bold)
                            .foregroundStyle(theme.textPrimary)

                        Text("Ready to play")
                            .font(.system(.title3, design: .rounded))
                            .foregroundStyle(theme.textSecondary)
                    }

                    PrimaryButton(title: "Begin") {
                        environment.hapticEngine.playFeedback(type: .medium)
                        path.append(.modeSelect)
                    }
                    .frame(maxWidth: 250)
                    .padding(.top, 32)
                }
                .padding()
            }
            .navigationDestination(for: Route.self) { route in
                destination(for: route)
            }
        }
        .onAppear(perform: handleAppear)
    }

    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case .modeSelect:
            ModeSelectView(path: $path)
        case .playerSetup:
            PlayerSetupView(path: $path)
        case .wheelSetup:
            WheelSetupView(path: $path)
        case .game(let mode):
            GameHostView(mode: mode)
        }
    }

    private func handleAppear() {
        // Kick the repeating animation once the view is on screen.
        isPulsing = true
        environment.hapticEngine.startEngine()
        environment.soundEngine.start()

        #if DEBUG
        // CI screenshot deep-link; a no-op during normal use.
        let route = ScreenshotSupport.requestedRoute
        if !route.isEmpty {
            if ScreenshotSupport.needsSeededPlayers {
                gameState.configurePlayers(
                    count: ScreenshotSupport.sampleNames.count,
                    names: ScreenshotSupport.sampleNames
                )
            }
            if let mode = ScreenshotSupport.requestedMode {
                gameState.currentMode = mode
            }
            path = route
        }
        #endif
    }

    private var imageIcon: some View {
        Image(systemName: "circle.fill")
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: 80, height: 80)
            .foregroundStyle(theme.accent)
            .overlay(
                Image(systemName: "circle.dashed")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 110, height: 110)
                    .foregroundStyle(theme.accent.opacity(0.3))
            )
            .accessibilityHidden(true)
    }
}

/// Routes a mode to its screen. One place to add a game, rather than a switch
/// buried in `navigationDestination`.
struct GameHostView: View {
    let mode: GameMode

    var body: some View {
        switch mode {
        case .fingerPicker: FingerPickerView()
        case .pinwheel: PinwheelView()
        case .hotPotato: HotPotatoView()
        case .uppercut: UppercutView()
        case .tapFrenzy: TapFrenzyView()
        case .chicken: ChickenView()
        }
    }
}

#Preview {
    LaunchView()
        .environment(AppEnvironment())
        .environment(GameState())
        .environment(WheelStore())
}
