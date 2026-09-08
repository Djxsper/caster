package com.jesperhaafkes.caster.ui.ads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.theme.CasterType
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import kotlinx.coroutines.delay

/**
 * A stand-in interstitial, for judging the pacing before committing to an ad
 * network.
 *
 * No ad SDK is linked in any build that can be made from this repository, so
 * without this there is no way to answer the only question that actually
 * matters — *is this annoying?* — until after an AdMob account, a Gradle
 * dependency and a signed Play build. That is far too late to find out the
 * answer is yes.
 *
 * It goes through the same [AdPresenter] interface and is gated by the same
 * `AdPacing` rules as the real thing, so what you feel here is what shipping
 * would feel like. The only difference is what appears on screen.
 *
 * Debug builds only — [AdPresenterFactory] never constructs it otherwise.
 */
class FakeAdPresenter : AdPresenter {

    var isShowing: Boolean by mutableStateOf(false)
        private set

    /**
     * Counters for the debug readout in Settings, so a long quiet stretch reads
     * as the rules working rather than as something being broken.
     */
    var shownCount: Int by mutableIntStateOf(0)
        private set
    var preloadCount: Int by mutableIntStateOf(0)
        private set
    var rewardedCount: Int by mutableIntStateOf(0)
        private set

    override val isAvailable: Boolean get() = true

    override fun preload() {
        preloadCount += 1
    }

    /**
     * [onFinished] fires immediately, exactly as a real presenter does — the
     * SDK hands control back as soon as it has presented, and dismissal is the
     * ad's own business. Keeping the contract identical is the point.
     */
    override fun presentInterstitial(onFinished: () -> Unit) {
        shownCount += 1
        isShowing = true
        onFinished()
    }

    override fun presentRewarded(onReward: () -> Unit, onFinished: () -> Unit) {
        rewardedCount += 1
        onReward()
        onFinished()
    }

    fun dismiss() {
        isShowing = false
    }
}

/**
 * What the stand-in looks like. Loud on purpose: this must never be mistaken
 * for a real ad, and it should be obvious in a screen recording which frames
 * were the fake.
 */
@Composable
fun FakeAdScreen(onDismiss: () -> Unit) {
    val theme = LocalTheme.current

    // A real interstitial does not let the back gesture out of it, and without
    // this one the press would fall through to the navigation stack behind and
    // pop a screen you cannot see. Swallowing it is both more faithful and the
    // only way the close-delay below actually costs anything.
    BackHandler(enabled = true) { }

    /** How long a real interstitial usually withholds its close button. */
    val closeDelaySeconds = 3
    var secondsLeft by remember { mutableIntStateOf(closeDelaySeconds) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    // The bar is the honest part of the imitation: a real interstitial tells you
    // how long you are trapped, and watching it fill is most of the annoyance
    // being measured here.
    val progress by animateFloatAsState(
        targetValue = 1f - secondsLeft.toFloat() / closeDelaySeconds,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "fake-ad-progress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "TEST BUILD",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.warning)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = CasterType.badge.copy(color = theme.background),
            )

            Spacer(Modifier.height(20.dp))

            Text(text = "▨", style = CasterType.display.copy(color = theme.textSecondary))

            Spacer(Modifier.height(20.dp))

            Text(
                text = "An ad would appear here",
                style = CasterType.subtitle.copy(
                    color = theme.textPrimary,
                    textAlign = TextAlign.Center,
                ),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Same timing rules as the real one. Ask yourself whether " +
                    "this interrupted anything.",
                modifier = Modifier.widthIn(max = 300.dp),
                style = CasterType.rowDetail.copy(
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center,
                ),
            )

            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.border),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = progress.coerceIn(0f, 1f)
                            transformOrigin =
                                androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .background(theme.warning),
                )
            }
        }

        // A real interstitial makes you wait before it lets you out. Without
        // that this would feel far cheaper than the real thing and the whole
        // exercise would flatter itself.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            PrimaryButton(
                title = if (secondsLeft > 0) "Close in $secondsLeft" else "Close",
                isEnabled = secondsLeft <= 0,
                onClick = onDismiss,
            )
        }
    }
}
