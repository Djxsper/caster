import StoreKitTest
import XCTest
@testable import Caster

/// The money path, end to end, against a simulated storefront.
///
/// `SKTestSession` intercepts StoreKit inside this process, so `StoreService`
/// runs completely unmodified — the real `Product.products(for:)`, the real
/// `product.purchase()`, the real `Transaction.currentEntitlements`. Nothing is
/// stubbed and nothing is injected; the only thing that is fake is Apple.
///
/// That matters because it means these tests can be trusted before there is an
/// Apple Developer Program, an App Store Connect record, a sandbox tester or a
/// single euro spent. The first thing left untested after this is Apple's own
/// servers.
@MainActor
final class PurchaseFlowTests: XCTestCase {
    private var session: SKTestSession!

    override func setUp() async throws {
        try await super.setUp()
        session = try SKTestSession(contentsOf: Self.configurationURL)
        // Nothing here should ever wait for a human to tap Confirm.
        session.disableDialogs = true
        session.resetToDefaultState()
        session.clearTransactions()
    }

    override func tearDown() async throws {
        session = nil
        try await super.tearDown()
    }

    // MARK: - Buying

    func testTheProductLoadsAndMatchesTheContract() async throws {
        let store = makeStore().service
        await store.loadProduct()

        let product = try XCTUnwrap(store.plusProduct, "the fixture product should load")
        XCTAssertEqual(product.id, StoreProduct.plus)
        XCTAssertNotNil(store.displayPrice, "the paywall needs a storefront price, not a constant")
    }

    func testBuyingPlusUnlocksEverything() async throws {
        let (entitlements, store) = makeStore()
        await store.loadProduct()

        XCTAssertFalse(entitlements.hasPlus)

        await store.purchasePlus()

        XCTAssertTrue(entitlements.hasPlus, "a completed purchase must grant Plus")
        XCTAssertNil(store.lastError, "a successful purchase must not report an error")
        XCTAssertEqual(entitlements.savedWheelCapacity, .max)
        XCTAssertEqual(entitlements.savedRosterCapacity, .max)
        XCTAssertTrue(entitlements.hasThemes)
        XCTAssertFalse(entitlements.showsAds, "Plus is what turns the interstitial off")
    }

    /// The cache is what the app actually reads, so it has to survive the app
    /// dying — that is the whole reason a pub with no signal still shows a Plus
    /// user their palettes.
    func testTheEntitlementSurvivesARelaunch() async throws {
        let defaults = Self.freshDefaults()
        let entitlements = EntitlementStore(defaults: defaults)
        let store = StoreService(entitlements: entitlements)

        await store.loadProduct()
        await store.purchasePlus()
        XCTAssertTrue(entitlements.hasPlus)

        // A fresh store over the same defaults is what the next cold start sees.
        XCTAssertTrue(EntitlementStore(defaults: defaults).hasPlus)
    }

    // MARK: - Losing it again

    /// A refund drops the transaction out of `currentEntitlements`, which is the
    /// same path this takes. Keeping Plus after a refund would be theft in the
    /// other direction.
    func testLosingTheTransactionRevokesPlus() async throws {
        let (entitlements, store) = makeStore()
        await store.loadProduct()
        await store.purchasePlus()
        XCTAssertTrue(entitlements.hasPlus)

        session.clearTransactions()
        await store.refreshEntitlements()

        XCTAssertFalse(entitlements.hasPlus, "no entitlement means no Plus")
        XCTAssertEqual(entitlements.savedWheelCapacity, FreeLimits.savedWheels)
    }

    /// Nothing anybody typed is ever collateral damage. A refund takes the
    /// palettes and the ad-free-ness; it does not take the wheels.
    func testRevocationDoesNotTouchSavedWork() async throws {
        let defaults = Self.freshDefaults()
        let entitlements = EntitlementStore(defaults: defaults)
        let store = StoreService(entitlements: entitlements)
        let wheels = WheelStore(defaults: defaults)

        await store.loadProduct()
        await store.purchasePlus()
        wheels.capacity = entitlements.savedWheelCapacity
        for index in 0..<6 { wheels.createWheel(named: "Wheel \(index)") }
        XCTAssertEqual(wheels.wheels.count, 7)

        session.clearTransactions()
        await store.refreshEntitlements()
        wheels.capacity = entitlements.savedWheelCapacity

        XCTAssertEqual(wheels.wheels.count, 7, "every wheel is still there")
        XCTAssertFalse(wheels.canCreateWheel, "but the library is frozen at its size")
    }

    // MARK: - Restoring

    /// Required by App Review, and genuinely needed — entitlements follow an
    /// Apple ID, and this may be a new device.
    func testRestoreBringsPlusBackOnAFreshDevice() async throws {
        let (firstDevice, firstStore) = makeStore()
        await firstStore.loadProduct()
        await firstStore.purchasePlus()
        XCTAssertTrue(firstDevice.hasPlus)

        // A different device: same Apple ID and therefore the same StoreKit
        // entitlements, but nothing at all on disk.
        let (secondDevice, secondStore) = makeStore()
        XCTAssertFalse(secondDevice.hasPlus)

        await secondStore.refreshEntitlements()

        XCTAssertTrue(secondDevice.hasPlus, "the purchase should be found again")
    }

    // MARK: - Helpers

    private func makeStore() -> (entitlements: EntitlementStore, service: StoreService) {
        let entitlements = EntitlementStore(defaults: Self.freshDefaults())
        return (entitlements, StoreService(entitlements: entitlements))
    }

    private static func freshDefaults() -> UserDefaults {
        let name = "caster.tests.\(UUID().uuidString)"
        UserDefaults().removePersistentDomain(forName: name)
        return UserDefaults(suiteName: name)!
    }

    /// The same `Caster.storekit` the scheme uses, read from the source tree
    /// rather than copied into the test bundle — one file, no build step that
    /// could let the two versions disagree.
    ///
    /// This file lives at `<root>/CasterTests/PurchaseFlowTests.swift`.
    private static var configurationURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Caster.storekit")
    }
}
