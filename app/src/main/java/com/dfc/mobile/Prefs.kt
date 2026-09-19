package com.dfc.mobile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Local settings plus the last backup bookkeeping the UI shows.
 *
 * The device token is the one secret this app holds, so it lives in
 * EncryptedSharedPreferences (key material in the Android Keystore) rather
 * than beside the preferences. If the Keystore is unusable — a corrupted
 * profile, a broken vendor Keymaster — the secure file is dropped and the app
 * falls back to ordinary prefs with a logged warning: degrading to
 * OS-sandboxed storage beats bricking setup, and the token can be revoked
 * server-side the moment the user notices.
 *
 * A Prefs backed by an in-memory map (probe()) carries a candidate token
 * during Setup's verify-before-save without touching real storage.
 */
class Prefs private constructor(context: Context?) {
    private val sp: SharedPreferences =
        context?.getSharedPreferences("dfc_mobile", Context.MODE_PRIVATE)
            ?: InMemoryPrefs()

    private val secure: SharedPreferences =
        context?.let { createSecure(it) } ?: InMemoryPrefs()

    private fun createSecure(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "dfc_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // A Keystore that cannot be unlocked leaves every get() throwing;
            // reset the file once and fall back so the user can re-register.
            Log.e("Prefs", "secure prefs unavailable, falling back to plain prefs", e)
            try {
                context.deleteSharedPreferences("dfc_secure")
            } catch (_: Exception) {
            }
            context.getSharedPreferences("dfc_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * This device's own credential, minted by the server at registration.
     * It authorizes THIS install only and can be revoked from the drive's
     * device list without touching any other phone.
     */
    var token: String
        get() = secure.getString(KEY_TOKEN, "")!!
        set(v) {
            if (v.isEmpty()) secure.edit().remove(KEY_TOKEN).apply()
            else secure.edit().putString(KEY_TOKEN, v).apply()
        }

    /** The opaque device id the server returned at registration, if known. */
    var deviceId: String
        get() = sp.getString(KEY_DEVICE_ID, "")!!
        set(v) = sp.edit().putString(KEY_DEVICE_ID, v).apply()

    /** Server address. Empty means the default drive (see [Server]). */
    var serverUrl: String
        get() = sp.getString(KEY_SERVER_URL, "")!!.trim()
        set(v) = sp.edit().putString(KEY_SERVER_URL, v.trim().trimEnd('/')).apply()

    var wifiOnly: Boolean
        get() = sp.getBoolean(KEY_WIFI_ONLY, true)
        set(v) = sp.edit().putBoolean(KEY_WIFI_ONLY, v).apply()

    var appLockEnabled: Boolean
        get() = sp.getBoolean(KEY_APP_LOCK, false)
        set(v) = sp.edit().putBoolean(KEY_APP_LOCK, v).apply()

    var lastScanGeneration: Long
        get() = sp.getLong(KEY_LAST_SCAN_GEN, -1L)
        set(v) = sp.edit().putLong(KEY_LAST_SCAN_GEN, v).apply()

    /**
     * When the index was last walked end to end. The walk is what notices photos
     * the phone no longer has, and MediaStore's generation counter cannot report
     * a deletion that happened while the app was not running, so a walk is forced
     * once a day rather than only when the counter moves.
     */
    var lastFullScanAt: Long
        get() = sp.getLong(KEY_LAST_FULL_SCAN, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_FULL_SCAN, v).apply()

    var lastBackupAt: Long
        get() = sp.getLong(KEY_LAST_BACKUP, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_BACKUP, v).apply()

    val isConfigured: Boolean
        get() = token.isNotEmpty()

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
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_APP_LOCK = "app_lock_enabled"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_LAST_SCAN_GEN = "last_scan_generation"
        private const val KEY_LAST_FULL_SCAN = "last_full_scan_at"

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }

        /**
         * Ephemeral prefs for the Setup screen's verify-before-save probe —
         * carries the candidate token without touching real storage.
         */
        fun probe(token: String): Prefs {
            val p = Prefs(null)
            p.token = token
            return p
        }
    }
}
