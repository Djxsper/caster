import SwiftUI
import Observation

/// App-wide services. Injected with `.environment(_:)` and read with
/// `@Environment(AppEnvironment.self)` — `@EnvironmentObject` only works with
/// `ObservableObject`, not with `@Observable` types.
///
/// Seat colours live on `Theme` rather than here, so light and dark palettes
/// have a single owner.
@Observable
@MainActor
final class AppEnvironment {
    let hapticEngine = HapticEngine.shared
}
