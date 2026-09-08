package com.jesperhaafkes.caster.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.jesperhaafkes.caster.BuildConfig
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.LocalBilling
import com.jesperhaafkes.caster.LocalEntitlements
import com.jesperhaafkes.caster.LocalThemeStore
import com.jesperhaafkes.caster.domain.AdPacing
import com.jesperhaafkes.caster.domain.PlusPrompt
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.tappable
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.theme.CasterType
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import com.jesperhaafkes.caster.ui.theme.ThemeSelection
import com.jesperhaafkes.caster.ui.theme.themeForScheme

private const val PRIVACY_POLICY = "https://djxsper.github.io/caster/privacy"
private const val TERMS = "https://djxsper.github.io/caster/terms"

/**
 * The app's first settings screen.
 *
 * It exists for three reasons, and only one of them is money. It gives the
 * sound toggle a home — `AppEnvironment.isMuted` has been wired to the sound
 * engine since the beginning with nothing in the UI ever setting it. It gives
 * the palette a manual override. And it is where Play requires a restore path
 * and a privacy policy to be findable.
 *
 * Plus lives here as one row among several, which is the point: a settings
 * screen that earns its place is not a paywall wearing a disguise.
 *
 * The twin of `SettingsView` in `Caster/Interface/Views/SettingsView.swift`.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val entitlements = LocalEntitlements.current
    val billing = LocalBilling.current
    val themeStore = LocalThemeStore.current
    val theme = LocalTheme.current
    val systemPalette = themeForScheme()
    val uriHandler = LocalUriHandler.current

    var plusPrompt by remember { mutableStateOf<PlusPrompt?>(null) }

    CasterScreen(title = "Settings", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingsSection("Sound") {
                    SettingsRow(
                        title = "Cue tones",
                        detail = "Haptics follow your phone's system setting.",
                        trailing = {
                            Switch(
                                checked = !environment.isMuted,
                                onCheckedChange = { environment.isMuted = !it },
                                colors = SwitchDefaults.colors(checkedTrackColor = theme.accent),
                            )
                        },
                    )
                }
            }

            item {
                SettingsSection("Appearance") {
                    ThemeSelection.entries.forEachIndexed { index, selection ->
                        if (index > 0) RowDivider()
                        ThemeRow(
                            selection = selection,
                            systemPalette = systemPalette,
                            isLocked = selection.isPlus && !entitlements.hasThemes,
                            isActive = themeStore.effective(entitlements.hasPlus) == selection,
                        ) {
                            environment.hapticEngine.playFeedback(FeedbackType.LIGHT)
                            if (selection.isPlus && !entitlements.hasThemes) {
                                plusPrompt = PlusPrompt.THEME
                            } else {
                                themeStore.select(selection)
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection("Caster Plus") {
                    if (entitlements.hasPlus) {
                        SettingsRow(title = "Unlocked", detail = "Thank you", titleColor = theme.success)
                    } else {
                        SettingsRow(
                            title = "See what Plus adds",
                            detail = if (entitlements.isLegacy) {
                                "Your saved wheels and groups are already unlimited."
                            } else {
                                "One payment. Not a subscription."
                            },
                            trailing = {
                                billing.displayPrice?.let {
                                    Text(text = it, style = CasterType.rowTitle.copy(color = theme.accent))
                                }
                            },
                            onClick = { plusPrompt = PlusPrompt.BROWSING },
                        )
                        RowDivider()
                        SettingsRow(
                            title = "Restore purchase",
                            titleColor = theme.accent,
                            // Null while a query is already in flight, which is
                            // what makes the row untappable rather than queuing
                            // a second one behind the first.
                            onClick = if (billing.isWorking) null else { { billing.restore() } },
                        )
                    }

                    billing.lastError?.let {
                        RowDivider()
                        SettingsRow(title = it, titleColor = theme.danger)
                    }
                }
            }

            item {
                SettingsSection("About") {
                    SettingsRow(
                        title = "Privacy policy",
                        titleColor = theme.accent,
                        onClick = { uriHandler.openUri(PRIVACY_POLICY) },
                    )
                    RowDivider()
                    SettingsRow(
                        title = "Terms of use",
                        titleColor = theme.accent,
                        onClick = { uriHandler.openUri(TERMS) },
                    )
                    RowDivider()
                    SettingsRow(
                        title = "Version",
                        trailing = {
                            Text(
                                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = CasterType.rowDetail.copy(color = theme.textSecondary),
                            )
                        },
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                item { DebugSection() }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    plusPrompt?.let { prompt ->
        PlusDialog(prompt = prompt, onDismiss = { plusPrompt = null })
    }
}

// region Test build

/**
 * Everything needed to test the paid tier without a Play Console, plus the
 * numbers behind the ad pacing so a long quiet stretch reads as the rules
 * working rather than as something being broken.
 *
 * Debug builds only. Kotlin has no `#if DEBUG`, so this is gated on
 * `BuildConfig.DEBUG` at the one call site above and R8 drops it from release.
 */
