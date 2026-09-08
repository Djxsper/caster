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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jesperhaafkes.caster.LocalAppEnvironment
import com.jesperhaafkes.caster.LocalEntitlements
import com.jesperhaafkes.caster.LocalRosterStore
import com.jesperhaafkes.caster.LocalWheelStore
import com.jesperhaafkes.caster.domain.PlayerLimits
import com.jesperhaafkes.caster.domain.PlusPrompt
import com.jesperhaafkes.caster.ui.components.AddRow
import com.jesperhaafkes.caster.ui.components.BarAction
import com.jesperhaafkes.caster.ui.components.BarTextAction
import com.jesperhaafkes.caster.ui.components.CasterScreen
import com.jesperhaafkes.caster.ui.components.ConfirmDeleteDialog
import com.jesperhaafkes.caster.ui.components.EditorEmptyState
import com.jesperhaafkes.caster.ui.components.EditorMenuDivider
import com.jesperhaafkes.caster.ui.components.EditorMenuItem
import com.jesperhaafkes.caster.ui.components.EditorRow
import com.jesperhaafkes.caster.ui.components.NamePromptDialog
import com.jesperhaafkes.caster.ui.components.OptionsMenuButton
import com.jesperhaafkes.caster.ui.components.PickerHeader
import com.jesperhaafkes.caster.ui.haptics.FeedbackType
import com.jesperhaafkes.caster.ui.theme.LocalTheme

/** Which name the prompt is collecting. One dialog serves both jobs. */
private enum class NamePrompt { NEW_ROSTER, RENAME_ROSTER }

/**
 * The roster editor, in the same shape as the pinwheel's: named groups you
 * switch between, and every keystroke written straight to the store.
 *
 * Deliberately has no navigation chrome of its own, so the same editor serves
 * both the setup screen and the dialog a game opens — the list of names is the
 * same list wherever you reach it from.
 */
