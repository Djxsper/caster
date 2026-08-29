import Foundation

/// The six mini-games. A plain value enum — `@Observable` only applies to
/// classes, and an enum's cases are immutable anyway.
enum GameMode: String, CaseIterable, Identifiable, Hashable {
    case fingerPicker
    case pinwheel
    case threeCupShuffle
    case uppercut
    case tapFrenzy
    case chicken

    /// `Identifiable` conformance. The raw value is already unique and stable,
    /// so it doubles as the identity used by `ForEach`.
    var id: String { rawValue }

    var title: String {
        switch self {
        case .fingerPicker: return "Finger Picker"
        case .pinwheel: return "Pinwheel"
        case .threeCupShuffle: return "Three Cup Shuffle"
        case .uppercut: return "Uppercut"
        case .tapFrenzy: return "Tap Frenzy"
        case .chicken: return "Chicken"
        }
    }

    var summary: String {
        switch self {
        case .fingerPicker: return "Each thumb adds tickets. Fewest tickets lose."
        case .pinwheel: return "Flick the wheel. Each player wins by being faster."
        case .threeCupShuffle: return "Find the ball. Fewest tickets lose the round."
        case .uppercut: return "First tap wins. Each player's odds shift with their reaction."
        case .tapFrenzy: return "Five seconds of taps. Each tap adds tickets."
        case .chicken: return "Last to flinch scores. Each player gains tickets from each other."
        }
    }

    /// SF Symbol name. Every one of these ships in SF Symbols 4 (iOS 16+).
    var iconName: String {
        switch self {
        case .fingerPicker: return "hand.tap"
        case .pinwheel: return "arrow.triangle.2.circlepath"
        case .threeCupShuffle: return "cup.and.saucer"
        case .uppercut: return "bolt.fill"
        case .tapFrenzy: return "hand.tap.fill"
        case .chicken: return "flame.fill"
        }
    }

    /// How long players hold before the round resolves.
    var holdDuration: TimeInterval {
        switch self {
        case .fingerPicker, .threeCupShuffle: return 3.0
        case .pinwheel, .uppercut: return 2.0
        case .tapFrenzy: return 5.0
        case .chicken: return 4.0
        }
    }
}
