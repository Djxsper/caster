package com.jesperhaafkes.caster.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jesperhaafkes.caster.LocalBilling
import com.jesperhaafkes.caster.LocalEntitlements
import com.jesperhaafkes.caster.domain.PlusBenefits
import com.jesperhaafkes.caster.domain.PlusPrompt
import com.jesperhaafkes.caster.ui.components.BarTextAction
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.components.tappable
import com.jesperhaafkes.caster.ui.theme.CasterType
import com.jesperhaafkes.caster.ui.theme.LocalTheme

/**
 * The only screen in the app that asks for money.
 *
 * It is reached two ways: from Settings, or from the exact moment a cap was hit
 * — and in the second case it names what was refused rather than showing the
 * same anonymous wall everywhere. What it deliberately does not have: a
 * countdown, a struck-through price, a "limited time", a second tier, or any
 * path to it that the user did not take on purpose. It is never shown on
 * launch, and dismissing it is never punished.
 *
 * The list of what stays free is not decoration. It is the honest summary of
 * the deal, and it is first on the screen because it is the part most people
 * need to read.
 *
 * The twin of `PlusView` in `Caster/Interface/Views/PlusView.swift`.
 */
@Composable
fun PlusDialog(prompt: PlusPrompt, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PlusScreen(prompt = prompt, onDismiss = onDismiss)
    }
}

@Composable
private fun PlusScreen(prompt: PlusPrompt, onDismiss: () -> Unit) {
    val theme = LocalTheme.current
    val entitlements = LocalEntitlements.current
    val billing = LocalBilling.current
    val activity = LocalActivity.current

    CasterScreen(
        title = "Caster Plus",
        onBack = null,
        actions = { BarTextAction(label = "Not now", onClick = onDismiss) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                // Header: what was just refused, in one line.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "✦",
                        style = CasterType.display.copy(color = theme.accent),
                    )
                    Text(
                        text = prompt.reason,
                        style = CasterType.subtitle.copy(
                            color = theme.textPrimary,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }

                FreeForever()
                PlusAdds()

                val error = billing.lastError
                if (error != null) {
                    Text(
                        text = error,
                        modifier = Modifier.fillMaxWidth(),
                        style = CasterType.caption.copy(
                            color = theme.danger,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }

                Spacer(Modifier.height(4.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (entitlements.hasPlus) {
                    Text(
                        text = "You have Plus",
                        style = CasterType.rowTitle.copy(color = theme.success),
                    )
                    Text(
                        text = "Thank you — that genuinely paid for something.",
                        style = CasterType.caption.copy(
                            color = theme.textSecondary,
                            textAlign = TextAlign.Center,
                        ),
                    )
                } else {
                    PrimaryButton(
                        title = buyTitle(billing.isWorking, billing.displayPrice),
                        isEnabled = billing.plusProduct != null && !billing.isWorking,
                    ) {
                        activity?.let { billing.purchasePlus(it) }
                    }

                    Text(
                        text = "Restore purchase",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .tappable(enabled = !billing.isWorking) { billing.restore() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = CasterType.caption.copy(color = theme.textSecondary),
                    )

                    Text(
                        text = "One payment. Not a subscription.",
                        style = CasterType.caption.copy(color = theme.textSecondary),
                    )
                }
            }
        }
    }
}

/**
 * Deliberately above the paid list. Somebody deciding not to buy should leave
 * knowing the app is still theirs.
 */
@Composable
private fun FreeForever() {
    val theme = LocalTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.success.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Free, always", style = CasterType.sectionHeader.copy(color = theme.success))
        Text(
            text = "All six games, every mode, as many players and wheel entries as " +
                "you like. No ads inside a round, ever. Nothing you have already " +
                "saved is taken away.",
            style = CasterType.rowDetail.copy(color = theme.textSecondary),
        )
    }
}

@Composable
private fun PlusAdds() {
    val theme = LocalTheme.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Plus adds", style = CasterType.sectionHeader.copy(color = theme.textPrimary))

        // Read from PlusBenefits rather than written out here, so
        // OfferingParityTest can hold every line on this screen against the
        // shared contract. A benefit named here that the app cannot yet do is a
        // false claim on a screen that takes money.
        PlusBenefits.advertised.forEach { Benefit(it.glyph, it.text) }
    }
}

@Composable
private fun Benefit(glyph: String, text: String) {
    val theme = LocalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Text(text = glyph, style = CasterType.rowTitle.copy(color = theme.accent))
        }
        Text(text = text, style = CasterType.rowTitle.copy(color = theme.textPrimary))
    }
}

/**
 * The price comes from the storefront, never from a constant: the same product
 * is a different number in each country it sells in.
 */
private fun buyTitle(isWorking: Boolean, price: String?): String = when {
    isWorking -> "Working…"
    price == null -> "Unavailable offline"
    else -> "Unlock Plus — $price"
}
