import SwiftUI

@main
struct CasterApp: App {
    @State private var environment = AppEnvironment()
    @State private var gameState = GameState()
    @State private var wheelStore = WheelStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(environment)
                .environment(gameState)
                .environment(wheelStore)
        }
    }
}

/// Reads the system colour scheme and publishes the matching palette. Doing it
/// in a view (rather than in `App`) is what makes the theme track a live
/// light/dark switch.
private struct RootView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        LaunchView()
            .environment(\.theme, Theme.forScheme(colorScheme))
            .onChange(of: scenePhase) { _, phase in
                // Own the engines here rather than in a screen's `onDisappear`:
                // pushing a destination can tear them down mid-round otherwise.
                switch phase {
                case .active:
                    environment.hapticEngine.startEngine()
                    environment.soundEngine.start()
                case .background, .inactive:
                    environment.hapticEngine.endEngine()
                    environment.soundEngine.stop()
                @unknown default:
                    break
                }
            }
    }
}
