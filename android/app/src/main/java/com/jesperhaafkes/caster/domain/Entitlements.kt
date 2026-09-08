package com.jesperhaafkes.caster.domain

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the free app allows. Mirrored in `shared/monetization/offering.json`,
 * which iOS reads too — change one and change both, or the same app quietly
 * offers two different deals on the two phones. `OfferingParityTest` fails if
 * these drift.
 *
 * The games are deliberately absent from this list. All six modes, every
 * sub-mode and every wheel entry are free forever; only the *size of a saved
 * library* is capped. A group that keeps the flatmates, the five-a-side squad
 * and a chore list is the group that will happily pay for a fourth.
 */
object FreeLimits {
    const val SAVED_WHEELS = 3
    const val SAVED_ROSTERS = 3
}

/** The one thing there is to buy. */
object StoreProduct {
    /**
     * Play's product id, which is *not* the App Store's. Apple wants a reverse
     * -DNS string and Play wants a lowercase slug, so the two stores get two
     * ids for one product; both live in `offering.json` and neither app invents
     * its own.
     */
    const val PLUS = "caster_plus"
}

/**
 * One line on the paywall, and the key in `shared/monetization/offering.json`
 * that says whether it is true.
 */
data class PlusBenefit(val key: String, val glyph: String, val text: String)

/**
 * Everything the paywall claims Plus adds.
 *
 * A list rather than four hard-coded rows in the screen, so `OfferingParityTest`
 * can hold every claim against the contract: each [PlusBenefit.key] must be
 * `true` under `plusBenefits` in `offering.json`. That file deliberately carries
 * `soundPacks`, `persistentScoreboard` and `allPresetWheelPacks` as `false`
 * because they are not built, and the test is what stops one of them drifting
 * onto a purchase screen before it works.
 *
 * A benefit named here that the app cannot do is not a copy mistake. It is a
 * false claim on a screen that takes money.
 */
object PlusBenefits {
    val advertised = listOf(
        PlusBenefit("unlimitedSavedWheels", "∞", "Unlimited saved wheels and groups"),
        PlusBenefit("activeMemberToggle", "◐", "Sit people out without deleting them"),
        PlusBenefit("themePacks", "◈", "Four more palettes"),
        PlusBenefit("removesInterstitials", "⊘", "No interstitials"),
    )
}

/**
 * Which cap a screen ran into, so the Plus sheet can name it rather than
 * showing the same anonymous wall everywhere. The stores stay ignorant of this
 * — they only ever answer "no".
 */
enum class PlusPrompt {
    WHEEL_LIMIT,
    ROSTER_LIMIT,
    THEME,
    ACTIVE_MEMBERS,

    /** Opened deliberately from Settings rather than by hitting a wall. */
    BROWSING;

    /** One honest line about what was just refused. No countdowns, no discounts. */
    val reason: String
        get() = when (this) {
            WHEEL_LIMIT ->
                "You have ${FreeLimits.SAVED_WHEELS} wheels saved. Plus keeps as many as you like."
            ROSTER_LIMIT ->
                "You have ${FreeLimits.SAVED_ROSTERS} groups saved. Plus keeps as many as you like."
            THEME -> "Plus adds four more palettes."
            ACTIVE_MEMBERS -> "Plus lets you sit people out without deleting them."
            BROWSING -> "Everything the free app does, it keeps doing."
        }
}

/**
 * Whether this device has Caster Plus, cached on disk.
 *
 * The cache is authoritative offline, on purpose. Caster's whole pitch is "no
 * accounts and no network" and it is played in pubs and on trains; a paying
 * user with no signal must not lose their themes because a purchase could not
 * be re-queried. [com.jesperhaafkes.caster.billing.BillingService] reconciles
 * with Play whenever a connection turns up, and only ever writes through this
 * type.
 *
 * Same shape as [WheelStore] and [RosterStore]: Compose state, injectable
 * prefs, load in `init`.
 */
class EntitlementStore(private val prefs: SharedPreferences) {

    var hasPlus: Boolean by mutableStateOf(false)
        private set

    /**
     * Set once for someone who was already using Caster before any of this
     * existed. Their library predates the cap, so the cap does not apply to it.
     */
    var isLegacy: Boolean by mutableStateOf(false)
        private set

    init {
        hasPlus = prefs.getBoolean(PLUS_KEY, false)
        isLegacy = prefs.getBoolean(LEGACY_KEY, false)
    }

    // region What it unlocks

    /**
     * Legacy covers saved libraries only, not ads: it exists to avoid taking
     * something away, not to hand out the paid product to anyone who once
     * installed the app.
     */
    val hasUnlimitedLibraries: Boolean get() = hasPlus || isLegacy

    val savedWheelCapacity: Int
        get() = if (hasUnlimitedLibraries) Int.MAX_VALUE else FreeLimits.SAVED_WHEELS

    val savedRosterCapacity: Int
        get() = if (hasUnlimitedLibraries) Int.MAX_VALUE else FreeLimits.SAVED_ROSTERS

    val showsAds: Boolean get() = !hasPlus
    val hasThemes: Boolean get() = hasPlus
    val hasActiveMemberToggle: Boolean get() = hasPlus

    // endregion

    // region Writing

    /** Written only by the billing service, from a verified purchase. */
    fun setPlus(value: Boolean) {
        if (value == hasPlus) return
        hasPlus = value
        prefs.edit().putBoolean(PLUS_KEY, value).apply()
    }

    /**
     * Runs once, on the first launch of a build that has limits at all.
     *
     * Saved wheels and rosters were unbounded before this, so capping them is a
     * regression for anyone already using the app. Someone over the line when
     * the cap arrives keeps their library for good. Anyone under it is treated
     * as new — which is everybody arriving from Play.
     */
    fun grandfatherIfNeeded(wheelCount: Int, rosterCount: Int) {
        if (prefs.getBoolean(GRANDFATHERED_KEY, false)) return
        prefs.edit().putBoolean(GRANDFATHERED_KEY, true).apply()

        if (wheelCount <= FreeLimits.SAVED_WHEELS && rosterCount <= FreeLimits.SAVED_ROSTERS) {
            return
        }
        isLegacy = true
        prefs.edit().putBoolean(LEGACY_KEY, true).apply()
    }

    // endregion

    private companion object {
        const val PLUS_KEY = "caster.entitlements.plus"
        const val LEGACY_KEY = "caster.entitlements.legacy"
        const val GRANDFATHERED_KEY = "caster.entitlements.grandfathered"
    }
}
