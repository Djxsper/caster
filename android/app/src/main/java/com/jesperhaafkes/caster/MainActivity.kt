package com.jesperhaafkes.caster

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jesperhaafkes.caster.billing.BillingService
import com.jesperhaafkes.caster.domain.EntitlementStore
import com.jesperhaafkes.caster.domain.GameState
import com.jesperhaafkes.caster.domain.RosterStore
import com.jesperhaafkes.caster.domain.WheelStore
import com.jesperhaafkes.caster.ui.ads.FakeAdScreen
import com.jesperhaafkes.caster.ui.screens.LaunchScreen
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
import com.jesperhaafkes.caster.ui.theme.LocalTheme
import com.jesperhaafkes.caster.ui.theme.ThemeStore
import com.jesperhaafkes.caster.ui.theme.materialScheme
import com.jesperhaafkes.caster.ui.theme.themeForScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system splash hands over to the
        // app theme rather than flashing between the two.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CasterApp() }
    }
}

/**
 * Reads the system colour scheme and publishes the matching palette. Doing it
 * here (rather than around the activity) is what makes the theme track a live
 * light/dark switch.
 *
 * Also the one place entitlements are turned into limits. The stores hold a
 * plain `capacity` number and know nothing about purchases; this is the seam
 * between "what was bought" and "what the app allows", so there is exactly one
 * place to read to find out how the two are connected.
 */
@Composable
fun CasterApp() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences("caster", Context.MODE_PRIVATE)
    }

    val environment = remember(context, prefs) { AppEnvironment(context, prefs) }
    val gameState = remember { GameState() }
    val wheelStore = remember(prefs) { WheelStore(prefs) }
    val rosterStore = remember(prefs) { RosterStore(prefs) }
    val themeStore = remember(prefs) { ThemeStore(prefs) }
    val entitlements = remember(prefs) { EntitlementStore(prefs) }
    val billing = remember(context, entitlements) {
        BillingService(context.applicationContext, entitlements)
    }

    LaunchedEffect(Unit) {
        // Before the caps are applied, so an existing library is measured as it
        // stands rather than after being refused something.
        entitlements.grandfatherIfNeeded(
            wheelCount = wheelStore.wheels.size,
            rosterCount = rosterStore.rosters.size,
        )
        environment.pacing.beginSession()
        billing.start()
    }

    // Covers the whole lifecycle in one place: a purchase, a restore on a new
    // device, and a refund revoking it again.
    LaunchedEffect(entitlements.hasPlus) {
        wheelStore.capacity = entitlements.savedWheelCapacity
        rosterStore.capacity = entitlements.savedRosterCapacity
        rosterStore.honoursActiveFlags = entitlements.hasActiveMemberToggle
    }

    // Own the engines here rather than in a screen's disposal: navigating to a
    // destination can otherwise tear them down mid-round.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, environment) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    environment.hapticEngine.startEngine()
                    environment.soundEngine.start()
                    environment.soundEngine.resume()
                }

                Lifecycle.Event.ON_STOP -> {
                    environment.hapticEngine.endEngine()
                    environment.soundEngine.stop()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            environment.hapticEngine.endEngine()
            environment.soundEngine.stop()
        }
    }

    // Falls back to the system palette whenever Plus is not held, without
    // forgetting which one was picked.
    val systemPalette = themeForScheme()
    val theme = themeStore.effective(entitlements.hasPlus).palette(systemPalette)

    MaterialTheme(colorScheme = theme.materialScheme(isSystemInDarkTheme())) {
        CompositionLocalProvider(
            LocalTheme provides theme,
            // Only covers a Text that omits `style` entirely. Every call site in
            // this app passes an explicit TextStyle, which replaces this rather
            // than merging with it, so the rounded face is named at each of them
            // instead - see Type.kt. This is here for anything added later that
            // does not.
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = CasterFontFamily),
            LocalAppEnvironment provides environment,
            LocalGameState provides gameState,
            LocalWheelStore provides wheelStore,
            LocalRosterStore provides rosterStore,
            LocalEntitlements provides entitlements,
            LocalBilling provides billing,
            LocalThemeStore provides themeStore,
        ) {
            LaunchScreen()

            // The stand-in interstitial, over the whole app. Presented here
            // rather than inside the mode list so it covers whatever is on
            // screen, exactly as a real one would. `fakeAds` is null in every
            // release build, which is what keeps this out of the shipped app.
            val fake = environment.fakeAds
            if (fake != null && fake.isShowing) {
                FakeAdScreen(onDismiss = { fake.dismiss() })
            }
        }
    }
}
