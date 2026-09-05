package com.rotationboard.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rotationboard.app.databinding.ActivityAlarmBinding
import com.rotationboard.app.service.AlarmRingService

// Shown full-screen, over the lock screen, exactly like the phone's native
// alarm-clock ringing screen. Dismiss stops the looping alarm sound/vibration.
// Snooze stops it too, but reschedules the same account for 10 minutes later.
class AlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmBinding
    private var accountId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val email = intent.getStringExtra("email") ?: ""
        val project = intent.getStringExtra("project") ?: ""
        accountId = intent.getLongExtra("accountId", -1L)

        binding.tvEmail.text = email
        binding.tvProject.text = project

        binding.btnDismiss.setOnClickListener {
            sendServiceAction(AlarmRingService.ACTION_STOP)
            finish()
        }

        binding.btnSnooze.setOnClickListener {
            sendServiceAction(AlarmRingService.ACTION_SNOOZE)
            finish()
        }
    }

    private fun sendServiceAction(action: String) {
        val serviceIntent = Intent(this, AlarmRingService::class.java).apply {
            this.action = action
            putExtra("accountId", accountId)
        }
        startService(serviceIntent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Treat back-press as dismiss too, so the alarm can't keep ringing silently in the background.
        sendServiceAction(AlarmRingService.ACTION_STOP)
        super.onBackPressed()
    }
}
