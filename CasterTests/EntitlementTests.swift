import XCTest
@testable import Caster

/// The caps, the grandfather clause, and the promise that nothing already saved
/// is ever taken away.
@MainActor
final class EntitlementTests: XCTestCase {

    // MARK: - Caps

    func testFreeLibraryStopsAtTheLimit() {
        let store = WheelStore(defaults: Self.freshDefaults())
        store.capacity = FreeLimits.savedWheels

        // One starter wheel already exists, so there is room for the rest.
        while store.wheels.count < FreeLimits.savedWheels {
            XCTAssertNotNil(store.createWheel(named: "Another"))
        }

        XCTAssertFalse(store.canCreateWheel)
        XCTAssertNil(store.createWheel(named: "One too many"))
        XCTAssertFalse(store.duplicateSelected(), "duplicating is creating")
        XCTAssertEqual(store.wheels.count, FreeLimits.savedWheels)
    }

    func testPlusRaisesTheCap() {
        let entitlements = EntitlementStore(defaults: Self.freshDefaults())
        entitlements.setPlus(true)

        let store = WheelStore(defaults: Self.freshDefaults())
        store.capacity = entitlements.savedWheelCapacity

        for index in 0..<20 {
            XCTAssertNotNil(store.createWheel(named: "Wheel \(index)"))
        }
        XCTAssertEqual(store.wheels.count, 21)
    }

    /// The cap freezes a library; it never shrinks one. Somebody who already had
    /// eight wheels keeps all eight visible and editable.
    func testAnOversizedLibraryIsFrozenNotTrimmed() {
        let defaults = Self.freshDefaults()
        let store = WheelStore(defaults: defaults)
        store.capacity = .max
        for index in 0..<8 { store.createWheel(named: "Wheel \(index)") }

        store.capacity = FreeLimits.savedWheels
        XCTAssertEqual(store.wheels.count, 9, "nothing is deleted")
        XCTAssertFalse(store.canCreateWheel, "but nothing more can be added")
    }

    // MARK: - Grandfathering

    func testExistingUsersKeepUnlimitedLibraries() {
        let defaults = Self.freshDefaults()
        let entitlements = EntitlementStore(defaults: defaults)

        entitlements.grandfatherIfNeeded(wheelCount: 8, rosterCount: 1)

        XCTAssertTrue(entitlements.isLegacy)
        XCTAssertEqual(entitlements.savedWheelCapacity, .max)
        XCTAssertEqual(entitlements.savedRosterCapacity, .max)
        XCTAssertFalse(entitlements.hasPlus, "grandfathering is not a free copy of Plus")
        XCTAssertTrue(entitlements.showsAds, "nor is it ad-free")

        // Survives a relaunch.
        XCTAssertTrue(EntitlementStore(defaults: defaults).isLegacy)
    }

    func testNewUsersAreNotGrandfathered() {
        let entitlements = EntitlementStore(defaults: Self.freshDefaults())
        entitlements.grandfatherIfNeeded(wheelCount: 1, rosterCount: 1)

        XCTAssertFalse(entitlements.isLegacy)
        XCTAssertEqual(entitlements.savedWheelCapacity, FreeLimits.savedWheels)
    }

    /// It runs once, at the moment the cap arrives — not every launch. Otherwise
    /// a free user who reached the cap legitimately would be handed the legacy
    /// flag the next time they opened the app.
    func testGrandfatheringRunsOnlyOnce() {
        let defaults = Self.freshDefaults()
        EntitlementStore(defaults: defaults).grandfatherIfNeeded(wheelCount: 1, rosterCount: 1)

        let second = EntitlementStore(defaults: defaults)
        second.grandfatherIfNeeded(wheelCount: 99, rosterCount: 99)
        XCTAssertFalse(second.isLegacy)
    }

    // MARK: - Revocation

    func testRefundRevokesEverything() {
        let defaults = Self.freshDefaults()
        let entitlements = EntitlementStore(defaults: defaults)

        entitlements.setPlus(true)
        XCTAssertTrue(entitlements.hasThemes)
        XCTAssertFalse(entitlements.showsAds)

        entitlements.setPlus(false)
        XCTAssertFalse(entitlements.hasThemes)
        XCTAssertTrue(entitlements.showsAds)
        XCTAssertFalse(EntitlementStore(defaults: defaults).hasPlus, "written through to disk")
    }

    /// A Plus palette must not strand somebody after a refund, but the app also
    /// should not forget which one they had chosen.
    func testThemeFallsBackWithoutForgetting() {
        let themes = ThemeStore(defaults: Self.freshDefaults())
        themes.select(.midnight)

        XCTAssertEqual(themes.effective(hasPlus: false), .system)
        XCTAssertEqual(themes.effective(hasPlus: true), .midnight)
        XCTAssertEqual(themes.preferred, .midnight)
    }

    // MARK: - Active members

    func testSittingSomebodyOutDoesNotDeleteThem() {
        let store = RosterStore(defaults: Self.freshDefaults())
        store.honoursActiveFlags = true
        store.replaceAll(with: ["Alex", "Bo", "Cleo"])

        let bo = store.members[1]
        store.setActive(id: bo.id, false)

        XCTAssertEqual(store.members.count, 3, "still on the list")
        XCTAssertEqual(store.names, ["Alex", "Cleo"], "but not at the table")
        XCTAssertTrue(store.canPlay)
    }

    func testActiveFlagsAreIgnoredWithoutPlus() {
        let store = RosterStore(defaults: Self.freshDefaults())
        store.honoursActiveFlags = true
        store.replaceAll(with: ["Alex", "Bo", "Cleo"])
        store.setActive(id: store.members[1].id, false)

        // A refund turns the flags off. Everybody plays again rather than
        // silently staying benched.
        store.honoursActiveFlags = false
        XCTAssertEqual(store.names, ["Alex", "Bo", "Cleo"])
    }

    /// Rosters saved before the flag existed have no `isActive` key. The
    /// synthesised decoder would reject them outright and take every saved
    /// group with it, which is why `RosterMember` decodes by hand.
    func testRostersSavedBeforeTheFlagStillDecode() throws {
        let legacy = """
        [{"id":"\(UUID().uuidString)","name":"The flatmates","members":[
          {"id":"\(UUID().uuidString)","name":"Alex"},
          {"id":"\(UUID().uuidString)","name":"Bo"}
        ]}]
        """
        let decoded = try JSONDecoder().decode([SavedRoster].self, from: Data(legacy.utf8))

        XCTAssertEqual(decoded.first?.members.count, 2)
        XCTAssertTrue(decoded.first?.members.allSatisfy(\.isActive) ?? false)
    }

    private static func freshDefaults() -> UserDefaults {
        let name = "caster.tests.\(UUID().uuidString)"
        UserDefaults().removePersistentDomain(forName: name)
        return UserDefaults(suiteName: name)!
    }
}
