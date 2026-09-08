import Foundation
import Observation

/// What the free app allows. Mirrored in `shared/monetization/offering.json`,
/// which Android reads too — change one and change both, or the same app quietly
/// offers two different deals on the two phones.
///
/// The games are deliberately absent from this list. All six modes, every
/// sub-mode and every wheel entry are free forever; only the *size of a saved
/// library* is capped. A group that keeps the flatmates, the five-a-side squad
/// and a chore list is the group that will happily pay for a fourth.
enum FreeLimits {
    static let savedWheels = 3
    static let savedRosters = 3
}

/// The one thing there is to buy.
enum StoreProduct {
    static let plus = "com.jesperhaafkes.Caster.plus"
}

/// One line on the paywall, and the key under `plusBenefits` in
/// `shared/monetization/offering.json` that says whether it is true.
struct PlusBenefit {
    let key: String
    let symbol: String
    let text: String
}

/// Everything the paywall claims Plus adds.
///
/// A list rather than four rows written into `PlusView`, so
/// `OfferingParityTests` can hold every claim against the contract: each `key`
/// must be `true` under `plusBenefits` in `offering.json`. That file carries
/// `soundPacks`, `persistentScoreboard` and `allPresetWheelPacks` as `false` on
/// purpose because they are not built, and the test is what stops one of them
/// drifting back onto a purchase screen before it works.
///
/// A benefit named here that the app cannot do is not a copy mistake. It is a
/// false claim on a screen that takes money — and an App Review rejection.
enum PlusBenefits {
    static let advertised: [PlusBenefit] = [
        PlusBenefit(
            key: "unlimitedSavedWheels",
            symbol: "infinity",
            text: "Unlimited saved wheels and groups"
        ),
        PlusBenefit(
            key: "activeMemberToggle",
            symbol: "person.crop.circle.badge.checkmark",
            text: "Sit people out without deleting them"
        ),
        PlusBenefit(
            key: "themePacks",
            symbol: "paintpalette.fill",
            text: "Four more palettes"
        ),
        PlusBenefit(
            key: "removesInterstitials",
            symbol: "hand.raised.fill",
            text: "No interstitials"
        ),
    ]
}

/// Which cap a screen ran into, so the Plus sheet can name it rather than
/// showing the same anonymous wall everywhere. The stores stay ignorant of
/// this — they only ever answer "no".
enum PlusPrompt: String, Identifiable {
    case wheelLimit
    case rosterLimit
    case theme
    case soundPack
    case scoreboard
    case activeMembers
    /// Opened deliberately from Settings rather than by hitting a wall.
    case browsing

    var id: String { rawValue }

    /// One honest line about what was just refused. No countdowns, no discounts.
    var reason: String {
        switch self {
        case .wheelLimit:
            return "You have \(FreeLimits.savedWheels) wheels saved. Plus keeps as many as you like."
        case .rosterLimit:
            return "You have \(FreeLimits.savedRosters) groups saved. Plus keeps as many as you like."
        case .theme:
            return "Plus adds four more palettes."
        case .soundPack:
            return "Plus adds three more sound packs."
        case .scoreboard:
            return "Plus remembers the score between sessions."
        case .activeMembers:
            return "Plus lets you sit people out without deleting them."
        case .browsing:
            return "Everything the free app does, it keeps doing."
        }
    }
}

/// Whether this device has Caster Plus, cached on disk.
///
/// The cache is authoritative offline, on purpose. Caster's whole pitch is "no
/// accounts and no network" and it is played in pubs and on trains; a paying
/// user with no signal must not lose their themes because a receipt could not be
/// re-fetched. `StoreService` reconciles with StoreKit whenever a network turns
/// up, and only ever writes through this type.
///
/// Same shape as `WheelStore` and `RosterStore`: `@Observable`, injectable
/// `UserDefaults`, `load()`/`save()`.
@Observable
@MainActor
final class EntitlementStore {
    private static let plusKey = "caster.entitlements.plus"
    private static let legacyKey = "caster.entitlements.legacy"
    private static let grandfatheredKey = "caster.entitlements.grandfathered"

    private(set) var hasPlus = false

    /// Set once for someone who was already using Caster before any of this
    /// existed. Their library predates the cap, so the cap does not apply to it.
    private(set) var isLegacy = false

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        hasPlus = defaults.bool(forKey: Self.plusKey)
        isLegacy = defaults.bool(forKey: Self.legacyKey)
    }

    // MARK: - What it unlocks

    /// Legacy covers saved libraries only, not ads: it exists to avoid taking
    /// something away, not to hand out the paid product to anyone who once
    /// sideloaded the app.
    var hasUnlimitedLibraries: Bool { hasPlus || isLegacy }

    var savedWheelCapacity: Int {
        hasUnlimitedLibraries ? .max : FreeLimits.savedWheels
    }

    var savedRosterCapacity: Int {
        hasUnlimitedLibraries ? .max : FreeLimits.savedRosters
    }

    var showsAds: Bool { !hasPlus }
    var hasThemes: Bool { hasPlus }
    var hasSoundPacks: Bool { hasPlus }
    var hasScoreboard: Bool { hasPlus }
    var hasActiveMemberToggle: Bool { hasPlus }

    // MARK: - Writing

    /// Written only by `StoreService`, from a verified transaction.
    func setPlus(_ value: Bool) {
        guard value != hasPlus else { return }
        hasPlus = value
        defaults.set(value, forKey: Self.plusKey)
    }

    /// Runs once, on the first launch of a build that has limits at all.
    ///
    /// Saved wheels and rosters were unbounded before this, so capping them is a
    /// regression for anyone already using the app. Someone over the line when
    /// the cap arrives keeps their library for good. Anyone under it is treated
    /// as new — which is everybody arriving from the App Store.
    func grandfatherIfNeeded(wheelCount: Int, rosterCount: Int) {
        guard !defaults.bool(forKey: Self.grandfatheredKey) else { return }
        defaults.set(true, forKey: Self.grandfatheredKey)

        guard wheelCount > FreeLimits.savedWheels || rosterCount > FreeLimits.savedRosters else {
            return
        }
        isLegacy = true
        defaults.set(true, forKey: Self.legacyKey)
    }
}
