package com.example.applocker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerOff: Spinner
    private lateinit var spinnerWait: Spinner
    private lateinit var btnToggle: Button
    private lateinit var tvUsage: TextView

    private val prefs by lazy { getSharedPreferences("locker_prefs", MODE_PRIVATE) }
    private val PIN = "1234"
    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUsageText()
            handler.postDelayed(this, 150)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerOff = findViewById(R.id.spinnerOff)
        spinnerWait = findViewById(R.id.spinnerWait)
        btnToggle = findViewById(R.id.btnToggle)
        tvUsage = findViewById(R.id.tvUsage)

        val offOptions = arrayOf("20 menit", "30 menit")
        val waitOptions = arrayOf("15 menit", "20 menit")
        spinnerOff.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, offOptions)
        spinnerWait.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, waitOptions)

        spinnerOff.setSelection(prefs.getInt("off_timer", 0))
        spinnerWait.setSelection(prefs.getInt("wait_timer", 0))

        updateButton()
        updateSpinnersEnabled()
        updateUsageText()

        btnToggle.setOnClickListener {
            if (isActive()) {
                showPinDialog()
            } else {
                if (!hasUsageAccess()) {
                    requestUsageAccess()
                    return@setOnClickListener
                }
                if (!Settings.canDrawOverlays(this)) {
                    requestOverlayPermission()
                    return@setOnClickListener
                }

                saveSettings()
                prefs.edit().putBoolean("active", true).apply()
                startMonitorService()
                updateButton()
                updateSpinnersEnabled()
                Toast.makeText(this, "Mode penguncian AKTIF - Timer mulai berjalan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isActive() = prefs.getBoolean("active", false)

    private fun updateButton() {
        if (isActive()) {
            btnToggle.text = "NONAKTIFKAN (Merah = Aktif)"
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            btnToggle.text = "AKTIFKAN"
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }
    }

    private fun updateSpinnersEnabled() {
        val enabled = !isActive()
        spinnerOff.isEnabled = enabled
        spinnerWait.isEnabled = enabled
        spinnerOff.alpha = if (enabled) 1f else 0.5f
        spinnerWait.alpha = if (enabled) 1f else 0.5f
    }

    private fun updateUsageText() {
        if (!isActive()) {
            tvUsage.text = "Status: Nonaktif"
            return
        }

        val offMinutes = prefs.getInt("off_minutes", 20)
        val limitMs = offMinutes * 60 * 1000L
        val accumulated = prefs.getLong("accumulated", 0)
        val remainingMs = (limitMs - accumulated).coerceAtLeast(0)

        val lockUntil = prefs.getLong("lock_until", 0)
        val now = System.currentTimeMillis()

        if (now < lockUntil) {
            val sisa = lockUntil - now
            val m = (sisa / 60000).toInt()
            val s = ((sisa % 60000) / 1000).toInt()
            val ms = (sisa % 1000).toInt()
            tvUsage.text = "SEDANG DIKUNCI\n%02d:%02d:%03d".format(m, s, ms)
        } else {
            val m = (remainingMs / 60000).toInt()
            val s = ((remainingMs % 60000) / 1000).toInt()
            val ms = (remainingMs % 1000).toInt()
            tvUsage.text = "Waktu tersisa %02d:%02d:%03d dari %d menit".format(m, s, ms, offMinutes)
        }
    }

    private fun saveSettings() {
        val offMin = if (spinnerOff.selectedItemPosition == 0) 20 else 30
        val waitMin = if (spinnerWait.selectedItemPosition == 0) 15 else 20
        prefs.edit()
            .putInt("off_timer", spinnerOff.selectedItemPosition)
            .putInt("wait_timer", spinnerWait.selectedItemPosition)
            .putInt("off_minutes", offMin)
            .putInt("wait_minutes", waitMin)
            .apply()
    }

    private fun showPinDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("Masukkan Kode PIN")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == PIN) {
                    prefs.edit().putBoolean("active", false).apply()
                    stopService(Intent(this, MonitorService::class.java))
                    updateButton()
                    updateSpinnersEnabled()
                    updateUsageText()
                    Toast.makeText(this, "Mode penguncian DINONAKTIFKAN", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN salah!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageAccess() {
        Toast.makeText(this, "Aktifkan izin Usage Access", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "Izinkan tampil di atas aplikasi lain", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun startMonitorService() {
        val intent = Intent(this, MonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onResume() {
        super.onResume()
        updateButton()
        updateSpinnersEnabled()
        updateUsageText()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }
}
