package com.jesperhaafkes.caster

import android.content.SharedPreferences

/**
 * An in-memory [SharedPreferences], so the stores can be tested without an
 * emulator.
 *
 * The android.jar on a unit-test classpath is a stub whose every method throws,
 * which is why `Context.getSharedPreferences` cannot be used here — but
 * `SharedPreferences` is an *interface*, and implementing it costs nothing and
 * pulls in no Robolectric.
 *
 * Deliberately synchronous: `apply()` writes immediately, so a test never has
 * to wait for a background flush that in production is invisible anyway.
 */
class FakePrefs : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = edited { pending[key] = value }

        override fun putStringSet(key: String, value: MutableSet<String>?) =
            edited { pending[key] = value }

        override fun putInt(key: String, value: Int) = edited { pending[key] = value }

        override fun putLong(key: String, value: Long) = edited { pending[key] = value }

        override fun putFloat(key: String, value: Float) = edited { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean) = edited { pending[key] = value }

        override fun remove(key: String) = edited { removals += key }

        override fun clear() = edited { clearAll = true }

        override fun commit(): Boolean {
            if (clearAll) values.clear()
            removals.forEach { values.remove(it) }
            // A null value means "unset" in SharedPreferences, not "store null".
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }

        override fun apply() {
            commit()
        }

        private fun edited(body: () -> Unit): SharedPreferences.Editor {
            body()
            return this
        }
    }
}
