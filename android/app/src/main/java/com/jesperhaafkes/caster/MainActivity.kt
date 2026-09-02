package com.jesperhaafkes.caster

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jesperhaafkes.caster.domain.GameState
import com.jesperhaafkes.caster.domain.RosterStore
import com.jesperhaafkes.caster.domain.WheelStore
import com.jesperhaafkes.caster.ui.screens.LaunchScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import com.jesperhaafkes.caster.ui.theme.materialScheme
import androidx.compose.material3.LocalTextStyle
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
import com.jesperhaafkes.caster.ui.theme.LocalTheme
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
 */
@Composable
fun CasterApp() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences("caster", Context.MODE_PRIVATE)
    }

    val environment = remember(context) { AppEnvironment(context) }
    val gameState = remember { GameState() }
    val wheelStore = remember(prefs) { WheelStore(prefs) }
    val rosterStore = remember(prefs) { RosterStore(prefs) }

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

    val theme = themeForScheme()
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
        ) {
            LaunchScreen()
        }
    }
}
