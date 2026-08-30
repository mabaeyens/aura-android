package com.mab.aura.ui.tipjar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mab.aura.R
import com.mab.aura.ui.theme.Palette

/**
 * "Propina" — the tip jar, ported from the iOS `TipJarView`. Three voluntary consumable tips over the same
 * time-of-day sky gradient the Hoy screen uses, with white text and translucent cards, so the surface reads
 * as the iOS one. The billing lives in [TipJarViewModel]; this file is only the layout and the three visible
 * states (loading, load error, the price rows) plus the success / pending / failure confirmations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipJarScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TipJarViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // launchBillingFlow needs the hosting Activity (Play draws its buy sheet over it), which the app context
    // held by the ViewModel isn't. Unwrap it from the Compose context once. Android-note: this ContextWrapper
    // walk is the standard way to reach the Activity from a Composable without threading it through by hand.
    val activity = remember(context) { context.findActivity() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.timeGradient()),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.tipjar_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Header()
                    Spacer(Modifier.height(24.dp))

                    when (val load = state.load) {
                        LoadState.Loading -> LoadingCard()
                        LoadState.Failed -> LoadErrorCard(onRetry = viewModel::retry)
                        is LoadState.Loaded -> TipButtons(
                            products = load.products,
                            purchase = state.purchase,
                            enabled = activity != null,
                            onTip = { id -> activity?.let { viewModel.purchase(it, id) } },
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Footnote()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // Confirmations, layered over the whole screen like the iOS ZStack overlays.
        when (val p = state.purchase) {
            PurchaseState.Success -> ConfirmationOverlay(
                icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp)) },
                title = stringResource(R.string.tipjar_thanks_title),
                body = stringResource(R.string.tipjar_thanks_body),
                onDismiss = viewModel::dismissPurchaseResult,
            )
            PurchaseState.Pending -> ConfirmationOverlay(
                icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp)) },
                title = stringResource(R.string.tipjar_pending_title),
                body = stringResource(R.string.tipjar_pending_body),
                onDismiss = viewModel::dismissPurchaseResult,
            )
            is PurchaseState.Failed -> AlertDialog(
                onDismissRequest = viewModel::dismissPurchaseResult,
                confirmButton = {
                    TextButton(onClick = viewModel::dismissPurchaseResult) {
                        Text(stringResource(R.string.tipjar_ok))
                    }
                },
                title = { Text(stringResource(R.string.tipjar_alert_title)) },
                text = { Text(p.message) },
            )
            else -> Unit
        }
    }
}

/** The bottle icon, heading and blurb, all in white over the gradient. */
@Composable
private fun Header() {
    Icon(
        painter = painterResource(R.drawable.ic_tip_bottle),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(48.dp),
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.tipjar_heading),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.tipjar_body),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
    )
}

/** The three price rows, small → large. All disabled while any purchase is in flight, matching iOS. */
@Composable
private fun TipButtons(
    products: List<TipProduct>,
    purchase: PurchaseState,
    enabled: Boolean,
    onTip: (String) -> Unit,
) {
    val anyInFlight = purchase is PurchaseState.Purchasing
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        for (product in products) {
            val isThisPurchasing = purchase is PurchaseState.Purchasing && purchase.productId == product.id
            TipRow(
                product = product,
                purchasing = isThisPurchasing,
                enabled = enabled && !anyInFlight,
                onClick = { onTip(product.id) },
            )
        }
    }
}

@Composable
private fun TipRow(product: TipProduct, purchasing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = product.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        if (purchasing) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(
                text = product.price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.20f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    FrostedCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.tipjar_loading), color = Color.White)
        }
    }
}

@Composable
private fun LoadErrorCard(onRetry: () -> Unit) {
    FrostedCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
                Text(
                    stringResource(R.string.tipjar_load_error),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Button(onClick = onRetry, colors = translucentButtonColors()) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun Footnote() {
    Text(
        text = stringResource(R.string.tipjar_footnote),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.70f),
        textAlign = TextAlign.Center,
    )
}

/** A centred confirmation card over a dimmed backdrop; tapping the backdrop or the button dismisses it. */
@Composable
private fun ConfirmationOverlay(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.40f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.16f))
                // Consume taps on the card itself so they don't fall through to the dismiss backdrop.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onDismiss, colors = translucentButtonColors()) {
                Text(stringResource(R.string.tipjar_close))
            }
        }
    }
}

/** A translucent-white pill card that reads over the sky gradient, the approximation of iOS's ultraThinMaterial. */
@Composable
private fun FrostedCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun translucentButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color.White.copy(alpha = 0.22f),
    contentColor = Color.White,
)

/** Walk the ContextWrapper chain to the hosting Activity, or null if there isn't one (e.g. a preview). */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
