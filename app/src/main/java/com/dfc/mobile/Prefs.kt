package com.dfc.mobile

import android.content.Context
import android.content.SharedPreferences

/**
 * Server connection settings plus the last backup bookkeeping the UI shows.
 * A Prefs backed by an in-memory map (probe()) carries candidate credentials
 * during Setup's verify-before-save without touching real storage.
 */
class Prefs private constructor(context: Context?) {
    private val sp: SharedPreferences =
        context?.getSharedPreferences("dfc_mobile", Context.MODE_PRIVATE)
            ?: InMemoryPrefs()

    var serverUrl: String
        get() = sp.getString(KEY_SERVER, "")!!.trimEnd('/')
        set(v) = sp.edit().putString(KEY_SERVER, v.trimEnd('/')).apply()

    var token: String
        get() = sp.getString(KEY_TOKEN, "")!!
        set(v) = sp.edit().putString(KEY_TOKEN, v).apply()

    var wifiOnly: Boolean
        get() = sp.getBoolean(KEY_WIFI_ONLY, true)
        set(v) = sp.edit().putBoolean(KEY_WIFI_ONLY, v).apply()

    var lastScanGeneration: Long
        get() = sp.getLong(KEY_LAST_SCAN_GEN, -1L)
        set(v) = sp.edit().putLong(KEY_LAST_SCAN_GEN, v).apply()

    var lastBackupAt: Long
        get() = sp.getLong(KEY_LAST_BACKUP, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_BACKUP, v).apply()

    val isConfigured: Boolean
        get() = serverUrl.startsWith("http") && token.isNotEmpty()

    /** SharedPreferences backed by a mutable map; lives and dies with the probe. */
    private class InMemoryPrefs : SharedPreferences {
        private val map = HashMap<String, Any>()
        private val lock = Any()
        override fun getString(key: String?, defValue: String?) = map[key] as? String ?: defValue
        override fun getLong(key: String?, defValue: Long) = map[key] as? Long ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = map[key] as? Boolean ?: defValue
        override fun getInt(key: String?, defValue: Int) = map[key] as? Int ?: defValue
        override fun getFloat(key: String?, defValue: Float) = map[key] as? Float ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getAll(): MutableMap<String, *> = map
        override fun contains(key: String?) = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = HashMap<String, Any>()
            private val removals = ArrayList<String>()
            override fun putString(k: String?, v: String?): SharedPreferences.Editor {
                if (v == null) removals.add(k!!) else pending[k!!] = v
                return this
            }
            override fun putLong(k: String?, v: Long): SharedPreferences.Editor { pending[k!!] = v; return this }
            override fun putBoolean(k: String?, v: Boolean): SharedPreferences.Editor { pending[k!!] = v; return this }
            override fun putInt(k: String?, v: Int): SharedPreferences.Editor { pending[k!!] = v; return this }
            override fun putFloat(k: String?, v: Float): SharedPreferences.Editor { pending[k!!] = v; return this }
            override fun putStringSet(k: String?, v: MutableSet<String>?): SharedPreferences.Editor = this
            override fun remove(k: String?): SharedPreferences.Editor = this.apply { removals.add(k!!) }
            override fun clear(): SharedPreferences.Editor = this
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                synchronized(this@InMemoryPrefs.lock) {
                    removals.forEach { this@InMemoryPrefs.map.remove(it) }
                    this@InMemoryPrefs.map.putAll(pending)
                }
            }
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    companion object {
        private const val KEY_SERVER = "server_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_LAST_SCAN_GEN = "last_scan_generation"

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }

        /**
         * Ephemeral prefs for the Setup screen's verify-before-save probe —
         * carries the candidate credentials without touching real storage.
         */
        fun probe(serverUrl: String, token: String): Prefs {
            val p = Prefs(null)
            p.serverUrl = serverUrl
            p.token = token
            return p
        }
    }
}
