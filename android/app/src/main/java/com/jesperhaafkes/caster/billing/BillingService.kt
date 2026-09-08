package com.jesperhaafkes.caster.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.jesperhaafkes.caster.domain.EntitlementStore
import com.jesperhaafkes.caster.domain.StoreProduct

/**
 * Buys and restores Caster Plus.
 *
 * Play Billing directly, with no purchasing SDK in front of it: there is one
 * non-consumable product and no server to reconcile against, which is the case
 * RevenueCat and friends are not worth a dependency for. Play already verifies
 * the purchase and holds it against the Google account, so there is nothing
 * left for a receipt service to do here.
 *
 * The service never gates a feature itself. It writes to [EntitlementStore],
 * which caches to disk, and every screen reads that — so an unreachable Play
 * Store means a stale-but-correct answer instead of a locked app.
 *
 * The twin of `StoreService` in `Caster/App/Store/StoreService.swift`. The two
 * stores have genuinely different shapes — StoreKit hands Swift an `async`
 * function and Play hands Kotlin a listener — so this is a port of the
 * behaviour, not of the code.
 */
class BillingService(
    context: Context,
    private val entitlements: EntitlementStore,
) : PurchasesUpdatedListener {

    /**
     * Loaded from Play so the price shown is the one the user's account will
     * actually be charged, in their own currency. Null until it loads, and on a
     * device that cannot reach the store.
     */
    var plusProduct: ProductDetails? by mutableStateOf(null)
        private set

    var isWorking: Boolean by mutableStateOf(false)
        private set

    var lastError: String? by mutableStateOf(null)
        private set

    /**
     * The price string for the buy button. Never a hard-coded price constant —
     * that would be wrong in most of the countries the app is sold in.
     */
    val displayPrice: String?
        get() = plusProduct?.oneTimePurchaseOfferDetails?.formattedPrice

    /** Guards against a second start() from a recomposition. */
    private var hasStarted = false

    /**
     * Pending purchases must be enabled explicitly, and this app sells exactly
     * one one-time product. A pending purchase is a real state on Play — cash
     * payment at a kiosk, or a guardian's approval — and it is not an
     * entitlement until it clears, which [applyPurchases] enforces by only ever
     * accepting PURCHASED.
     */
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /**
     * Connects, then loads the product and reconciles what is already owned.
     *
     * Play's service is killed and restarted routinely — during a Play Store
     * update, for one — so a disconnection is expected rather than exceptional.
     */
    fun start() {
        if (hasStarted) return
        hasStarted = true
        connect()
    }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    // Not surfaced: no Play services is the normal state on a
                    // sideloaded build and on much of the world's hardware, and
                    // it is not something the user did wrong.
                    return
                }
                loadProduct()
                refreshEntitlements()
            }

            override fun onBillingServiceDisconnected() {
                // Reconnected lazily rather than in a retry loop here, which
                // would spin forever on a device with no Play services at all.
                hasStarted = false
            }
        })
    }

    fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(StoreProduct.PLUS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(params) { result, details ->
            plusProduct = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details.firstOrNull { it.productId == StoreProduct.PLUS }
            } else {
                null
            }
        }
    }

    // region Buying

    /**
     * Needs the [Activity] because Play draws its sheet over it. That is the one
     * place this differs from the iOS build, where StoreKit presents itself.
     */
    fun purchasePlus(activity: Activity) {
        val product = plusProduct
        if (product == null || isWorking) return

        isWorking = true
        lastError = null

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build()
                )
            )
            .build()

        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            isWorking = false
            lastError = "The purchase could not be started."
        }
        // The success path ends in onPurchasesUpdated, which clears isWorking.
    }

    /**
     * Play delivers every purchase here — the one just made, one approved hours
     * later by a guardian, and one that completed while the app was closed.
     */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        isWorking = false

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                applyPurchases(purchases.orEmpty())
                lastError = null
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Not an error. Saying nothing is the correct response to
                // somebody deciding not to buy something.
                lastError = null
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Owned on this account but not yet known here, which is what a
                // reinstall looks like. Re-querying is the fix, not an error.
                refreshEntitlements()
            }

            else -> {
                lastError = "The purchase could not be completed."
            }
        }
    }

    /**
     * Required by Play, and genuinely needed: entitlements follow a Google
     * account, and this device may be a new one.
     */
    fun restore(onFinished: (found: Boolean) -> Unit = {}) {
        if (isWorking) return
        isWorking = true
        lastError = null

        queryPurchases { found ->
            isWorking = false
            if (!found) {
                lastError = "No previous purchase was found on this Google account."
            }
            onFinished(found)
        }
    }

    // endregion

    // region Entitlement reconciliation

    /**
     * The source of truth, consulted at launch and after every purchase. Also
     * revokes: a refunded purchase stops coming back from queryPurchasesAsync,
     * and keeping Plus after a refund would be theft in the other direction.
     */
    fun refreshEntitlements() {
        queryPurchases()
    }

    private fun queryPurchases(onFinished: ((found: Boolean) -> Unit)? = null) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // The cache stands. An unreachable store is not evidence that
                // somebody did not pay.
                onFinished?.invoke(entitlements.hasPlus)
            } else {
                onFinished?.invoke(applyPurchases(purchases))
            }
        }
    }

    /**
     * @return whether Plus is owned according to [purchases].
     *
     * Only PURCHASED counts. A PENDING purchase is somebody on their way to a
     * kiosk with cash, and granting on it would hand out the product for a
     * payment that may never arrive.
     */
    private fun applyPurchases(purchases: List<Purchase>): Boolean {
        val plus = purchases.firstOrNull { purchase ->
            purchase.products.contains(StoreProduct.PLUS) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        entitlements.setPlus(plus != null)

        // Play refunds anything left unacknowledged for three days, so this is
        // not bookkeeping — skipping it takes the purchase back off somebody who
        // paid for it.
        if (plus != null && !plus.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(plus.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { /* Retried on the next launch. */ }
        }

        return plus != null
    }

    // endregion
}
