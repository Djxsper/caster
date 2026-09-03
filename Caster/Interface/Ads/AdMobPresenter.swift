#if canImport(GoogleMobileAds)
import Foundation
import GoogleMobileAds
import UIKit

/// The App Store build's ad presenter.
///
/// Inert until the Google Mobile Ads package is resolved — the whole file is
/// behind `canImport`, so a plain `git clone` still builds with no package
/// manager, exactly as the README promises.
///
/// Setup on the Mac, once:
///   1. File → Add Package Dependencies →
///      https://github.com/googleads/swift-package-manager-google-mobile-ads
///   2. Add `ADS_ENABLED` to *Active Compilation Conditions* on the App Store
///      configuration only.
///   3. Put the real `GADApplicationIdentifier` in the Info.plist settings, and
///      the real unit ids below.
///   4. Declare AdMob's required-reason APIs in `PrivacyInfo.xcprivacy`.
///
/// Until step 3 is done these stay Google's public test units. Pointing live
/// unit ids at development traffic is an AdMob policy violation and gets
/// accounts suspended, so they are the default rather than a TODO.
@MainActor
final class AdMobPresenter: NSObject, AdPresenter {
    private enum Unit {
        static let testInterstitial = "ca-app-pub-3940256099942544/4411468910"
        static let testRewarded = "ca-app-pub-3940256099942544/1712485313"

        static var interstitial: String { testInterstitial }
        static var rewarded: String { testRewarded }
    }

    private var interstitial: InterstitialAd?
    private var rewarded: RewardedAd?
    private var isLoading = false

    /// Set by the consent flow. Nothing is requested before a decision, which is
    /// what the EU requires and what Google's UMP SDK exists to collect.
    private(set) var hasConsent = false

    var isAvailable: Bool { hasConsent }

    func grantConsent(_ granted: Bool) {
        hasConsent = granted
        if granted { preload() }
    }

    func preload() {
        guard hasConsent, !isLoading, interstitial == nil else { return }
        isLoading = true

        Task { [weak self] in
            defer { self?.isLoading = false }
            self?.interstitial = try? await InterstitialAd.load(
                with: Unit.interstitial,
                request: Request()
            )
        }
    }

    func presentInterstitial(completion: @escaping () -> Void) {
        guard hasConsent, let ad = interstitial, let root = Self.rootViewController else {
            // Nothing loaded is not a failure worth telling anyone about. The
            // app carries on and tries again next time.
            completion()
            return
        }

        interstitial = nil
        ad.present(from: root)
        completion()
        preload()
    }

    func presentRewarded(onReward: @escaping () -> Void, completion: @escaping () -> Void) {
        guard hasConsent, let root = Self.rootViewController else {
            completion()
            return
        }

        Task {
            guard let ad = try? await RewardedAd.load(with: Unit.rewarded, request: Request()) else {
                completion()
                return
            }
            // The closure fires only on a completed view; a dismissal never
            // reaches it, so a skipped ad grants nothing.
            ad.present(from: root) { onReward() }
            completion()
        }
    }

    private static var rootViewController: UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .rootViewController
    }
}
#endif
