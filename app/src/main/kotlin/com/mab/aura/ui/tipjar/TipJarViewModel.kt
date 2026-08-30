package com.mab.aura.ui.tipjar

import android.app.Activity
import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.mab.aura.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One tip the screen can show: the display name and formatted price come straight from Play, never hardcoded. */
data class TipProduct(val id: String, val title: String, val price: String)

/** Where product loading is, mirroring the iOS `TipJar.LoadState`. */
sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val products: List<TipProduct>) : LoadState
    data object Failed : LoadState
}

/** Where a purchase is, mirroring the iOS `TipJar.PurchaseState`. */
sealed interface PurchaseState {
    data object Ready : PurchaseState
    data class Purchasing(val productId: String) : PurchaseState
    data object Success : PurchaseState
    data object Pending : PurchaseState
    data class Failed(val message: String) : PurchaseState
}

/** The whole screen state in one immutable value, so Compose collects a single flow. */
data class TipJarUiState(
    val load: LoadState = LoadState.Loading,
    val purchase: PurchaseState = PurchaseState.Ready,
)

/**
 * The tip jar's store, ported from the iOS `TipJar` (StoreKit 2) to Google Play Billing. Three consumable
 * products, no restore flow (consumables unlock nothing), prices and names read from Play at runtime.
 *
 * Android notes for someone coming from StoreKit:
 * - StoreKit's `Product.products(for:)` becomes [queryProductDetails]; `product.purchase()` becomes
 *   [BillingClient.launchBillingFlow]; `transaction.finish()` becomes [consumePurchase] (consuming both
 *   acknowledges the purchase and frees the product to be bought again, which is what a repeatable tip needs).
 * - Play delivers *every* purchase result, including ones that land out-of-band, through one
 *   [PurchasesUpdatedListener], the rough equivalent of StoreKit's `Transaction.updates`.
 * - This is an `AndroidViewModel` because the [BillingClient] needs a `Context`; it holds the app context, and
 *   the one call that needs an `Activity` ([purchase]) takes it as a parameter from the screen.
 * - Billing only returns real products to a signed build on a Play testing track with licence testers. On a
 *   plain debug build the connection or the query comes back empty, and the screen shows its retryable load
 *   error rather than crashing (see [LoadState.Failed]).
 */
class TipJarViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(TipJarUiState())
    val state: StateFlow<TipJarUiState> = _state.asStateFlow()

    // Kept so [purchase] can look up the ProductDetails to launch the Play sheet without exposing the billing
    // type to the UI layer.
    private var productDetailsById: Map<String, ProductDetails> = emptyMap()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases?.forEach(::handlePurchase)
            // The user backed out of the Play sheet: quietly return to Ready, no error, matching iOS.
            BillingResponseCode.USER_CANCELED -> setPurchase(PurchaseState.Ready)
            else -> setPurchase(PurchaseState.Failed(string(R.string.tipjar_error_failed)))
        }
    }

    private val billing: BillingClient = BillingClient.newBuilder(app)
        .setListener(purchasesListener)
        // Required since Billing 6.2: declare that the app can handle pending (deferred) one-time purchases.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    init {
        connect()
    }

    /** Open (or reuse) the billing connection, then load the products. Also the retry entry point. */
    fun retry() = connect()

    private fun connect() {
        if (billing.connectionState == BillingClient.ConnectionState.CONNECTED) {
            viewModelScope.launch { loadProducts() }
            return
        }
        _state.update { it.copy(load = LoadState.Loading) }
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    viewModelScope.launch {
                        loadProducts()
                        // Consume anything left PURCHASED from a prior run (an interrupted consume, or a
                        // pending purchase that has since cleared) so a stuck token never blocks a fresh tip.
                        reconcile()
                    }
                } else {
                    _state.update { it.copy(load = LoadState.Failed) }
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update { it.copy(load = LoadState.Failed) }
            }
        })
    }

    private suspend fun loadProducts() {
        _state.update { it.copy(load = LoadState.Loading) }
        val queryProducts = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(queryProducts).build()
        val result = billing.queryProductDetails(params)
        val list = result.productDetailsList
        if (result.billingResult.responseCode != BillingResponseCode.OK || list.isNullOrEmpty()) {
            _state.update { it.copy(load = LoadState.Failed) }
            return
        }
        // Sort by real price ascending so the rows always read small → large, matching the iOS `$0.price < $1.price`.
        val sorted = list.sortedBy { it.oneTimePurchaseOfferDetails?.priceAmountMicros ?: Long.MAX_VALUE }
        productDetailsById = sorted.associateBy { it.productId }
        val tips = sorted.mapNotNull { pd ->
            val offer = pd.oneTimePurchaseOfferDetails ?: return@mapNotNull null
            TipProduct(id = pd.productId, title = pd.name, price = offer.formattedPrice)
        }
        _state.update { it.copy(load = LoadState.Loaded(tips)) }
    }

    /** Start a purchase. Needs the current [activity] because Play draws its buy sheet over it. */
    fun purchase(activity: Activity, productId: String) {
        val details = productDetailsById[productId] ?: run {
            setPurchase(PurchaseState.Failed(string(R.string.tipjar_error_failed)))
            return
        }
        setPurchase(PurchaseState.Purchasing(productId))
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val launch = billing.launchBillingFlow(activity, flowParams)
        // A non-OK code here means the sheet never opened (e.g. billing unavailable). A cancel isn't reported
        // here, it arrives later through the listener, so any non-OK response is a genuine failure.
        if (launch.responseCode != BillingResponseCode.OK) {
            setPurchase(PurchaseState.Failed(string(R.string.tipjar_error_failed)))
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> viewModelScope.launch {
                consume(purchase)
                setPurchase(PurchaseState.Success)
            }
            Purchase.PurchaseState.PENDING -> setPurchase(PurchaseState.Pending)
            else -> Unit
        }
    }

    private suspend fun reconcile() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billing.queryPurchasesAsync(params)
        result.purchasesList
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .forEach { consume(it) }
    }

    private suspend fun consume(purchase: Purchase) {
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billing.consumePurchase(params)
    }

    /** Dismiss a success/pending card or an error, returning to the ready state (iOS `resetPurchaseState`). */
    fun dismissPurchaseResult() = setPurchase(PurchaseState.Ready)

    private fun setPurchase(p: PurchaseState) = _state.update { it.copy(purchase = p) }

    private fun string(@StringRes id: Int) = getApplication<Application>().getString(id)

    override fun onCleared() {
        billing.endConnection()
        super.onCleared()
    }

    companion object {
        // Play in-app product IDs. Play requires lowercase (letters, digits, _, .), so these can't reuse the
        // iOS StoreKit ids (com.mab.Aura.tip.small, capital A). Registered in Play Console; see
        // notes/play-listing.md. This order is only a fallback; the UI sorts by real price ascending.
        private val PRODUCT_IDS = listOf("tip_small", "tip_medium", "tip_large")
    }
}
