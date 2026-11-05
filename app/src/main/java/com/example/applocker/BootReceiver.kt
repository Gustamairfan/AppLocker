package com.example.applocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Accessibility Service dikelola oleh sistem,
        // jadi tidak perlu di-start manual saat boot.
        // Biarkan kosong atau hanya log jika diperlukan.
    }
}
