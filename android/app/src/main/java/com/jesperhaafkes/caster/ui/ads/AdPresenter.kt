package com.jesperhaafkes.caster.ui.ads

import com.jesperhaafkes.caster.BuildConfig

/**
 * What the app is allowed to ask an ad network for.
 *
 * Two methods, both fire-and-forget, and neither of them can block a game: an
 * ad that fails to load is an ad that does not appear, never a screen that
 * waits for one.
 *
 * The twin of `AdPresenter` in `Caster/Interface/Ads/AdPresenter.swift`.
 */
interface AdPresenter {
    /**
     * False on any build without an ad SDK linked, and on a device that has not
     * been given consent. Call sites check this rather than branching on the
     * build type themselves.
     */
    val isAvailable: Boolean

    /**
     * Fetches the next one in the background. Called when a game *starts*, so
     * that if one is going to be shown on the way out it is already in memory
     * and the transition does not stutter.
     */
    fun preload()

    /**
     * Only ever called from the return to the mode list, and only when
     * [com.jesperhaafkes.caster.domain.AdPacing] has already said yes.
     */
    fun presentInterstitial(onFinished: () -> Unit)

    /**
     * Opt-in only. [onReward] runs solely if the video was actually watched to
     * the end; a dismissed one grants nothing and says nothing.
     */
    fun presentRewarded(onReward: () -> Unit, onFinished: () -> Unit)
}

/**
 * The implementation the open-source build uses, and the one that runs in CI
 * and in every unit test.
 *
 * Caster ships from two places. The GitHub build stays what the README says it
 * is — no ad SDK, no network — and gets this. Only a Play release links a real
 * network, selected in [AdPresenterFactory]. Keeping the seam here rather than
 * at the call sites is what lets that promise stay literally true.
 */
class NoOpAdPresenter : AdPresenter {
    override val isAvailable: Boolean get() = false

    override fun preload() = Unit

    override fun presentInterstitial(onFinished: () -> Unit) {
        onFinished()
    }

    override fun presentRewarded(onReward: () -> Unit, onFinished: () -> Unit) {
        // No ad, so no reward. Granting one here would make the free build a
        // different product from the paid one.
        onFinished()
    }
}

object AdPresenterFactory {
    /**
     * No real network is linked in this repository, exactly as on iOS. A debug
     * build gets the stand-in so the pacing can be judged; everything else gets
     * nothing at all.
     *
     * Kotlin has no `#if DEBUG`, so [FakeAdPresenter] is compiled into every
     * build rather than stripped from release ones. R8 is on for release and
     * will drop it as unreachable, but the guarantee that matters is this
     * branch: a release build never constructs it.
     */
    fun make(): AdPresenter = if (BuildConfig.DEBUG) FakeAdPresenter() else NoOpAdPresenter()
}
