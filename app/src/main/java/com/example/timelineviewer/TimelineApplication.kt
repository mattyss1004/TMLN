package com.example.timelineviewer

import android.app.Application
import com.example.timelineviewer.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimelineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-warm database initialization on launch to trigger auto-seeding if fresh install
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(this@TimelineApplication).openHelper.writableDatabase
        }
    }
}
