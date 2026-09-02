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
 * One slice of the pinwheel. A named type rather than a bare String so a list
 * row and the spin animation both have stable identity even when two entries
 * carry the same text.
 */
data class WheelEntry(
    val id: UUID = UUID.randomUUID(),
    val label: String,
)

/**
 * A named wheel. Groups keep several of these around — the flatmates, the
 * five-a-side squad, a list of chores — and switch between them rather than
 * retyping one list into another.
 */
data class SavedWheel(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val entries: List<WheelEntry> = emptyList(),
) {
    val canSpin: Boolean get() = entries.size >= 2
}

/**
 * Every saved wheel plus which one is in play, persisted so nothing is retyped
 * between launches. Entry counts are deliberately unbounded — the wheel scales
 * its own slices, type size and label density to whatever is on it.
 */
class WheelStore(private val prefs: SharedPreferences) {

    private val _wheels = mutableStateListOf<SavedWheel>()
    val wheels: List<SavedWheel> get() = _wheels

    var selectedID: UUID? by mutableStateOf(null)
        private set

    init {
        load()
    }

    // region The wheel in play

    val selectedWheel: SavedWheel?
        get() = _wheels.firstOrNull { it.id == selectedID }

    val selectedName: String
        get() = selectedWheel?.name ?: "Wheel"

    val entries: List<WheelEntry>
        get() = selectedWheel?.entries ?: emptyList()

    val labels: List<String>
        get() = entries.map { it.label }

    val canSpin: Boolean
        get() = entries.size >= 2

    /** The last wheel cannot be deleted — there always has to be one to edit. */
    val canDeleteWheel: Boolean
        get() = _wheels.size > 1

    // endregion

    // region Entries

    fun add(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        updateSelected { it.copy(entries = it.entries + WheelEntry(label = trimmed)) }
    }

    fun rename(id: UUID, to: String) {
        val trimmed = to.trim()
        updateSelected { wheel ->
            val index = wheel.entries.indexOfFirst { it.id == id }
            if (index < 0) return@updateSelected wheel
            val entries = wheel.entries.toMutableList()
            // An emptied field removes the row rather than leaving a blank slice.
            if (trimmed.isEmpty()) {
                entries.removeAt(index)
            } else {
                entries[index] = entries[index].copy(label = trimmed)
            }
            wheel.copy(entries = entries)
        }
    }

    fun remove(id: UUID) {
        updateSelected { wheel -> wheel.copy(entries = wheel.entries.filterNot { it.id == id }) }
    }

    fun removeAt(index: Int) {
        updateSelected { wheel ->
            if (index !in wheel.entries.indices) return@updateSelected wheel
            wheel.copy(entries = wheel.entries.toMutableList().also { it.removeAt(index) })
        }
    }

    /**
     * Moves one row. iOS gets drag-to-reorder from its List for free; here the
     * editor puts a pair of arrows on each row and calls this.
     */
    fun move(from: Int, to: Int) {
        updateSelected { wheel ->
            if (from !in wheel.entries.indices || to !in wheel.entries.indices) {
                return@updateSelected wheel
            }
            val entries = wheel.entries.toMutableList()
            entries.add(to, entries.removeAt(from))
            wheel.copy(entries = entries)
        }
    }

    fun replaceAll(labels: List<String>) {
        updateSelected { wheel ->
            wheel.copy(
                entries = labels
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { WheelEntry(label = it) }
            )
        }
    }

    fun resetToDefaults() = replaceAll(STARTER_LABELS)

    // endregion

    // region Wheels

    fun select(id: UUID) {
        if (_wheels.none { it.id == id }) return
        selectedID = id
        save()
    }

    fun createWheel(named: String): UUID {
        val trimmed = named.trim()
        val wheel = SavedWheel(name = trimmed.ifEmpty { nextDefaultName() })
        _wheels.add(wheel)
        selectedID = wheel.id
        save()
        return wheel.id
    }

    fun renameSelected(to: String) {
        val trimmed = to.trim()
        if (trimmed.isEmpty()) return
        updateSelected { it.copy(name = trimmed) }
    }

