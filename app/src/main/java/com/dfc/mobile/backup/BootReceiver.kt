package com.dfc.mobile.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the periodic backup after a reboot: WorkManager persists its own
 * jobs, but a fresh install that never ran (or an OEM force-stop) would
 * otherwise stay silent until the app is opened again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = com.dfc.mobile.Prefs.get(context)
        if (prefs.isConfigured) {
            BackupWorker.schedule(context, prefs.wifiOnly)
        }
    }
}