@Composable
fun RosterEditor(modifier: Modifier = Modifier) {
    val theme = LocalTheme.current
    val environment = LocalAppEnvironment.current
    val rosterStore = LocalRosterStore.current
    val wheelStore = LocalWheelStore.current
    val entitlements = LocalEntitlements.current

    var draftName by remember { mutableStateOf("") }
    var namePrompt by remember { mutableStateOf<NamePrompt?>(null) }
    var plusPrompt by remember { mutableStateOf<PlusPrompt?>(null) }
    var isDeleteConfirmShown by remember { mutableStateOf(false) }
    val addFocus = remember { FocusRequester() }
    var focusTicket by remember { mutableStateOf(0) }

    LaunchedEffect(focusTicket) {
        if (focusTicket > 0) runCatching { addFocus.requestFocus() }
    }

    val members = rosterStore.members
    val canCommitDraft = !rosterStore.isFull && draftName.isNotBlank()

    val memberCountLabel = when (val count = members.size) {
        0 -> "No players yet"
        1 -> "1 player — needs two to start"
        PlayerLimits.MAXIMUM -> "$count players — full"
        else -> "$count players"
    }

    fun commitDraft() {
        if (!canCommitDraft) return
        rosterStore.add(draftName)
        draftName = ""
        environment.hapticEngine.playFeedback(FeedbackType.LIGHT)
        // Keep focus so twelve names can be typed straight through without
        // reaching back for the field between each one.
        if (!rosterStore.isFull) focusTicket += 1
    }

    Column(
        modifier = modifier.fillMaxSize(),
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
                glyph = "👥",
                title = rosterStore.selectedName,
                subtitle = memberCountLabel,
                modifier = Modifier.weight(1f),
            ) { dismiss ->
                for (roster in rosterStore.rosters) {
                    val marker = if (roster.id == rosterStore.selectedID) "  ✓" else ""
                    EditorMenuItem("${roster.name}  ·  ${roster.members.size}$marker") {
                        rosterStore.select(roster.id)
                        dismiss()
                    }
                }
                EditorMenuDivider()
                EditorMenuItem("New group") {
                    // Checked before the name prompt, not after: being asked to
                    // name a group and only then told it cannot be made is the
                    // rudest possible order to do this in.
                    if (rosterStore.canCreateRoster) {
                        namePrompt = NamePrompt.NEW_ROSTER
                    } else {
                        plusPrompt = PlusPrompt.ROSTER_LIMIT
                    }
                    dismiss()
                }
            }

            OptionsMenuButton { dismiss ->
                EditorMenuItem("Rename group") {
                    namePrompt = NamePrompt.RENAME_ROSTER
                    dismiss()
                }
                EditorMenuItem("Duplicate group") {
                    if (!rosterStore.duplicateSelected()) {
                        plusPrompt = PlusPrompt.ROSTER_LIMIT
                    }
                    dismiss()
                }
                EditorMenuItem(
                    label = "Delete group",
                    enabled = rosterStore.canDeleteRoster,
                    isDestructive = true,
                ) {
                    isDeleteConfirmShown = true
                    dismiss()
                }
                EditorMenuDivider()
                EditorMenuItem("Use wheel entries", enabled = wheelStore.labels.isNotEmpty()) {
                    rosterStore.replaceAll(wheelStore.labels)
                    dismiss()
                }
                EditorMenuItem("Reset to numbered players") {
                    rosterStore.resetToDefaults()
                    dismiss()
                }
                EditorMenuItem("Remove all names", isDestructive = true) {
                    rosterStore.replaceAll(emptyList())
                    dismiss()
                }
            }
        }

        AddRow(
            value = draftName,
            onValueChange = { draftName = it },
            placeholder = if (rosterStore.isFull) {
                "${PlayerLimits.MAXIMUM} players is the maximum"
            } else {
                "Add a player"
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = !rosterStore.isFull,
            canCommit = canCommitDraft,
            focusRequester = addFocus,
            onCommit = { commitDraft() },
        )

        if (members.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EditorEmptyState(
                    glyph = "👤",
                    message = "Add at least two players to start.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                itemsIndexed(members, key = { _, member -> member.id }) { index, member ->
                    // Where this person will actually sit, or -1 when they are
                    // sitting out. Taken from the *active* list rather than from
                    // the row's position, because that is what the games seat.
                    val seat = rosterStore.activeMembers.indexOfFirst { it.id == member.id }

                    EditorRow(
                        // The seat colour the games will actually use, so the
                        // list doubles as a key to the rings and the potato.
                        swatch = if (seat >= 0) theme.playerColor(seat) else theme.border,
                        value = member.name,
                        onValueChange = { rosterStore.rename(member.id, it) },
                        onMoveUp = if (index > 0) {
                            { rosterStore.move(index, index - 1) }
                        } else {
                            null
                        },
                        onMoveDown = if (index < members.lastIndex) {
                            { rosterStore.move(index, index + 1) }
                        } else {
                            null
                        },
                        onDelete = { rosterStore.remove(member.id) },
                        isActive = member.isActive,
                        onToggleActive = {
                            if (entitlements.hasActiveMemberToggle) {
                                environment.hapticEngine.playFeedback(FeedbackType.LIGHT)
                                rosterStore.setActive(member.id, !member.isActive)
                            } else {
                                plusPrompt = PlusPrompt.ACTIVE_MEMBERS
                            }
                        },
                    )
                }
            }
        }
    }

    when (namePrompt) {
        NamePrompt.NEW_ROSTER -> NamePromptDialog(
            title = "New group",
            message = "Groups are kept separately, so one does not overwrite another.",
            placeholder = "Group name",
            initialValue = "",
            onDismiss = { namePrompt = null },
            onSave = { name ->
                if (rosterStore.createRoster(name) == null) {
                    plusPrompt = PlusPrompt.ROSTER_LIMIT
                } else {
                    focusTicket += 1
                }
                namePrompt = null
            },
        )

        NamePrompt.RENAME_ROSTER -> NamePromptDialog(
            title = "Rename group",
            message = null,
            placeholder = "Group name",
            initialValue = rosterStore.selectedName,
            onDismiss = { namePrompt = null },
            onSave = { name ->
                rosterStore.renameSelected(name)
                namePrompt = null
            },
        )

        null -> Unit
    }

    plusPrompt?.let { prompt ->
        PlusDialog(prompt = prompt, onDismiss = { plusPrompt = null })
    }

    if (isDeleteConfirmShown) {
        ConfirmDeleteDialog(
            title = "Delete this group?",
            message = "${rosterStore.selectedName} and its ${members.size} names will be removed.",
            onDismiss = { isDeleteConfirmShown = false },
            onConfirm = {
                rosterStore.deleteSelected()
                isDeleteConfirmShown = false
            },
        )
    }
}

/**
 * The roster over a game, so names can be fixed from inside a round without
 * backing out of it. Every edit is already saved by the time this closes, so
 * dismissing it — deliberately or with a stray tap — can no longer cost anybody
 * their names.
 */
@Composable
fun RosterDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CasterScreen(
            title = "Players",
            onBack = null,
            actions = { BarTextAction(label = "Done", onClick = onDismiss) },
        ) {
            RosterEditor(Modifier.padding(top = 12.dp, bottom = 12.dp))
        }
    }
}

/** Opens the roster over a game. */
@Composable
fun RosterBarAction(onClick: () -> Unit) {
    BarAction(glyph = "👥", contentDescription = "Players", onClick = onClick)
}