    /**
     * Copies the wheel in play and switches to the copy. Entry ids are minted
     * fresh so the two wheels never share identity.
     */
    fun duplicateSelected() {
        val wheel = selectedWheel ?: return
        val copy = SavedWheel(
            name = "${wheel.name} copy",
            entries = wheel.entries.map { WheelEntry(label = it.label) },
        )
        _wheels.add(copy)
        selectedID = copy.id
        save()
    }

    fun deleteSelected() {
        if (!canDeleteWheel) return
        val index = _wheels.indexOfFirst { it.id == selectedID }
        if (index < 0) return
        _wheels.removeAt(index)
        selectedID = _wheels[minOf(index, _wheels.size - 1)].id
        save()
    }

    // endregion

    // region Storage

    private fun updateSelected(body: (SavedWheel) -> SavedWheel) {
        val index = _wheels.indexOfFirst { it.id == selectedID }
        if (index < 0) return
        _wheels[index] = body(_wheels[index])
        save()
    }

    private fun nextDefaultName(): String {
        var number = _wheels.size + 1
        val taken = _wheels.map { it.name }.toSet()
        while ("Wheel $number" in taken) number += 1
        return "Wheel $number"
    }

    private fun load() {
        _wheels.clear()
        _wheels.addAll(decodeStored() ?: migratedLegacyWheels() ?: listOf(starterWheel()))

        val raw = prefs.getString(SELECTION_KEY, null)
        val id = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        selectedID = if (id != null && _wheels.any { it.id == id }) {
            id
        } else {
            _wheels.firstOrNull()?.id
        }
    }

    private fun decodeStored(): List<SavedWheel>? {
        val raw = prefs.getString(STORAGE_KEY, null) ?: return null
        val decoded = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { wheelIndex ->
                val obj = array.getJSONObject(wheelIndex)
                SavedWheel(
                    id = UUID.fromString(obj.getString("id")),
                    name = obj.getString("name"),
                    entries = decodeEntries(obj.getJSONArray("entries")),
                )
            }
        }.getOrNull()
        return decoded?.takeIf { it.isNotEmpty() }
    }

    /**
     * Carries a list saved by the single-wheel build into a named wheel, so an
     * upgrade does not silently drop what someone already typed in.
     */
    private fun migratedLegacyWheels(): List<SavedWheel>? {
        val raw = prefs.getString(LEGACY_ENTRIES_KEY, null) ?: return null
        val entries = runCatching { decodeEntries(JSONArray(raw)) }.getOrNull()
        if (entries.isNullOrEmpty()) return null

        prefs.edit().remove(LEGACY_ENTRIES_KEY).apply()
        return listOf(SavedWheel(name = "My Wheel", entries = entries))
    }

    private fun decodeEntries(array: JSONArray): List<WheelEntry> =
        (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            WheelEntry(
                id = UUID.fromString(entry.getString("id")),
                label = entry.getString("label"),
            )
        }

    private fun save() {
        val array = JSONArray()
        for (wheel in _wheels) {
            val entries = JSONArray()
            for (entry in wheel.entries) {
                entries.put(
                    JSONObject()
                        .put("id", entry.id.toString())
                        .put("label", entry.label)
                )
            }
            array.put(
                JSONObject()
                    .put("id", wheel.id.toString())
                    .put("name", wheel.name)
                    .put("entries", entries)
            )
        }
        prefs.edit()
            .putString(STORAGE_KEY, array.toString())
            .putString(SELECTION_KEY, selectedID?.toString())
            .apply()
    }

    // endregion

    companion object {
        private const val STORAGE_KEY = "caster.wheels"
        private const val SELECTION_KEY = "caster.wheels.selected"

        /** The single-list format this replaced. Read once, then migrated away. */
        private const val LEGACY_ENTRIES_KEY = "caster.wheel.entries"

        private val STARTER_LABELS = listOf("Alex", "Bo", "Cleo", "Dax", "Eve", "Fin")

        private fun starterWheel() =
            SavedWheel(name = "Party", entries = STARTER_LABELS.map { WheelEntry(label = it) })
    }
}
