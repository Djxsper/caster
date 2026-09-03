import SwiftUI
import Observation

/// The palette the app is wearing.
///
/// `.system` is the free default and follows the phone's light/dark setting, as
/// the app always has. The named ones are Plus, and each is a fixed look rather
/// than a light/dark pair — picking "Midnight" is choosing a dark app, not
/// choosing how a dark app looks.
///
/// Every one of them keeps `PlayerPalette.colors` for the seats. Seat colour is
/// identity, not decoration: it is how a person finds their own finger on a
/// crowded screen, it is pinned by `shared/parity/golden.json` so the two
/// platforms agree, and a theme that recoloured it would change how the games
/// *play*. Themes dress the chrome and nothing else.
enum ThemeSelection: String, CaseIterable, Identifiable, Codable {
    case system
    case midnight
    case dusk
    case forest
    case paper

    var id: String { rawValue }

    var isPlus: Bool { self != .system }

    var title: String {
        switch self {
        case .system: return "System"
        case .midnight: return "Midnight"
        case .dusk: return "Dusk"
        case .forest: return "Forest"
        case .paper: return "Paper"
        }
    }

    var subtitle: String {
        switch self {
        case .system: return "Follows your phone"
        case .midnight: return "Near-black, high contrast"
        case .dusk: return "Warm plum and amber"
        case .forest: return "Deep green, low glare"
        case .paper: return "Off-white and ink"
        }
    }

    func palette(for scheme: ColorScheme) -> Theme {
        switch self {
        case .system: return Theme.forScheme(scheme)
        case .midnight: return PlusPalettes.midnight
        case .dusk: return PlusPalettes.dusk
        case .forest: return PlusPalettes.forest
        case .paper: return PlusPalettes.paper
        }
    }

    /// The two swatches the settings row shows, so a palette can be judged
    /// without applying it.
    func swatch(for scheme: ColorScheme) -> (background: Color, accent: Color) {
        let theme = palette(for: scheme)
        return (theme.background, theme.accent)
    }
}

/// The four Plus palettes. Each is one `Theme` value of the same eleven fields
/// as `LightTheme` and `DarkTheme` — there is no new machinery here, which is
/// what makes these cheap enough to be worth shipping.
enum PlusPalettes {
    static let midnight = Theme(
        background: Color(red: 0.008, green: 0.012, blue: 0.024),
        surface: Color(red: 0.024, green: 0.031, blue: 0.051),
        surfaceRaised: Color(red: 0.055, green: 0.071, blue: 0.106),
        border: Color(red: 0.129, green: 0.157, blue: 0.212),
        textPrimary: Color(red: 0.925, green: 0.945, blue: 0.980),
        textSecondary: Color(red: 0.529, green: 0.573, blue: 0.647),
        accent: Color(red: 0.318, green: 0.627, blue: 0.996),
        success: Color(red: 0.204, green: 0.827, blue: 0.600),
        danger: Color(red: 0.984, green: 0.404, blue: 0.447),
        warning: Color(red: 0.984, green: 0.780, blue: 0.353),
        playerColors: PlayerPalette.colors
    )

    static let dusk = Theme(
        background: Color(red: 0.086, green: 0.043, blue: 0.098),
        surface: Color(red: 0.129, green: 0.067, blue: 0.145),
        surfaceRaised: Color(red: 0.192, green: 0.102, blue: 0.208),
        border: Color(red: 0.310, green: 0.180, blue: 0.325),
        textPrimary: Color(red: 0.984, green: 0.949, blue: 0.965),
        textSecondary: Color(red: 0.706, green: 0.588, blue: 0.694),
        accent: Color(red: 0.976, green: 0.596, blue: 0.310),
        success: Color(red: 0.353, green: 0.812, blue: 0.596),
        danger: Color(red: 0.965, green: 0.400, blue: 0.482),
        warning: Color(red: 0.988, green: 0.808, blue: 0.400),
        playerColors: PlayerPalette.colors
    )

    static let forest = Theme(
        background: Color(red: 0.031, green: 0.086, blue: 0.067),
        surface: Color(red: 0.047, green: 0.125, blue: 0.098),
        surfaceRaised: Color(red: 0.075, green: 0.180, blue: 0.141),
        border: Color(red: 0.145, green: 0.290, blue: 0.235),
        textPrimary: Color(red: 0.925, green: 0.965, blue: 0.941),
        textSecondary: Color(red: 0.573, green: 0.694, blue: 0.635),
        accent: Color(red: 0.443, green: 0.827, blue: 0.502),
        success: Color(red: 0.325, green: 0.847, blue: 0.588),
        danger: Color(red: 0.937, green: 0.435, blue: 0.396),
        warning: Color(red: 0.933, green: 0.780, blue: 0.365),
        playerColors: PlayerPalette.colors
    )

    static let paper = Theme(
        background: Color(red: 0.976, green: 0.965, blue: 0.933),
        surface: Color(red: 0.988, green: 0.980, blue: 0.957),
        surfaceRaised: Color(red: 0.937, green: 0.918, blue: 0.871),
        border: Color(red: 0.859, green: 0.831, blue: 0.769),
        textPrimary: Color(red: 0.114, green: 0.106, blue: 0.086),
        textSecondary: Color(red: 0.396, green: 0.373, blue: 0.325),
        accent: Color(red: 0.706, green: 0.325, blue: 0.184),
        success: Color(red: 0.216, green: 0.518, blue: 0.353),
        danger: Color(red: 0.729, green: 0.204, blue: 0.204),
        warning: Color(red: 0.729, green: 0.510, blue: 0.114),
        playerColors: PlayerPalette.colors
    )
}

/// Remembers the chosen palette. Its own tiny store rather than a field on
/// `AppEnvironment`, for the same reason the wheels have one: a preference that
/// lives in memory is a preference that a relaunch throws away.
///
/// Falls back to `.system` whenever Plus is not held, so a refund or a restore
/// on a new device can never strand somebody in a palette they no longer own —
/// without forgetting which one they had picked, in case they buy it again.
@Observable
@MainActor
final class ThemeStore {
    private static let storageKey = "caster.theme.selection"

    private(set) var preferred: ThemeSelection = .system

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let raw = defaults.string(forKey: Self.storageKey),
           let stored = ThemeSelection(rawValue: raw) {
            preferred = stored
        }
    }

    func select(_ selection: ThemeSelection) {
        preferred = selection
        defaults.set(selection.rawValue, forKey: Self.storageKey)
    }

    func effective(hasPlus: Bool) -> ThemeSelection {
        (preferred.isPlus && !hasPlus) ? .system : preferred
    }
}
