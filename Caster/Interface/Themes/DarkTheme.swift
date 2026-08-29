import SwiftUI

enum DarkTheme {
    static let palette = Theme(
        background: Color(red: 0.027, green: 0.027, blue: 0.030),
        surface: Color(red: 0.047, green: 0.047, blue: 0.047),
        surfaceRaised: Color(red: 0.106, green: 0.106, blue: 0.106),
        border: Color(red: 0.182, green: 0.182, blue: 0.182),
        textPrimary: Color(red: 0.965, green: 0.965, blue: 0.965),
        textSecondary: Color(red: 0.569, green: 0.569, blue: 0.569),
        accent: Color(red: 0.235, green: 0.506, blue: 0.882),
        success: Color(red: 0.157, green: 0.780, blue: 0.549),
        danger: Color(red: 0.937, green: 0.325, blue: 0.376),
        warning: Color(red: 0.976, green: 0.749, blue: 0.290),
        playerColors: PlayerPalette.colors
    )
}
