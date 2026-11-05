package com.example.applocker

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val targetPackages = listOf(
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.aweme"
    )

    private val prefs by lazy { getSharedPreferences("locker_prefs", MODE_PRIVATE) }

    private var accumulatedMs = 0L
    private var lockUntil = 0L
    private var lastTick = 0L
    private var isTargetInForeground = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!prefs.getBoolean("active", false)) {
                stopSelf()
                return
            }

            val now = System.currentTimeMillis()

            // Masih dalam masa kunci
            if (now < lockUntil) {
                checkAndShowLock()
                handler.postDelayed(this, 1500)
                return
            }

            val currentlyTarget = isAnyTargetInForeground()

            if (currentlyTarget) {
                if (!isTargetInForeground) {
                    lastTick = SystemClock.elapsedRealtime()
                    isTargetInForeground = true
                } else {
                    val elapsed = SystemClock.elapsedRealtime() - lastTick
                    if (elapsed > 0) {
                        accumulatedMs += elapsed
                        lastTick = SystemClock.elapsedRealtime()
                        
                        // Simpan setiap kali bertambah
                        prefs.edit().putLong("accumulated", accumulatedMs).apply()
                    }
                }
            } else {
                isTargetInForeground = false
            }

            val offMinutes = prefs.getInt("off_minutes", 20)
            val limitMs = TimeUnit.MINUTES.toMillis(offMinutes.toLong())

            if (accumulatedMs >= limitMs) {
                val waitMinutes = prefs.getInt("wait_minutes", 15)
                lockUntil = now + TimeUnit.MINUTES.toMillis(waitMinutes.toLong())
                accumulatedMs = 0L
                prefs.edit()
                    .putLong("lock_until", lockUntil)
                    .putLong("accumulated", 0L)
                    .apply()
                checkAndShowLock()
            }

            handler.postDelayed(this, 2000) // cek setiap 2 detik
        }
    }

    private fun isAnyTargetInForeground(): Boolean {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 8000
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return false

            val current = stats.maxByOrNull { it.lastTimeUsed }
            return current != null && targetPackages.contains(current.packageName)
        } catch (e: Exception) {
            return false
        }
    }

    private fun checkAndShowLock() {
        if (isAnyTargetInForeground()) {
            val intent = Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        lockUntil = prefs.getLong("lock_until", 0)
        accumulatedMs = prefs.getLong("accumulated", 0)
        lastTick = SystemClock.elapsedRealtime()

        handler.post(checkRunnable)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "monitor",
            "App Locker",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "monitor")
            .setContentTitle("App Locker aktif")
            .setContentText("Memantau YouTube, Facebook & TikTok")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }
}
