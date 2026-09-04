package com.rotationboard.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rotationboard.app.databinding.ActivityAlarmBinding
import com.rotationboard.app.service.AlarmRingService

// Shown full-screen, over the lock screen, exactly like the phone's native
// alarm-clock ringing screen. Dismiss stops the looping alarm sound/vibration.
class AlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val email = intent.getStringExtra("email") ?: ""
        val project = intent.getStringExtra("project") ?: ""

        binding.tvEmail.text = email
        binding.tvProject.text = project

        binding.btnDismiss.setOnClickListener {
            stopAlarmService()
            finish()
        }
    }

    private fun stopAlarmService() {
        val stopIntent = Intent(this, AlarmRingService::class.java).apply {
            action = AlarmRingService.ACTION_STOP
        }
        startService(stopIntent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Treat back-press as dismiss too, so the alarm can't keep ringing silently in the background.
        stopAlarmService()
        super.onBackPressed()
    }
}