@Composable
private fun DebugSection() {
    val theme = LocalTheme.current
    val environment = LocalAppEnvironment.current
    val entitlements = LocalEntitlements.current

    SettingsSection("Test build", accent = theme.warning) {
        SettingsRow(
            title = "Pretend Plus is bought",
            detail = "No purchase, no money. Flip it both ways.",
            trailing = {
                Switch(
                    checked = entitlements.hasPlus,
                    onCheckedChange = { entitlements.setPlus(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = theme.warning),
                )
            },
        )
        RowDivider()
        SettingsRow(
            title = "Ad status",
            trailing = {
                Text(
                    text = adStatus(
                        hasPlus = entitlements.hasPlus,
                        launchCount = environment.pacing.state.launchCount,
                        rounds = environment.pacing.state.roundsCompleted,
                        thisSession = environment.pacing.state.interstitialsThisSession,
                        lastAt = environment.pacing.state.lastInterstitialAt,
                    ),
                    style = CasterType.rowDetail.copy(color = theme.textSecondary),
                )
            },
        )
        RowDivider()
        SettingsRow(
            title = "Counters",
            trailing = {
                val shown = environment.fakeAds?.shownCount ?: 0
                Text(
                    text = "launch ${environment.pacing.state.launchCount} · " +
                        "${environment.pacing.state.roundsCompleted} rounds · $shown shown",
                    style = CasterType.rowDetail.copy(color = theme.textSecondary),
                )
            },
        )
        RowDivider()
        SettingsRow(
            title = "Reset ad counters",
            titleColor = theme.accent,
            onClick = { environment.pacing.resetForTesting() },
        )
    }
}

/** Why an ad is or is not coming, in the order [AdPacing] checks it. */
private fun adStatus(
    hasPlus: Boolean,
    launchCount: Int,
    rounds: Int,
    thisSession: Int,
    lastAt: Long,
): String {
    if (hasPlus) return "off — Plus"
    if (launchCount < AdPacing.MINIMUM_LAUNCHES) {
        return "after ${AdPacing.MINIMUM_LAUNCHES - launchCount} more launch(es)"
    }
    if (rounds < AdPacing.MINIMUM_ROUNDS_COMPLETED) {
        return "after ${AdPacing.MINIMUM_ROUNDS_COMPLETED - rounds} more round(s)"
    }
    if (thisSession >= AdPacing.PER_SESSION_CAP) return "session cap reached"
    if (lastAt != 0L) {
        val wait = AdPacing.QUIET_PERIOD_MS - (System.currentTimeMillis() - lastAt)
        if (wait > 0) return "in ${wait / 60_000}m ${(wait / 1000) % 60}s"
    }
    return "eligible on the next game you finish"
}

// endregion

// region Grouped-list pieces

/**
 * The grouped-list look SwiftUI gets from `List` + `.insetGrouped`, which
 * Compose has no equivalent for. Written once here rather than approximated
 * per screen — the rounded card, the hairline between rows and the small caps
 * header are most of what makes a settings screen look native.
 */
@Composable
private fun SettingsSection(
    title: String,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val theme = LocalTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(start = 4.dp),
            style = CasterType.sectionHeader.copy(color = accent ?: theme.textSecondary),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.surfaceRaised)
                .border(1.dp, accent?.copy(alpha = 0.4f) ?: theme.border, RoundedCornerShape(16.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    detail: String? = null,
    titleColor: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val theme = LocalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tappable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = CasterType.rowTitle.copy(color = titleColor ?: theme.textPrimary),
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = CasterType.rowDetail.copy(color = theme.textSecondary),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun RowDivider() {
    val theme = LocalTheme.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(theme.border)
    )
}

/**
 * A locked palette is shown, not hidden. Somebody should be able to see what
 * they would be getting before deciding whether they want it.
 */
@Composable
private fun ThemeRow(
    selection: ThemeSelection,
    systemPalette: com.jesperhaafkes.caster.ui.theme.Theme,
    isLocked: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    val swatch = selection.palette(systemPalette)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(swatch.background)
                .border(1.dp, theme.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(swatch.accent)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = selection.title, style = CasterType.rowTitle.copy(color = theme.textPrimary))
            Text(
                text = selection.subtitle,
                style = CasterType.rowDetail.copy(color = theme.textSecondary),
            )
        }

        when {
            isLocked -> Text(
                text = "🔒",
                style = CasterType.rowDetail.copy(color = theme.textSecondary),
            )

            isActive -> Canvas(Modifier.size(18.dp)) {
                val tick = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.52f)
                    lineTo(size.width * 0.40f, size.height * 0.80f)
                    lineTo(size.width * 0.88f, size.height * 0.20f)
                }
                drawPath(
                    path = tick,
                    color = theme.accent,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

// endregion
