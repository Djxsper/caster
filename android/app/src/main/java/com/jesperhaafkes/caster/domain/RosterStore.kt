package com.jesperhaafkes.caster.domain

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One person at the table. A named type rather than a bare String so a list row
 * and its text field keep stable identity even when two people share a name.
 */
data class RosterMember(
    val id: UUID = UUID.randomUUID(),
    val name: String,
)

/**
 * A named group. Keeping several around is the whole point: the flatmates, the
 * five-a-side squad and a birthday party are three different tables, and
 * switching between them beats retyping twelve names.
 */
data class SavedRoster(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val members: List<RosterMember> = emptyList(),
) {
    val canPlay: Boolean get() = members.size >= PlayerLimits.MINIMUM
}

/**
 * Every saved roster plus which one is at the table, persisted so nothing is
 * retyped — not between launches, and not between one screen and the next.
 *
 * The deliberate twin of [WheelStore]: same shape, same storage strategy, same
 * editing verbs. Names used to live in a screen's own state, which meant that
 * leaving that screen — on purpose or by a stray back gesture — silently threw
 * away everything typed into it.
 */
class RosterStore(private val prefs: SharedPreferences) {

    private val _rosters = mutableStateListOf<SavedRoster>()
    val rosters: List<SavedRoster> get() = _rosters

    var selectedID: UUID? by mutableStateOf(null)
        private set

    init {
        load()
    }

    // region The roster at the table

    val selectedRoster: SavedRoster?
        get() = _rosters.firstOrNull { it.id == selectedID }

    val selectedName: String
        get() = selectedRoster?.name ?: "Players"

    val members: List<RosterMember>
        get() = selectedRoster?.members ?: emptyList()

    val names: List<String>
        get() = members.map { it.name }

    val canPlay: Boolean
        get() = members.size >= PlayerLimits.MINIMUM

    /**
     * Unlike the wheel, a roster is bounded: the games address people by seat
     * and [GameState] clamps to [PlayerLimits.MAXIMUM] anyway, so the editor
     * says so up front rather than accepting a name it would later drop.
     */
    val isFull: Boolean
        get() = members.size >= PlayerLimits.MAXIMUM

    /** The last roster cannot be deleted — there always has to be one to edit. */
    val canDeleteRoster: Boolean
        get() = _rosters.size > 1

    // endregion

    // region Members

