package com.example.applocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.TimeUnit

class AppMonitorService : AccessibilityService() {

    private val targetPackages = setOf(
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.aweme"
    )

    private val prefs by lazy { getSharedPreferences("locker_prefs", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())

    private var accumulatedMs = 0L
    private var lockUntil = 0L
    private var lastTick = 0L
    private var isTargetActive = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!prefs.getBoolean("active", false)) {
                handler.removeCallbacks(this)
                return
            }

            val now = System.currentTimeMillis()

            if (now < lockUntil) {
                // Masih dikunci
                if (isTargetActive) {
                    val intent = Intent(this@AppMonitorService, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                }
                handler.postDelayed(this, 1000)
                return
            }

            if (isTargetActive) {
                val current = SystemClock.elapsedRealtime()
                if (lastTick > 0) {
                    val elapsed = current - lastTick
                    if (elapsed in 1..3000) {
                        accumulatedMs += elapsed
                        prefs.edit().putLong("accumulated", accumulatedMs).apply()
                    }
                }
                lastTick = current

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
                }
            } else {
                lastTick = 0L
            }

            handler.postDelayed(this, 300)
        }
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info

        lockUntil = prefs.getLong("lock_until", 0)
        accumulatedMs = prefs.getLong("accumulated", 0)
        handler.post(tickRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        isTargetActive = targetPackages.contains(pkg)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }
}
