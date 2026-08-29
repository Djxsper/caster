import SwiftUI

/// A colour palette. `LightTheme` and `DarkTheme` are the two concrete values;
/// views read `\.theme` from the environment rather than hard-coding either one,
/// which is what keeps the app honest in Dark Mode.
struct Theme {
    let background: Color
    let surface: Color
    let surfaceRaised: Color
    let border: Color
    let textPrimary: Color
    let textSecondary: Color
    let accent: Color
    let playerColors: [Color]

    /// Colour for a seat, wrapping around when there are more players than colours.
    func playerColor(for index: Int) -> Color {
        guard !playerColors.isEmpty else { return accent }
        // `%` on a negative index would be negative and trap the subscript.
        return playerColors[abs(index) % playerColors.count]
    }

    static func forScheme(_ scheme: ColorScheme) -> Theme {
        scheme == .dark ? DarkTheme.palette : LightTheme.palette
    }
}

/// The eight seat colours, shared by both palettes.
enum PlayerPalette {
    static let colors: [Color] = [
        Color(red: 0.235, green: 0.506, blue: 0.882),  // 1 — Blue
        Color(red: 0.063, green: 0.690, blue: 0.498),  // 2 — Green
        Color(red: 0.859, green: 0.780, blue: 0.384),  // 3 — Amber
        Color(red: 0.560, green: 0.365, blue: 0.250),  // 4 — Brown
        Color(red: 0.533, green: 0.373, blue: 0.831),  // 5 — Purple
        Color(red: 0.961, green: 0.298, blue: 0.486),  // 6 — Pink
        Color(red: 0.063, green: 0.690, blue: 0.859),  // 7 — Cyan
        Color(red: 0.392, green: 0.447, blue: 0.509),  // 8 — Slate
    ]
}

private struct ThemeKey: EnvironmentKey {
    static let defaultValue = LightTheme.palette
}

extension EnvironmentValues {
    var theme: Theme {
        get { self[ThemeKey.self] }
        set { self[ThemeKey.self] = newValue }
    }
}
