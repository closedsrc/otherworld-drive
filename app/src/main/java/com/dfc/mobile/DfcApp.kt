package com.dfc.mobile

import android.app.Application
import androidx.work.Configuration

class DfcApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
