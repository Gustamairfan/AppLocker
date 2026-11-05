package com.example.applocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("locker_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("active", false)) {
                val service = Intent(context, MonitorService::class.java)
                ContextCompat.startForegroundService(context, service)
            }
        }
    }
}
