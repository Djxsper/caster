import Foundation

/// What the app is allowed to ask an ad network for.
///
/// Two methods, both fire-and-forget, and neither of them can block a game: an
/// ad that fails to load is an ad that does not appear, never a screen that
/// waits for one.
@MainActor
protocol AdPresenter: AnyObject {
    /// False on any build without an ad SDK linked, and on a device that has not
    /// been given consent. Call sites check this rather than branching on the
    /// build configuration themselves.
    var isAvailable: Bool { get }

    /// Fetches the next one in the background. Called when a game *starts*, so
    /// that if one is going to be shown on the way out it is already in memory
    /// and the transition does not stutter.
    func preload()

    /// Only ever called from the return to the mode list, and only when
    /// `AdPacing` has already said yes.
    func presentInterstitial(completion: @escaping () -> Void)

    /// Opt-in only. `onReward` runs solely if the video was actually watched to
    /// the end; a dismissed one grants nothing and says nothing.
    func presentRewarded(onReward: @escaping () -> Void, completion: @escaping () -> Void)
}

/// The implementation the open-source build uses, and the one that runs in CI,
/// in previews and in every unit test.
///
/// Caster ships from two places. The GitHub build stays what the README says it
/// is — no package manager, no dependencies, no network — and gets this. Only
/// the App Store build links an ad SDK, selected by the `ADS_ENABLED` build
/// setting in `AdPresenterFactory`. Keeping the seam here rather than at the
/// call sites is what lets that promise stay literally true.
@MainActor
final class NoOpAdPresenter: AdPresenter {
    var isAvailable: Bool { false }

    func preload() {}

    func presentInterstitial(completion: @escaping () -> Void) {
        completion()
    }

    func presentRewarded(onReward: @escaping () -> Void, completion: @escaping () -> Void) {
        // No ad, so no reward. Granting one here would make the free build a
        // different product from the paid one.
        completion()
    }
}

@MainActor
enum AdPresenterFactory {
    /// Both conditions are deliberate. `ADS_ENABLED` is the intent, set only on
    /// the App Store configuration; `canImport` is the fact, false until the
    /// SDK package is actually resolved. Requiring both means a checkout that
    /// has the flag but not the dependency still builds.
    static func make() -> AdPresenter {
        #if ADS_ENABLED && canImport(GoogleMobileAds)
        return AdMobPresenter()
        #elseif DEBUG
        // No SDK, but something to judge the pacing against. Debug only, so the
        // public IPA and the App Store binary never see it.
        return FakeAdPresenter()
        #else
        return NoOpAdPresenter()
        #endif
    }
}
