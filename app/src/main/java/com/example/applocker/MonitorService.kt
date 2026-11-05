package com.example.applocker

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val targetPackages = listOf(
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.zhiliaoapp.musically",          // TikTok global
        "com.ss.android.ugc.aweme"           // TikTok alternatif
    )

    private val prefs by lazy { getSharedPreferences("locker_prefs", MODE_PRIVATE) }

    private var lastCheckedTime = 0L
    private var accumulatedMs = 0L
    private var lockUntil = 0L

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
                handler.postDelayed(this, 3000)
                return
            }

            // Hitung waktu penggunaan sejak pengecekan terakhir
            val usageSinceLast = getUsageTimeSince(lastCheckedTime)
            accumulatedMs += usageSinceLast
            lastCheckedTime = now

            val offMinutes = prefs.getInt("off_minutes", 20)
            val limitMs = TimeUnit.MINUTES.toMillis(offMinutes.toLong())

            if (accumulatedMs >= limitMs) {
                // Waktu habis → kunci
                val waitMinutes = prefs.getInt("wait_minutes", 15)
                lockUntil = now + TimeUnit.MINUTES.toMillis(waitMinutes.toLong())
                accumulatedMs = 0L
                prefs.edit()
                    .putLong("lock_until", lockUntil)
                    .putLong("accumulated", 0L)
                    .apply()
                checkAndShowLock()
            } else {
                prefs.edit().putLong("accumulated", accumulatedMs).apply()
            }

            handler.postDelayed(this, 10_000) // cek setiap 10 detik
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        lockUntil = prefs.getLong("lock_until", 0)
        accumulatedMs = prefs.getLong("accumulated", 0)
        lastCheckedTime = System.currentTimeMillis()

        handler.post(checkRunnable)
    }

    private fun getUsageTimeSince(since: Long): Long {
        if (since <= 0) return 0L

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, since, end) ?: return 0L

        var total = 0L
        for (stat in stats) {
            if (targetPackages.contains(stat.packageName)) {
                // Ambil waktu yang benar-benar baru digunakan
                if (stat.lastTimeUsed >= since) {
                    total += stat.totalTimeInForeground
                }
            }
        }
        // Batasi supaya tidak terlalu besar karena data harian
        return total.coerceAtMost(TimeUnit.MINUTES.toMillis(2))
    }

    private fun checkAndShowLock() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 3000
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return

        val current = stats.maxByOrNull { it.lastTimeUsed }
        if (current != null && targetPackages.contains(current.packageName)) {
            val intent = Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "monitor",
            "App Locker",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "monitor")
            .setContentTitle("App Locker aktif")
            .setContentText("Memantau YouTube, Facebook & TikTok")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }
}
