package com.jesperhaafkes.caster.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.LocalRosterStore
import com.jesperhaafkes.caster.LocalWheelStore
import com.jesperhaafkes.caster.domain.GameMode
import com.jesperhaafkes.caster.domain.Route
import com.jesperhaafkes.caster.ui.components.AddRow
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.ConfirmDeleteDialog
import com.jesperhaafkes.caster.ui.components.EditorEmptyState
import com.jesperhaafkes.caster.ui.components.EditorMenuDivider
import com.jesperhaafkes.caster.ui.components.EditorMenuItem
import com.jesperhaafkes.caster.ui.components.EditorRow
import com.jesperhaafkes.caster.ui.components.NamePromptDialog
import com.jesperhaafkes.caster.ui.components.OptionsMenuButton
import com.jesperhaafkes.caster.ui.components.PickerHeader
import com.jesperhaafkes.caster.ui.components.PrimaryButton
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.theme.PlayerPalette

/** Which name the prompt is collecting. One dialog serves both jobs. */
private enum class WheelNamePrompt { NEW_WHEEL, RENAME_WHEEL }

/**
 * The pinwheel's entry editor. Wheels are saved and named, so a group can keep
 * the flatmates, the five-a-side squad and the chore list side by side instead
 * of retyping one over the other. Entries are unbounded on purpose — the wheel
 * scales itself to whatever is in the list.
 */
@Composable
fun WheelSetupScreen(onBack: () -> Unit, onSpin: (Route) -> Unit) {
    val environment = LocalAppEnvironment.current
    val wheelStore = LocalWheelStore.current
    val rosterStore = LocalRosterStore.current

    var draftEntry by remember { mutableStateOf("") }
    var namePrompt by remember { mutableStateOf<WheelNamePrompt?>(null) }
    var isDeleteConfirmShown by remember { mutableStateOf(false) }
    val addFocus = remember { FocusRequester() }
    var focusTicket by remember { mutableStateOf(0) }

    LaunchedEffect(focusTicket) {
        if (focusTicket > 0) runCatching { addFocus.requestFocus() }
    }

    val entries = wheelStore.entries

    val entryCountLabel = when (val count = entries.size) {
        0 -> "No entries yet"
        1 -> "1 entry — needs two to spin"
        else -> "$count entries"
    }

    fun commitDraft() {
        if (draftEntry.isBlank()) return
        wheelStore.add(draftEntry)
        draftEntry = ""
        environment.hapticEngine.playFeedback(FeedbackType.LIGHT)
        // Keep focus so a list can be typed in without reaching for the field.
        focusTicket += 1
    }

    CasterScreen(title = "The Wheel", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PickerHeader(
                    glyph = "🎡",
                    title = wheelStore.selectedName,
                    subtitle = entryCountLabel,
                    modifier = Modifier.weight(1f),
                ) { dismiss ->
                    for (wheel in wheelStore.wheels) {
                        val marker = if (wheel.id == wheelStore.selectedID) "  ✓" else ""
                        EditorMenuItem("${wheel.name}  ·  ${wheel.entries.size}$marker") {
                            wheelStore.select(wheel.id)
                            dismiss()
                        }
                    }
                    EditorMenuDivider()
                    EditorMenuItem("New wheel") {
                        namePrompt = WheelNamePrompt.NEW_WHEEL
                        dismiss()
                    }
                }

                OptionsMenuButton { dismiss ->
                    EditorMenuItem("Rename wheel") {
                        namePrompt = WheelNamePrompt.RENAME_WHEEL
                        dismiss()
                    }
                    EditorMenuItem("Duplicate wheel") {
                        wheelStore.duplicateSelected()
                        dismiss()
                    }
                    EditorMenuItem(
                        label = "Delete wheel",
                        enabled = wheelStore.canDeleteWheel,
                        isDestructive = true,
                    ) {
                        isDeleteConfirmShown = true
                        dismiss()
                    }
                    EditorMenuDivider()
                    // Read from the saved roster rather than the in-play
                    // players, so this works before a name-based game has ever
                    // been started.
                    EditorMenuItem("Use player names", enabled = rosterStore.names.isNotEmpty()) {
                        wheelStore.replaceAll(rosterStore.names)
                        dismiss()
                    }
                    EditorMenuItem("Reset to sample names") {
                        wheelStore.resetToDefaults()
                        dismiss()
                    }
                    EditorMenuItem("Remove all entries", isDestructive = true) {
                        wheelStore.replaceAll(emptyList())
                        dismiss()
                    }
                }
            }

            AddRow(
                value = draftEntry,
                onValueChange = { draftEntry = it },
                placeholder = "Add an entry",
                modifier = Modifier.padding(horizontal = 16.dp),
                onCommit = { commitDraft() },
            )

            if (entries.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EditorEmptyState(glyph = "◌", message = "Add at least two entries to spin.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                        EditorRow(
                            swatch = PlayerPalette.spread(index, entries.size),
                            value = entry.label,
                            onValueChange = { wheelStore.rename(entry.id, it) },
                            onMoveUp = if (index > 0) {
                                { wheelStore.move(index, index - 1) }
                            } else {
                                null
                            },
                            onMoveDown = if (index < entries.lastIndex) {
                                { wheelStore.move(index, index + 1) }
                            } else {
                                null
                            },
                            onDelete = { wheelStore.remove(entry.id) },
                        )
                    }
                }
            }

            PrimaryButton(
                title = "Spin",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                isEnabled = wheelStore.canSpin,
            ) {
                environment.hapticEngine.playFeedback(FeedbackType.HEAVY)
                onSpin(Route.Game(GameMode.PINWHEEL))
            }
        }
    }

    when (namePrompt) {
        WheelNamePrompt.NEW_WHEEL -> NamePromptDialog(
            title = "New wheel",
            message = "Wheels are kept separately, so one does not overwrite another.",
            placeholder = "Wheel name",
            initialValue = "",
            onDismiss = { namePrompt = null },
            onSave = { name ->
                wheelStore.createWheel(name)
                namePrompt = null
                focusTicket += 1
            },
        )

        WheelNamePrompt.RENAME_WHEEL -> NamePromptDialog(
            title = "Rename wheel",
            message = null,
            placeholder = "Wheel name",
            initialValue = wheelStore.selectedName,
            onDismiss = { namePrompt = null },
            onSave = { name ->
                wheelStore.renameSelected(name)
                namePrompt = null
            },
        )

        null -> Unit
    }

    if (isDeleteConfirmShown) {
        ConfirmDeleteDialog(
            title = "Delete this wheel?",
            message = "${wheelStore.selectedName} and its ${entries.size} entries will be removed.",
            onDismiss = { isDeleteConfirmShown = false },
            onConfirm = {
                wheelStore.deleteSelected()
                isDeleteConfirmShown = false
            },
        )
    }
}
