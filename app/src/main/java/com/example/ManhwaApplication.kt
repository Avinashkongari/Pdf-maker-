package com.example

import android.app.Application
import android.util.Log
import androidx.work.Configuration

class ManhwaApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
