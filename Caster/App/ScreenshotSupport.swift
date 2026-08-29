#if DEBUG
import Foundation

/// Lets CI deep-link straight to a screen via a launch argument, so every
/// screen can be screenshotted without adding a UI-test target.
///
/// Debug builds only — this whole file compiles out of Release.
///
///     xcrun simctl launch <device> com.example.Caster -screen draw
enum ScreenshotSupport {
    /// The navigation path to install on launch, or empty for normal startup.
    static var requestedRoute: [Route] {
        let arguments = ProcessInfo.processInfo.arguments
        guard let flagIndex = arguments.firstIndex(of: "-screen"),
              arguments.indices.contains(flagIndex + 1) else { return [] }

        switch arguments[flagIndex + 1] {
        case "modeSelect":  return [.modeSelect]
        case "playerSetup": return [.modeSelect, .playerSetup]
        case "draw":        return [.modeSelect, .playerSetup, .draw]
        default:            return []
        }
    }

    /// The draw screen is empty without players, so seed a table for it.
    static var needsSeededPlayers: Bool {
        requestedRoute.contains(.draw)
    }

    static let sampleNames = ["Ada", "Bo", "Cy", "Di"]
}
#endif
