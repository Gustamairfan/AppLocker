package com.example.applocker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerOff: Spinner
    private lateinit var spinnerWait: Spinner
    private lateinit var btnToggle: Button
    private val prefs by lazy { getSharedPreferences("locker_prefs", MODE_PRIVATE) }
    private val PIN = "1234"   // Ganti PIN di sini jika mau

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerOff = findViewById(R.id.spinnerOff)
        spinnerWait = findViewById(R.id.spinnerWait)
        btnToggle = findViewById(R.id.btnToggle)

        // Isi spinner
        val offOptions = arrayOf("20 menit", "30 menit")
        val waitOptions = arrayOf("15 menit", "20 menit")
        spinnerOff.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, offOptions)
        spinnerWait.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, waitOptions)

        // Load setting sebelumnya
        spinnerOff.setSelection(prefs.getInt("off_timer", 1))
        spinnerWait.setSelection(prefs.getInt("wait_timer", 0))
        updateButton()

        btnToggle.setOnClickListener {
            if (isActive()) {
                // Minta PIN untuk menonaktifkan
                showPinDialog()
            } else {
                // Aktifkan
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
                Toast.makeText(this, "Mode penguncian AKTIF", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, "Aktifkan izin Usage Access untuk App Locker", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "Izinkan tampil di atas aplikasi lain", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startMonitorService() {
        val intent = Intent(this, MonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onResume() {
        super.onResume()
        updateButton()
    }
}
