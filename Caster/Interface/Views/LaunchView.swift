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

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                theme.background
                    .ignoresSafeArea()

                VStack(spacing: 24) {
                    CasterMark()

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

/// The launch page's mark: a solid core that breathes, inside a dashed ring
/// that turns.
///
/// Driven from a clock rather than from a `@State` flag flipped in `onAppear`.
/// The flag version stalled, which is what made it look like it was coming in
/// and out of the screen: `onAppear` sets it once, and returning to the root of
/// a `NavigationStack` runs `onAppear` again with the value *already* true — so
/// nothing re-triggers, while the `repeatForever` animation that was cancelled
/// on the way out never resumes. The mark was left frozen at whatever scale it
/// had reached, then jumped when something else in the view happened to
/// invalidate it.
///
/// Reading the angle and the scale from `context.date` makes both a pure
/// function of time. There is no state to get stuck in, nothing to restart, and
/// no dependence on how many times this view has been on screen.
///
/// The pulse is a sine rather than an `easeInOut` ramp for the same reason: a
/// ramp has two endpoints where the motion stops dead, and stopping dead twice
/// a second is exactly what reads as "zooming in and out" instead of
/// "breathing".
private struct CasterMark: View {
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Seconds for one full turn of the ring. Slow on purpose — this is meant
    /// to be noticed only if you look at it.
    private static let turnPeriod: Double = 9
    /// Seconds for one breath, in and out.
    private static let breathPeriod: Double = 3.2

    private static let coreDiameter: CGFloat = 80
    private static let ringDiameter: CGFloat = 110
    private static let haloDiameter: CGFloat = 132

    var body: some View {
        // Paused rather than branched, so the two paths cannot drift apart.
        TimelineView(.animation(paused: reduceMotion)) { context in
            let time = context.date.timeIntervalSinceReferenceDate
            mark(
                scale: reduceMotion ? 1 : breath(at: time),
                angle: reduceMotion ? 0 : angle(at: time)
            )
        }
        .frame(width: Self.haloDiameter, height: Self.haloDiameter)
        .accessibilityHidden(true)
    }

    private func mark(scale: Double, angle: Double) -> some View {
        ZStack {
            // Sits the mark *in* the page rather than on top of it. The blur is
            // what keeps it from reading as a third circle.
            Circle()
                .fill(theme.accent.opacity(0.12))
                .frame(width: Self.haloDiameter, height: Self.haloDiameter)
                .blur(radius: 14)

            Circle()
                .stroke(
                    theme.accent.opacity(0.35),
                    style: StrokeStyle(lineWidth: 4, lineCap: .round, dash: [10, 12])
                )
                .frame(width: Self.ringDiameter, height: Self.ringDiameter)
                .rotationEffect(.degrees(angle))

            Circle()
                .fill(theme.accent)
                .frame(width: Self.coreDiameter, height: Self.coreDiameter)
                .scaleEffect(scale)
        }
    }

    /// 0.92…1.0. Shallow deliberately: the old one travelled 0.85…1.0, which on
    /// an 80-point circle is a 12-point swing and far too much movement for
    /// something that never stops.
    private func breath(at time: TimeInterval) -> Double {
        0.96 + 0.04 * sin(time * 2 * .pi / Self.breathPeriod)
    }

    private func angle(at time: TimeInterval) -> Double {
        (time / Self.turnPeriod).truncatingRemainder(dividingBy: 1) * 360
    }
}
