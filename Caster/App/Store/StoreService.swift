import Foundation
import Observation
import StoreKit

/// Buys and restores Caster Plus.
///
/// StoreKit 2 directly, with no purchasing SDK in front of it: there is one
/// non-consumable product and no server to reconcile against, which is the case
/// RevenueCat and friends are not worth a dependency for. `Transaction` is
/// already cryptographically verified by the OS, so there is nothing left for a
/// receipt service to do here.
///
/// The service never gates a feature itself. It writes to `EntitlementStore`,
/// which caches to disk, and every screen reads that — so an unreachable App
/// Store means a stale-but-correct answer instead of a locked app.
@Observable
@MainActor
final class StoreService {
    /// Loaded from the App Store so the price shown is the one the user's
    /// storefront will actually charge, in their own currency. Nil until it
    /// loads, and on a device that cannot reach the store.
    private(set) var plusProduct: Product?
    private(set) var isWorking = false
    private(set) var lastError: String?

    /// The price string for the buy button, or a resting label while the
    /// storefront is still answering. Never a hard-coded "€3.99" — that would
    /// be wrong in most of the 175 countries the app is sold in.
    var displayPrice: String? { plusProduct?.displayPrice }

    private let entitlements: EntitlementStore

    /// Deliberately never cancelled. There is one `StoreService` and it lives as
    /// long as the app; a purchase can be approved by a parent hours later, or
    /// interrupted and finished by the system on the next launch, and the
    /// listener has to still be there when it is.
    private var updatesTask: Task<Void, Never>?

    init(entitlements: EntitlementStore) {
        self.entitlements = entitlements
    }

    /// Starts the transaction listener before anything else, then loads the
    /// product. The listener has to outlive any one screen: a purchase can be
    /// approved by a parent hours later, or interrupted and finished by the
    /// system on next launch, and the app has to hear about it either way.
    func start() {
        guard updatesTask == nil else { return }

        updatesTask = Task { [weak self] in
            for await update in Transaction.updates {
                guard let self else { return }
                await self.apply(update)
            }
        }

        Task { await loadProduct() }
        Task { await refreshEntitlements() }
    }

    func loadProduct() async {
        do {
            let products = try await Product.products(for: [StoreProduct.plus])
            plusProduct = products.first
            lastError = nil
        } catch {
            // Not surfaced as a failure: no network is the normal state for this
            // app, and a missing price is not something the user did wrong.
            plusProduct = nil
        }
    }

    // MARK: - Buying

    func purchasePlus() async {
        guard let product = plusProduct, !isWorking else { return }

        isWorking = true
        lastError = nil
        defer { isWorking = false }

        do {
            switch try await product.purchase() {
            case .success(let verification):
                await apply(verification)
            case .userCancelled:
                // Not an error. Saying nothing is the correct response to
                // somebody deciding not to buy something.
                break
            case .pending:
                lastError = "That purchase needs approval before it can finish."
            @unknown default:
                break
            }
        } catch {
            lastError = "The purchase could not be completed."
        }
    }

    /// Required by App Review, and genuinely needed: entitlements are per Apple
    /// ID, and this device may be a new one.
    func restore() async {
        guard !isWorking else { return }

        isWorking = true
        lastError = nil
        defer { isWorking = false }

        do {
            try await AppStore.sync()
            await refreshEntitlements()
            if !entitlements.hasPlus {
                lastError = "No previous purchase was found on this Apple ID."
            }
        } catch {
            lastError = "Could not reach the App Store. Try again when you have a signal."
        }
    }

    // MARK: - Entitlement reconciliation

    /// The source of truth, consulted at launch and after every transaction.
    /// Also revokes: a refunded purchase drops out of `currentEntitlements`, and
    /// keeping Plus after a refund would be theft in the other direction.
    func refreshEntitlements() async {
        var owned = false
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }
            if transaction.productID == StoreProduct.plus, transaction.revocationDate == nil {
                owned = true
            }
        }
        entitlements.setPlus(owned)
    }

    private func apply(_ result: VerificationResult<Transaction>) async {
        guard case .verified(let transaction) = result else {
            // An unverified transaction is either a StoreKit bug or a tampered
            // device. Either way it is not a purchase, and it is not finished —
            // leaving it in the queue is the honest thing to do.
            return
        }

        if transaction.productID == StoreProduct.plus {
            entitlements.setPlus(transaction.revocationDate == nil)
        }

        // Always finish, or StoreKit replays it on every launch forever.
        await transaction.finish()
    }
}
