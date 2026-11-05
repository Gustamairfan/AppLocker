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
        "com.zhiliaoapp.musically"   // TikTok
    )
    private val prefs by lazy { getSharedPreferences("locker_prefs", MODE_PRIVATE) }

    private var accumulatedMs = 0L
    private var lockUntil = 0L

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!prefs.getBoolean("active", false)) {
                stopSelf()
                return
            }

            val now = System.currentTimeMillis()
            if (now < lockUntil) {
                // Masih dalam masa kunci → pastikan overlay muncul jika target dibuka
                checkAndLock()
            } else {
                // Hitung waktu penggunaan
                val usage = getUsageTimeLastMinutes(2) // cek 2 menit terakhir
                accumulatedMs += usage

                val offMinutes = prefs.getInt("off_minutes", 30)
                val limitMs = TimeUnit.MINUTES.toMillis(offMinutes.toLong())

                if (accumulatedMs >= limitMs) {
                    val waitMinutes = prefs.getInt("wait_minutes", 15)
                    lockUntil = now + TimeUnit.MINUTES.toMillis(waitMinutes.toLong())
                    accumulatedMs = 0
                    prefs.edit().putLong("lock_until", lockUntil).apply()
                    checkAndLock()
                }
            }
            handler.postDelayed(this, 15_000) // cek setiap 15 detik
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        lockUntil = prefs.getLong("lock_until", 0)
        handler.post(checkRunnable)
    }

    private fun getUsageTimeLastMinutes(minutes: Int): Long {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.MINUTES.toMillis(minutes.toLong())
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        var total = 0L
        stats?.forEach { stat ->
            if (targetPackages.contains(stat.packageName)) {
                total += stat.totalTimeInForeground
            }
        }
        return total
    }

    private fun checkAndLock() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 5000
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        val current = stats?.maxByOrNull { it.lastTimeUsed }
        if (current != null && targetPackages.contains(current.packageName)) {
            // Target app sedang dibuka → tampilkan LockActivity
            val intent = Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
            .setContentText("Memantau YouTube, Facebook, TikTok")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }
}
