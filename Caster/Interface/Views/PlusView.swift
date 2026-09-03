import SwiftUI

/// The only screen in the app that asks for money.
///
/// It is reached two ways: from Settings, or from the exact moment a cap was
/// hit — and in the second case it names what was refused rather than showing
/// the same anonymous wall everywhere. What it deliberately does not have: a
/// countdown, a struck-through price, a "limited time", a second tier, or any
/// path to it that the user did not take on purpose. It is never shown on
/// launch, and dismissing it is never punished.
///
/// The list of what stays free is not decoration. It is the honest summary of
/// the deal, and it is first on the screen because it is the part most people
/// need to read.
struct PlusView: View {
    @Environment(\.theme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(EntitlementStore.self) private var entitlements
    @Environment(StoreService.self) private var store

    let prompt: PlusPrompt

    var body: some View {
        NavigationStack {
            ZStack {
                theme.background
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 24) {
                        header
                        freeForever
                        plusAdds
                        if let error = store.lastError {
                            Text(error)
                                .font(.footnote)
                                .foregroundStyle(theme.danger)
                                .multilineTextAlignment(.center)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 24)
                }

                VStack {
                    Spacer()
                    purchaseControls
                        .padding(.horizontal, 20)
                        .padding(.bottom, 16)
                        .background(theme.background.opacity(0.95))
                }
            }
            .navigationTitle("Caster Plus")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Not now") { dismiss() }
                        .foregroundStyle(theme.textSecondary)
                }
            }
        }
    }

    // MARK: - Pieces

    private var header: some View {
        VStack(spacing: 10) {
            Image(systemName: "circle.hexagongrid.fill")
                .font(.system(size: 44))
                .foregroundStyle(theme.accent)
                .accessibilityHidden(true)

            Text(prompt.reason)
                .font(.system(.title3, design: .rounded))
                .fontWeight(.semibold)
                .foregroundStyle(theme.textPrimary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, 12)
    }

    /// Deliberately above the paid list. Somebody deciding not to buy should
    /// leave knowing the app is still theirs.
    private var freeForever: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Free, always", systemImage: "checkmark.seal.fill")
                .font(.system(.subheadline, design: .rounded))
                .fontWeight(.bold)
                .foregroundStyle(theme.success)

            Text("All six games, every mode, as many players and wheel entries as you like. No ads inside a round, ever. Nothing you have already saved is taken away.")
                .font(.footnote)
                .foregroundStyle(theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(theme.success.opacity(0.10))
        )
    }

    private var plusAdds: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Plus adds")
                .font(.system(.subheadline, design: .rounded))
                .fontWeight(.bold)
                .foregroundStyle(theme.textPrimary)

            // Only what actually ships. A benefit listed here that the app
            // cannot yet do is a false claim on a purchase screen, which is
            // both dishonest and an App Review rejection. Sound packs and the
            // persistent scoreboard go on this list on the day they work.
            benefit("infinity", "Unlimited saved wheels and groups")
            benefit("person.crop.circle.badge.checkmark", "Sit people out without deleting them")
            benefit("paintpalette.fill", "Four more palettes")
            benefit("hand.raised.fill", "No interstitials")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func benefit(_ symbol: String, _ text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.callout)
                .frame(width: 24)
                .foregroundStyle(theme.accent)
            Text(text)
                .font(.callout)
                .foregroundStyle(theme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var purchaseControls: some View {
        if entitlements.hasPlus {
            VStack(spacing: 8) {
                Label("You have Plus", systemImage: "checkmark.circle.fill")
                    .font(.system(.headline, design: .rounded))
                    .foregroundStyle(theme.success)
                Text("Thank you — that genuinely paid for something.")
                    .font(.footnote)
                    .foregroundStyle(theme.textSecondary)
            }
            .padding(.vertical, 12)
        } else {
            VStack(spacing: 10) {
                PrimaryButton(
                    title: buyTitle,
                    isEnabled: store.plusProduct != nil && !store.isWorking
                ) {
                    Task { await store.purchasePlus() }
                }

                Button("Restore purchase") {
                    Task { await store.restore() }
                }
                .font(.footnote)
                .foregroundStyle(theme.textSecondary)
                .disabled(store.isWorking)

                Text("One payment. Not a subscription.")
                    .font(.caption2)
                    .foregroundStyle(theme.textSecondary)
            }
        }
    }

    /// The price comes from the storefront, never from a constant: the same
    /// product is a different number in each of the 175 countries it sells in.
    private var buyTitle: String {
        if store.isWorking { return "Working…" }
        guard let price = store.displayPrice else { return "Unavailable offline" }
        return "Unlock Plus — \(price)"
    }
}

#Preview {
    PlusView(prompt: .wheelLimit)
        .environment(EntitlementStore())
        .environment(StoreService(entitlements: EntitlementStore()))
}
