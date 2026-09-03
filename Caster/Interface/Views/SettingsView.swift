import SwiftUI

/// The app's first settings screen.
///
/// It exists for three reasons, and only one of them is money. It gives the
/// sound toggle a home — `AppEnvironment.isMuted` has been wired to the sound
/// engine since the beginning with nothing in the UI ever setting it. It gives
/// the palette a manual override. And it is where App Review requires "Restore
/// Purchases" to be findable.
///
/// Plus lives here as one row among several, which is the point: a settings
/// screen that earns its place is not a paywall wearing a disguise.
struct SettingsView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(EntitlementStore.self) private var entitlements
    @Environment(StoreService.self) private var store
    @Environment(ThemeStore.self) private var themeStore
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.theme) private var theme

    @State private var plusPrompt: PlusPrompt?

    private static let privacyPolicy = URL(string: "https://djxsper.github.io/caster/privacy")!
    private static let terms = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            List {
                Section("Sound") {
                    Toggle(isOn: Binding(
                        get: { !environment.isMuted },
                        set: { environment.isMuted = !$0 }
                    )) {
                        Text("Cue tones")
                            .foregroundStyle(theme.textPrimary)
                    }
                    .tint(theme.accent)

                    Text("Haptics follow your phone's system setting.")
                        .font(.caption)
                        .foregroundStyle(theme.textSecondary)
                }
                .listRowBackground(theme.surfaceRaised)

                Section("Appearance") {
                    ForEach(ThemeSelection.allCases) { selection in
                        themeRow(selection)
                    }
                }
                .listRowBackground(theme.surfaceRaised)

                Section("Caster Plus") {
                    plusRow

                    if !entitlements.hasPlus {
                        Button("Restore purchase") {
                            Task { await store.restore() }
                        }
                        .foregroundStyle(theme.accent)
                        .disabled(store.isWorking)
                    }

                    if let error = store.lastError {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(theme.danger)
                    }
                }
                .listRowBackground(theme.surfaceRaised)

                Section("About") {
                    Link("Privacy policy", destination: Self.privacyPolicy)
                        .foregroundStyle(theme.accent)
                    Link("Terms of use", destination: Self.terms)
                        .foregroundStyle(theme.accent)
                    LabeledContent("Version", value: Self.versionString)
                        .foregroundStyle(theme.textSecondary)
                }
                .listRowBackground(theme.surfaceRaised)

                debugSection
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $plusPrompt) { PlusView(prompt: $0) }
    }

    // MARK: - Rows

    /// A locked palette is shown, not hidden. Somebody should be able to see
    /// what they would be getting before deciding whether they want it.
    private func themeRow(_ selection: ThemeSelection) -> some View {
        let isLocked = selection.isPlus && !entitlements.hasThemes
        let isActive = themeStore.effective(hasPlus: entitlements.hasPlus) == selection
        let swatch = selection.swatch(for: colorScheme)

        return Button {
            environment.hapticEngine.playFeedback(type: .light)
            if isLocked {
                plusPrompt = .theme
            } else {
                themeStore.select(selection)
            }
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(swatch.background)
                        .frame(width: 26, height: 26)
                    Circle()
                        .fill(swatch.accent)
                        .frame(width: 12, height: 12)
                }
                .overlay(Circle().stroke(theme.border, lineWidth: 1))

                VStack(alignment: .leading, spacing: 2) {
                    Text(selection.title)
                        .foregroundStyle(theme.textPrimary)
                    Text(selection.subtitle)
                        .font(.caption)
                        .foregroundStyle(theme.textSecondary)
                }

                Spacer(minLength: 8)

                if isLocked {
                    Image(systemName: "lock.fill")
                        .font(.caption)
                        .foregroundStyle(theme.textSecondary)
                } else if isActive {
                    Image(systemName: "checkmark")
                        .font(.callout.bold())
                        .foregroundStyle(theme.accent)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isActive ? [.isButton, .isSelected] : [.isButton])
        .accessibilityHint(isLocked ? "Requires Caster Plus" : "")
    }

    @ViewBuilder
    private var plusRow: some View {
        if entitlements.hasPlus {
            HStack {
                Label("Unlocked", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(theme.success)
                Spacer()
                Text("Thank you")
                    .font(.caption)
                    .foregroundStyle(theme.textSecondary)
            }
        } else {
            Button {
                plusPrompt = .browsing
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("See what Plus adds")
                            .foregroundStyle(theme.textPrimary)
                        Text(entitlements.isLegacy
                             ? "Your saved wheels and groups are already unlimited."
                             : "One payment. Not a subscription.")
                            .font(.caption)
                            .foregroundStyle(theme.textSecondary)
                    }
                    Spacer()
                    if let price = store.displayPrice {
                        Text(price)
                            .font(.callout.bold())
                            .foregroundStyle(theme.accent)
                    }
                }
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Testing

    /// Everything needed to test the paid tier without an App Store, plus the
    /// numbers behind the ad pacing so a long quiet stretch reads as the rules
    /// working rather than as something being broken.
    ///
    /// Debug builds only. `release.yml` builds Release, so none of this exists
    /// in the public IPA or the App Store binary.
    @ViewBuilder
    private var debugSection: some View {
        #if DEBUG
        Section("Test build") {
            Toggle(isOn: Binding(
                get: { entitlements.hasPlus },
                set: { entitlements.setPlus($0) }
            )) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Pretend Plus is bought")
                        .foregroundStyle(theme.textPrimary)
                    Text("No purchase, no money. Flip it both ways.")
                        .font(.caption)
                        .foregroundStyle(theme.textSecondary)
                }
            }
            .tint(theme.warning)

            LabeledContent("Ad status", value: adStatus)
                .font(.caption)
                .foregroundStyle(theme.textSecondary)

            LabeledContent("Counters", value: pacingCounters)
                .font(.caption)
                .foregroundStyle(theme.textSecondary)

            Button("Reset ad counters") {
                environment.pacing.resetForTesting()
            }
            .foregroundStyle(theme.accent)
        }
        .listRowBackground(theme.surfaceRaised)
        #endif
    }

    #if DEBUG
    /// Why an ad is or is not coming, in the order `AdPacing` checks it.
    private var adStatus: String {
        guard !entitlements.hasPlus else { return "off — Plus" }

        let state = environment.pacing.state
        if state.launchCount < AdPacing.minimumLaunches {
            return "after \(AdPacing.minimumLaunches - state.launchCount) more launch(es)"
        }
        if state.roundsCompleted < AdPacing.minimumRoundsCompleted {
            return "after \(AdPacing.minimumRoundsCompleted - state.roundsCompleted) more round(s)"
        }
        if state.interstitialsThisSession >= AdPacing.perSessionCap {
            return "session cap reached"
        }
        if let last = state.lastInterstitialAt {
            let wait = AdPacing.quietPeriod - Date().timeIntervalSince(last)
            if wait > 0 { return "in \(Int(wait / 60))m \(Int(wait) % 60)s" }
        }
        return "eligible on the next game you finish"
    }

    private var pacingCounters: String {
        let state = environment.pacing.state
        let shown = environment.fakeAds?.shownCount ?? 0
        return "launch \(state.launchCount) · \(state.roundsCompleted) rounds · \(shown) shown"
    }
    #endif

    private static var versionString: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String ?? "1"
        return "\(short) (\(build))"
    }
}
