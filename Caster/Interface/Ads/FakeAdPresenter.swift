#if DEBUG
import SwiftUI
import Observation

/// A stand-in interstitial, for judging the pacing before committing to an ad
/// network.
///
/// The ad SDK is not linked in any build that can be made from this repository,
/// so without this there is no way to answer the only question that actually
/// matters — *is this annoying?* — until after an AdMob account, a package
/// dependency and a signed App Store build. That is far too late to find out the
/// answer is yes.
///
/// It goes through the same `AdPresenter` protocol and is gated by the same
/// `AdPacing` rules as the real thing, so what you feel here is what shipping
/// would feel like. The only difference is what appears on screen.
///
/// Debug builds only. `release.yml` builds Release, so the public IPA and the
/// App Store binary contain none of this.
@Observable
@MainActor
final class FakeAdPresenter: AdPresenter {
    private(set) var isShowing = false

    /// Counters for the debug readout in Settings, so a long quiet stretch reads
    /// as the rules working rather than as something being broken.
    private(set) var shownCount = 0
    private(set) var preloadCount = 0
    private(set) var rewardedCount = 0

    /// False while CI is driving the app to a screen, so a stand-in ad can never
    /// land on top of a published screenshot. The pacing rules would almost
    /// certainly stop it anyway — CI never completes a round — but "almost
    /// certainly" is not what you want guarding your App Store listing.
    var isAvailable: Bool {
        ScreenshotSupport.requestedRoute.isEmpty
    }

    func preload() {
        preloadCount += 1
    }

    /// `completion` fires immediately, exactly as `AdMobPresenter` does — the
    /// real SDK hands control back as soon as it has presented, and dismissal is
    /// the ad's own business. Keeping the contract identical is the point.
    func presentInterstitial(completion: @escaping () -> Void) {
        shownCount += 1
        isShowing = true
        completion()
    }

    func presentRewarded(onReward: @escaping () -> Void, completion: @escaping () -> Void) {
        rewardedCount += 1
        onReward()
        completion()
    }

    func dismiss() {
        isShowing = false
    }
}

/// What the stand-in looks like. Loud on purpose: this must never be mistaken
/// for a real ad, and it should be obvious in a screen recording which frames
/// were the fake.
struct FakeAdView: View {
    @Environment(\.theme) private var theme
    @Environment(AppEnvironment.self) private var environment

    /// How long a real interstitial usually withholds its close button.
    private static let closeDelay: TimeInterval = 3

    @State private var secondsLeft = Int(closeDelay)

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()

            VStack(spacing: 20) {
                Spacer()

                Text("TEST BUILD")
                    .font(.system(.caption, design: .monospaced))
                    .fontWeight(.bold)
                    .foregroundStyle(theme.background)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(theme.warning, in: Capsule())

                Image(systemName: "rectangle.on.rectangle.slash")
                    .font(.system(size: 52))
                    .foregroundStyle(theme.textSecondary)

                VStack(spacing: 8) {
                    Text("An ad would appear here")
                        .font(.system(.title2, design: .rounded))
                        .fontWeight(.bold)
                        .foregroundStyle(theme.textPrimary)

                    Text("Same timing rules as the real one. Ask yourself whether this interrupted anything.")
                        .font(.footnote)
                        .foregroundStyle(theme.textSecondary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 32)
                }

                Spacer()

                // A real interstitial makes you wait before it lets you out.
                // Without that this would feel far cheaper than the real thing
                // and the whole exercise would flatter itself.
                PrimaryButton(
                    title: secondsLeft > 0 ? "Close in \(secondsLeft)" : "Close",
                    isEnabled: secondsLeft <= 0
                ) {
                    environment.fakeAds?.dismiss()
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 32)
            }
        }
        .task {
            secondsLeft = Int(Self.closeDelay)
            while secondsLeft > 0 {
                try? await Task.sleep(for: .seconds(1))
                secondsLeft -= 1
            }
        }
    }
}
#endif
