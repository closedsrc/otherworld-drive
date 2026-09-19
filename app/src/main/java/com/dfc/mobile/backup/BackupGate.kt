package com.dfc.mobile.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.dfc.mobile.Prefs

/**
 * Why a pending item has not been sent yet.
 *
 * The work is gated by WorkManager, which waits silently: an item can sit for
 * hours in a perfectly healthy app, and "waiting to back up" with no reason
 * reads like the backup is broken rather than like it is holding off on mobile
 * data. This answers the same question the constraints do, so the strip can say
 * which condition is holding the run up. It decides nothing — the constraints
 * still do.
 */
enum class BackupGate {
    READY,
    NO_NETWORK,
    WIFI_ONLY,
    BATTERY_LOW,
    ;

    /**
     * Short enough for the strip on the Photos screen, and about what is being
     * waited for rather than about the app being idle.
     */
    val waitingLabel: String
        get() = when (this) {
            READY -> "sends itself"
            NO_NETWORK -> "waiting for a connection"
            WIFI_ONLY -> "waiting for Wi-Fi"
            BATTERY_LOW -> "waiting for the battery"
        }

    /** Why a tap did not start anything yet, or null when it did. */
    fun blockedMessage(): String? = when (this) {
        READY -> null
        NO_NETWORK -> "Queued. There is no connection right now."
        WIFI_ONLY ->
            "Queued. Backups are set to Wi-Fi only — turn that off in Settings to send over mobile data."
        BATTERY_LOW -> "Queued. The backup starts once the battery recovers."
    }

    companion object {

        /** Battery below this reads as low, matching the platform's own threshold. */
        private const val LOW_BATTERY_PERCENT = 15

        fun check(context: Context): BackupGate {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return NO_NETWORK
            }
            // "Wi-Fi only" means unmetered, which is not the same question as
            // "is it Wi-Fi": ethernet and a tethered connection count, and a
            // metered hotspot does not.
            if (Prefs.get(context).wifiOnly &&
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            ) {
                return WIFI_ONLY
            }
            return if (isBatteryLow(context)) BATTERY_LOW else READY
        }

        private fun isBatteryLow(context: Context): Boolean {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return false
            val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            // 0 is the platform declining to answer, not an empty battery.
            return percent in 1 until LOW_BATTERY_PERCENT
        }
    }
}
