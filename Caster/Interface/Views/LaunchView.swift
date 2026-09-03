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
    case settings
    case game(GameMode)
}

struct LaunchView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(GameState.self) private var gameState
    @Environment(RosterStore.self) private var rosterStore
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

                        testBuildBadge
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
        case .settings:
            SettingsView()
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
                // Seeded into the store, not just `GameState`: the games adopt
                // the saved roster when they appear, which would otherwise
                // overwrite these with whatever the simulator had on disk.
                rosterStore.replaceAll(with: ScreenshotSupport.sampleNames)
                gameState.adoptRoster(ScreenshotSupport.sampleNames)
            }
            if let mode = ScreenshotSupport.requestedMode {
                gameState.currentMode = mode
            }
            path = route
        }
        #endif
    }

    /// So a test build is never mistaken for the real one — they share a bundle
    /// id, which means installing one replaces the other.
    ///
    /// Hidden while CI is driving the app to a screen, or it would end up in the
    /// screenshots on the README and the App Store listing.
    @ViewBuilder
    private var testBuildBadge: some View {
        #if DEBUG
        if ScreenshotSupport.requestedRoute.isEmpty {
            Text("TEST BUILD")
                .font(.system(.caption2, design: .monospaced))
                .fontWeight(.bold)
                .foregroundStyle(theme.background)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(theme.warning, in: Capsule())
                .accessibilityLabel("Test build")
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
///
/// Also the only place that arms the interstitial. Arming on a game appearing —
/// rather than letting the mode list guess from its own `onAppear` — is what
/// keeps the single placement single: the mode list is reached on the way in,
/// on the way back from the wheel editor and on the way back from a game, and
/// only the last of those is allowed to show anything.
struct GameHostView: View {
    @Environment(AppEnvironment.self) private var environment

    let mode: GameMode

    var body: some View {
        gameView
            .onAppear {
                environment.pacing.armForInterstitial()
                // Fetched now so that if one is shown on the way out, it is
                // already in memory and the transition does not stutter.
                environment.ads.preload()
            }
    }

    @ViewBuilder
    private var gameView: some View {
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
        .environment(RosterStore())
        .environment(ThemeStore())
        .environment(EntitlementStore())
        .environment(StoreService(entitlements: EntitlementStore()))
}
