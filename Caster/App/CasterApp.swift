import SwiftUI

@main
struct CasterApp: App {
    @State private var environment = AppEnvironment()
    @State private var gameState = GameState()
    @State private var wheelStore = WheelStore()
    @State private var rosterStore = RosterStore()
    @State private var themeStore = ThemeStore()
    @State private var entitlements: EntitlementStore
    @State private var storeService: StoreService

    /// The two commerce types are built here rather than as property defaults
    /// because `StoreService` needs the entitlement store to write through to,
    /// and there is only ever one of each.
    init() {
        let entitlements = EntitlementStore()
        _entitlements = State(initialValue: entitlements)
        _storeService = State(initialValue: StoreService(entitlements: entitlements))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(environment)
                .environment(gameState)
                .environment(wheelStore)
                .environment(rosterStore)
                .environment(themeStore)
                .environment(entitlements)
                .environment(storeService)
        }
    }
}

/// Reads the system colour scheme and publishes the matching palette. Doing it
/// in a view (rather than in `App`) is what makes the theme track a live
/// light/dark switch.
///
/// Also the one place entitlements are turned into limits. The stores hold a
/// plain `capacity` number and know nothing about purchases; this view is the
/// seam between "what was bought" and "what the app allows", so there is exactly
/// one line to read to find out how the two are connected.
private struct RootView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    @Environment(AppEnvironment.self) private var environment
    @Environment(WheelStore.self) private var wheelStore
    @Environment(RosterStore.self) private var rosterStore
    @Environment(ThemeStore.self) private var themeStore
    @Environment(EntitlementStore.self) private var entitlements
    @Environment(StoreService.self) private var storeService

    var body: some View {
        LaunchView()
            .environment(\.theme, resolvedTheme)
            .modifier(FakeAdOverlay())
            .task { start() }
            .onChange(of: entitlements.hasPlus) { _, _ in
                // Covers the whole lifecycle in one line: a purchase, a restore
                // on a new device, and a refund revoking it again.
                applyEntitlements()
            }
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

    /// Falls back to the system palette whenever Plus is not held, without
    /// forgetting which one was picked.
    private var resolvedTheme: Theme {
        themeStore
            .effective(hasPlus: entitlements.hasPlus)
            .palette(for: colorScheme)
    }

    private func start() {
        // Before the caps are applied, so an existing library is measured as it
        // stands rather than after being refused something.
        entitlements.grandfatherIfNeeded(
            wheelCount: wheelStore.wheels.count,
            rosterCount: rosterStore.rosters.count
        )
        applyEntitlements()
        environment.pacing.beginSession()
        storeService.start()
    }

    private func applyEntitlements() {
        wheelStore.capacity = entitlements.savedWheelCapacity
        rosterStore.capacity = entitlements.savedRosterCapacity
        rosterStore.honoursActiveFlags = entitlements.hasActiveMemberToggle
    }
}

/// Presents the stand-in interstitial over the whole app in debug builds, and
/// is a no-op everywhere else.
///
/// A modifier rather than an inline `#if` around a `.fullScreenCover`, so the
/// Release view tree is not a different shape from the Debug one — the one thing
/// that would make testing on a debug build stop telling you about the real one.
private struct FakeAdOverlay: ViewModifier {
    #if DEBUG
    @Environment(AppEnvironment.self) private var environment
    #endif

    func body(content: Content) -> some View {
        #if DEBUG
        content.fullScreenCover(isPresented: Binding(
            get: { environment.fakeAds?.isShowing ?? false },
            set: { if !$0 { environment.fakeAds?.dismiss() } }
        )) {
            FakeAdView()
        }
        #else
        content
        #endif
    }
}
