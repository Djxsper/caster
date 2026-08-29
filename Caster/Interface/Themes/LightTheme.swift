import SwiftUI

enum LightTheme {
    static let palette = Theme(
        background: Color(red: 1.0, green: 1.0, blue: 1.0),
        surface: Color(red: 1.0, green: 1.0, blue: 1.0),
        surfaceRaised: Color(red: 0.949, green: 0.949, blue: 0.949),
        border: Color(red: 0.902, green: 0.902, blue: 0.902),
        textPrimary: Color(red: 0.063, green: 0.063, blue: 0.063),
        textSecondary: Color(red: 0.404, green: 0.404, blue: 0.404),
        accent: Color(red: 0.235, green: 0.506, blue: 0.882),
        playerColors: PlayerPalette.colors
    )
}
