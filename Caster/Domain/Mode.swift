import Foundation

/// The six mini-games. A plain value enum — `@Observable` only applies to
/// classes, and an enum's cases are immutable anyway.
enum GameMode: String, CaseIterable, Identifiable, Hashable {
    case fingerPicker
    case pinwheel
    case hotPotato
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
        case .hotPotato: return "Hot Potato"
        case .uppercut: return "Uppercut"
        case .tapFrenzy: return "Tap Frenzy"
        case .chicken: return "Chicken"
        }
    }

    var summary: String {
        switch self {
        case .fingerPicker:
            return "Everyone holds a finger down. One gets picked — or split into teams, or put in order."
        case .pinwheel:
            return "Spin a wheel of names or anything else you type in. As many entries as you like."
        case .hotPotato:
            return "A hidden fuse burns while you pass the phone. Whoever holds it when it blows loses."
        case .uppercut:
            return "Hold a finger. When the light flips and the tone hits, lift. Slowest reaction loses."
        case .tapFrenzy:
            return "Claim a circle and hammer it. Decide first whether taps push the draw away from you or towards you."
        case .chicken:
            return "Circles light up one at a time. Let go in time and you are out. Last one left loses."
        }
    }

    /// SF Symbol name. Every one of these ships in SF Symbols 4 (iOS 16+).
    var iconName: String {
        switch self {
        case .fingerPicker: return "hand.tap"
        case .pinwheel: return "arrow.triangle.2.circlepath"
        case .hotPotato: return "timer"
        case .uppercut: return "bolt.fill"
        case .tapFrenzy: return "hand.tap.fill"
        case .chicken: return "flame.fill"
        }
    }

    /// The screen, if any, that has to run before the game itself.
    var setupRoute: Route? {
        switch self {
        case .hotPotato: return .playerSetup
        case .pinwheel: return .wheelSetup
        case .fingerPicker, .uppercut, .tapFrenzy, .chicken: return nil
        }
    }
}
