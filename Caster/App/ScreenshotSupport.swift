#if DEBUG
import Foundation

/// Lets CI deep-link straight to a screen via a launch argument, so every
/// screen can be screenshotted without adding a UI-test target.
///
/// Debug builds only — this whole file compiles out of Release.
///
///     xcrun simctl launch <device> com.example.Caster -screen chicken
enum ScreenshotSupport {
    /// The screen named on the command line, if any.
    private static var requestedKey: String? {
        let arguments = ProcessInfo.processInfo.arguments
        guard let flagIndex = arguments.firstIndex(of: "-screen"),
              arguments.indices.contains(flagIndex + 1) else { return nil }
        return arguments[flagIndex + 1]
    }

    /// A game mode name maps straight to that game's screen; anything else is
    /// one of the setup screens.
    static var requestedMode: GameMode? {
        guard let key = requestedKey else { return nil }
        return GameMode(rawValue: key)
    }

    /// The navigation path to install on launch, or empty for normal startup.
    static var requestedRoute: [Route] {
        guard let key = requestedKey else { return [] }

        if let mode = GameMode(rawValue: key) {
            var route: [Route] = [.modeSelect]
            if let setup = mode.setupRoute { route.append(setup) }
            route.append(.game(mode))
            return route
        }

        switch key {
        case "modeSelect":  return [.modeSelect]
        case "playerSetup": return [.modeSelect, .playerSetup]
        case "wheelSetup":  return [.modeSelect, .wheelSetup]
        default:            return []
        }
    }

    /// The name-based screens are empty without players, so seed a table.
    static var needsSeededPlayers: Bool {
        !requestedRoute.isEmpty
    }

    static let sampleNames = ["Ada", "Bo", "Cy", "Di"]
}
#endif
