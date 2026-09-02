package com.jesperhaafkes.caster.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.LocalGameState
import com.jesperhaafkes.caster.LocalRosterStore
import com.jesperhaafkes.caster.domain.Route
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.haptics.FeedbackType

/**
 * Name entry for the modes that address people by name. The list itself is
 * [RosterEditor], which reads and writes the store — so leaving this screen, by
 * the button or by the back gesture, keeps every name that was typed.
 */
@Composable
fun PlayerSetupScreen(onBack: () -> Unit, onStart: (Route) -> Unit) {
    val environment = LocalAppEnvironment.current
    val gameState = LocalGameState.current
    val rosterStore = LocalRosterStore.current

    CasterScreen(title = "Players", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RosterEditor(Modifier.weight(1f))

            PrimaryButton(
                title = "Start Game",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                isEnabled = rosterStore.canPlay,
            ) {
                gameState.adoptRoster(rosterStore.names)
                environment.hapticEngine.playFeedback(FeedbackType.HEAVY)
                onStart(Route.Game(gameState.currentMode))
            }
        }
    }
}
