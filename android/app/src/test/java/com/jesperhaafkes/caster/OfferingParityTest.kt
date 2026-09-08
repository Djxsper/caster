package com.jesperhaafkes.caster

import com.jesperhaafkes.caster.domain.AdPacing
import com.jesperhaafkes.caster.domain.FreeLimits
import com.jesperhaafkes.caster.domain.PlusBenefits
import com.jesperhaafkes.caster.domain.StoreProduct
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds this app to the commercial contract in
 * `shared/monetization/offering.json`.
 *
 * The sibling of [ParityTest], and it exists for the same reason: Caster is
 * written twice, and two independent codebases drift silently. A free limit
 * loosened on one platform and not the other, or a quiet period that is eight
 * minutes on iPhone and three on Android, is the same app offering two
 * different deals to two people standing next to each other — and without this,
 * no build anywhere goes red.
 *
 * The iOS half is `CasterTests/OfferingParityTests.swift`, reading the same
 * file. Neither app owns it.
 */
class OfferingParityTest {

    private val offering: JSONObject by lazy {
        // Unit tests run with the module directory as the working directory.
        val file = File("../../shared/monetization/offering.json")
        assertTrue(
            "offering.json not found at ${file.absolutePath} — the monetization " +
                "fixture is shared with the iOS app and must not be moved without " +
                "updating both.",
            file.exists(),
        )
        JSONObject(file.readText())
    }

    @Test
    fun `the product id matches the contract`() {
        // Play's id, not Apple's. Getting this wrong means a purchase that can
        // never be found again, on a store that has already taken the money.
        assertEquals(
            offering.getJSONObject("product").getString("playIdentifier"),
            StoreProduct.PLUS,
        )
    }

    @Test
    fun `the free limits match the contract`() {
        val limits = offering.getJSONObject("freeLimits")
        assertEquals(limits.getInt("savedWheels"), FreeLimits.SAVED_WHEELS)
        assertEquals(limits.getInt("savedRosters"), FreeLimits.SAVED_ROSTERS)
    }

    @Test
    fun `the ad pacing matches the contract`() {
        val pacing = offering.getJSONObject("adPacing")
        assertEquals(pacing.getInt("minimumLaunches"), AdPacing.MINIMUM_LAUNCHES)
        assertEquals(pacing.getInt("minimumRoundsCompleted"), AdPacing.MINIMUM_ROUNDS_COMPLETED)
        assertEquals(pacing.getInt("perSessionCap"), AdPacing.PER_SESSION_CAP)
        assertEquals(
            pacing.getLong("quietPeriodSeconds") * 1000L,
            AdPacing.QUIET_PERIOD_MS,
        )
        assertEquals(
            pacing.getLong("launchGraceSeconds") * 1000L,
            AdPacing.LAUNCH_GRACE_MS,
        )
    }

    @Test
    fun `the paywall only claims things that are built`() {
        val benefits = offering.getJSONObject("plusBenefits")

        for (benefit in PlusBenefits.advertised) {
            assertTrue(
                "the paywall lists \"${benefit.text}\" but offering.json has no " +
                    "'${benefit.key}' key at all",
                benefits.has(benefit.key),
            )
            assertTrue(
                "the paywall claims \"${benefit.text}\" but '${benefit.key}' is false " +
                    "in offering.json. That is a false claim on a screen that takes " +
                    "money. Build it and flip the flag, or take the line off the " +
                    "paywall — do not edit the flag to make this pass.",
                benefits.getBoolean(benefit.key),
            )
        }
    }

    @Test
    fun `nothing unbuilt has quietly become advertised`() {
        // The other direction of the same guard. These three are false on
        // purpose; this fails if one is flipped true without also being built,
        // which is the moment somebody would otherwise put it on the paywall.
        val benefits = offering.getJSONObject("plusBenefits")
        for (key in listOf("allPresetWheelPacks", "soundPacks", "persistentScoreboard")) {
            if (benefits.getBoolean(key)) {
                assertTrue(
                    "'$key' is now true in offering.json, so it must appear on both " +
                        "paywalls — add it to PlusBenefits.advertised and to PlusView.swift.",
                    PlusBenefits.advertised.any { it.key == key },
                )
            }
        }
    }
}
