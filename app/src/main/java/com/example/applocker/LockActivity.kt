package com.example.applocker

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        val tv = findViewById<TextView>(R.id.tvCountdown)
        val prefs = getSharedPreferences("locker_prefs", MODE_PRIVATE)
        val lockUntil = prefs.getLong("lock_until", 0)
        val remaining = lockUntil - System.currentTimeMillis()

        if (remaining <= 0) {
            finish()
            return
        }

        object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val min = millisUntilFinished / 60000
                val sec = (millisUntilFinished % 60000) / 1000
                tv.text = "Aplikasi terkunci\n\nSisa waktu: %02d:%02d".format(min, sec)
            }
            override fun onFinish() {
                finish()
            }
        }.start()
    }

    // Cegah tombol back
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // tidak melakukan apa-apa
    }
}
