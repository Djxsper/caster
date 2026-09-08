import XCTest
@testable import Caster

/// Holds the Swift constants to `shared/monetization/offering.json`.
///
/// The commercial twin of Android's `ParityTest`. Caster is written twice, and a
/// free limit loosened on one platform and not the other is a silent bug: the
/// same app quietly offers two different deals and nothing fails. This is the
/// test that fails.
///
/// The fixture is read from the source tree via `#filePath` rather than copied
/// into the test bundle, so there is exactly one copy of it in the repository
/// and no build step that could let the two drift apart again.
final class OfferingParityTests: XCTestCase {

    func testFreeLimitsMatchTheContract() throws {
        let limits = try offering(section: "freeLimits")
        XCTAssertEqual(limits["savedWheels"] as? Int, FreeLimits.savedWheels)
        XCTAssertEqual(limits["savedRosters"] as? Int, FreeLimits.savedRosters)
    }

    func testAdPacingMatchesTheContract() throws {
        let pacing = try offering(section: "adPacing")
        XCTAssertEqual(pacing["minimumLaunches"] as? Int, AdPacing.minimumLaunches)
        XCTAssertEqual(pacing["minimumRoundsCompleted"] as? Int, AdPacing.minimumRoundsCompleted)
        XCTAssertEqual(pacing["perSessionCap"] as? Int, AdPacing.perSessionCap)
        XCTAssertEqual(pacing["quietPeriodSeconds"] as? Double, AdPacing.quietPeriod)
        XCTAssertEqual(pacing["launchGraceSeconds"] as? Double, AdPacing.launchGrace)
    }

    func testProductIdentifierMatchesTheContract() throws {
        let product = try offering(section: "product")
        XCTAssertEqual(product["plusIdentifier"] as? String, StoreProduct.plus)
    }

    func testThePaywallOnlyClaimsThingsThatAreBuilt() throws {
        let benefits = try offering(section: "plusBenefits")

        for item in PlusBenefits.advertised {
            let value = benefits[item.key] as? Bool
            XCTAssertNotNil(
                value,
                "the paywall lists \"\(item.text)\" but offering.json has no '\(item.key)' key"
            )
            XCTAssertEqual(
                value, true,
                "The paywall claims \"\(item.text)\" but '\(item.key)' is false in "
                    + "offering.json. That is a false claim on a screen that takes "
                    + "money. Build it and flip the flag, or take the line off the "
                    + "paywall — do not edit the flag to make this pass."
            )
        }
    }

    func testNothingUnbuiltHasQuietlyBecomeAdvertised() throws {
        // The other direction of the same guard. These three are false on
        // purpose; this fails if one is flipped true without also being put on
        // both paywalls, which is the moment they would otherwise disagree.
        let benefits = try offering(section: "plusBenefits")
        for key in ["allPresetWheelPacks", "soundPacks", "persistentScoreboard"] {
            guard benefits[key] as? Bool == true else { continue }
            XCTAssertTrue(
                PlusBenefits.advertised.contains { $0.key == key },
                "'\(key)' is now true in offering.json, so it must appear on both "
                    + "paywalls — add it to PlusBenefits here and in Entitlements.kt."
            )
        }
    }

    // MARK: - Fixture

    private func offering(section: String) throws -> [String: Any] {
        let url = Self.repositoryRoot
            .appendingPathComponent("shared/monetization/offering.json")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(
            try JSONSerialization.jsonObject(with: data) as? [String: Any],
            "offering.json is not an object"
        )
        return try XCTUnwrap(root[section] as? [String: Any], "missing section: \(section)")
    }

    /// This file lives at `<root>/CasterTests/OfferingParityTests.swift`.
    private static var repositoryRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }
}