    /** @return whether the name was taken. A full roster refuses it. */
    fun add(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || isFull) return false
        updateSelected { it.copy(members = it.members + RosterMember(name = trimmed)) }
        return true
    }

    fun rename(id: UUID, to: String) {
        val trimmed = to.trim()
        updateSelected { roster ->
            val index = roster.members.indexOfFirst { it.id == id }
            if (index < 0) return@updateSelected roster
            val members = roster.members.toMutableList()
            // An emptied field removes the row rather than seating a blank name.
            if (trimmed.isEmpty()) {
                members.removeAt(index)
            } else {
                members[index] = members[index].copy(name = trimmed)
            }
            roster.copy(members = members)
        }
    }

    fun remove(id: UUID) {
        updateSelected { roster -> roster.copy(members = roster.members.filterNot { it.id == id }) }
    }

    fun removeAt(index: Int) {
        updateSelected { roster ->
            if (index !in roster.members.indices) return@updateSelected roster
            roster.copy(members = roster.members.toMutableList().also { it.removeAt(index) })
        }
    }

    /**
     * Moves one row. iOS gets drag-to-reorder from its List for free; here the
     * editor puts a pair of arrows on each row and calls this.
     */
    fun move(from: Int, to: Int) {
        updateSelected { roster ->
            if (from !in roster.members.indices || to !in roster.members.indices) {
                return@updateSelected roster
            }
            val members = roster.members.toMutableList()
            members.add(to, members.removeAt(from))
            roster.copy(members = members)
        }
    }

    fun replaceAll(names: List<String>) {
        updateSelected { roster ->
            roster.copy(
                members = names
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(PlayerLimits.MAXIMUM)
                    .map { RosterMember(name = it) }
            )
        }
    }

    fun resetToDefaults() = replaceAll(STARTER_NAMES)

    // endregion

    // region Rosters

    fun select(id: UUID) {
        if (_rosters.none { it.id == id }) return
        selectedID = id
        save()
    }

    fun createRoster(named: String): UUID {
        val trimmed = named.trim()
        val roster = SavedRoster(name = trimmed.ifEmpty { nextDefaultName() })
        _rosters.add(roster)
        selectedID = roster.id
        save()
        return roster.id
    }

    fun renameSelected(to: String) {
        val trimmed = to.trim()
        if (trimmed.isEmpty()) return
        updateSelected { it.copy(name = trimmed) }
    }

    /**
     * Copies the roster at the table and switches to the copy. Member ids are
     * minted fresh so the two rosters never share identity.
     */
    fun duplicateSelected() {
        val roster = selectedRoster ?: return
        val copy = SavedRoster(
            name = "${roster.name} copy",
            members = roster.members.map { RosterMember(name = it.name) },
        )
        _rosters.add(copy)
        selectedID = copy.id
        save()
    }

    fun deleteSelected() {
        if (!canDeleteRoster) return
        val index = _rosters.indexOfFirst { it.id == selectedID }
        if (index < 0) return
        _rosters.removeAt(index)
        selectedID = _rosters[minOf(index, _rosters.size - 1)].id
        save()
    }

    // endregion

    // region Storage

    private fun updateSelected(body: (SavedRoster) -> SavedRoster) {
        val index = _rosters.indexOfFirst { it.id == selectedID }
        if (index < 0) return
        _rosters[index] = body(_rosters[index])
        save()
    }

    private fun nextDefaultName(): String {
        var number = _rosters.size + 1
        val taken = _rosters.map { it.name }.toSet()
        while ("Group $number" in taken) number += 1
        return "Group $number"
    }

    /**
     * No legacy migration: before this store existed the names lived in a
     * screen's own state and were never written anywhere to migrate from.
     */
    private fun load() {
        _rosters.clear()
        _rosters.addAll(decodeStored() ?: listOf(starterRoster()))

        val raw = prefs.getString(SELECTION_KEY, null)
        val id = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        selectedID = if (id != null && _rosters.any { it.id == id }) {
            id
        } else {
            _rosters.firstOrNull()?.id
        }
    }

    private fun decodeStored(): List<SavedRoster>? {
        val raw = prefs.getString(STORAGE_KEY, null) ?: return null
        val decoded = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { rosterIndex ->
                val obj = array.getJSONObject(rosterIndex)
                val membersJson = obj.getJSONArray("members")
                SavedRoster(
                    id = UUID.fromString(obj.getString("id")),
                    name = obj.getString("name"),
                    members = (0 until membersJson.length()).map { memberIndex ->
                        val member = membersJson.getJSONObject(memberIndex)
                        RosterMember(
                            id = UUID.fromString(member.getString("id")),
                            name = member.getString("name"),
                        )
                    },
                )
            }
        }.getOrNull()
        return decoded?.takeIf { it.isNotEmpty() }
    }

    private fun save() {
        val array = JSONArray()
        for (roster in _rosters) {
            val members = JSONArray()
            for (member in roster.members) {
                members.put(
                    JSONObject()
                        .put("id", member.id.toString())
                        .put("name", member.name)
                )
            }
            array.put(
                JSONObject()
                    .put("id", roster.id.toString())
                    .put("name", roster.name)
                    .put("members", members)
            )
        }
        prefs.edit()
            .putString(STORAGE_KEY, array.toString())
            .putString(SELECTION_KEY, selectedID?.toString())
            .apply()
    }

    // endregion

    companion object {
        private const val STORAGE_KEY = "caster.rosters"
        private const val SELECTION_KEY = "caster.rosters.selected"

        /**
         * The four numbered seats the setup screen used to open on, so a first
         * run looks exactly as it always did.
         */
        private val STARTER_NAMES = (1..4).map { "Player $it" }

        private fun starterRoster() =
            SavedRoster(name = "Players", members = STARTER_NAMES.map { RosterMember(name = it) })
    }
}
