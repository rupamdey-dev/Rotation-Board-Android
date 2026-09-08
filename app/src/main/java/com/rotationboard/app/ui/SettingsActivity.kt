package com.rotationboard.app.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.rotationboard.app.databinding.ActivitySettingsBinding
import com.rotationboard.app.util.AlarmPrefs
import com.rotationboard.app.util.AppLockManager
import com.rotationboard.app.util.OemSettingsHelper

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        AlarmPrefs.setCustomSoundUri(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnFixBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }
        binding.btnAutostart.setOnClickListener { OemSettingsHelper.openAutoStartSettings(this) }
        binding.btnAlarmSound.setOnClickListener { openRingtonePicker() }
        binding.btnTestAlarm.setOnClickListener { fireTestAlarm() }

        setupAppLockSwitch()
    }

    override fun onResume() {
        super.onResume()
        updateBatteryBanner()
    }

    private fun setupAppLockSwitch() {
        binding.switchAppLock.setOnCheckedChangeListener(null)
        binding.switchAppLock.isChecked = AppLockManager.isLockEnabled(this)
        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val biometricManager = BiometricManager.from(this)
                val canAuth = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                    android.widget.Toast.makeText(
                        this,
                        "Set up a fingerprint, face unlock, or screen lock (PIN/pattern) in your phone settings first.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    binding.switchAppLock.isChecked = false
                    return@setOnCheckedChangeListener
                }
                AppLockManager.setLockEnabled(this, true)
                AppLockManager.isUnlockedThisSession = true // already "in" the app right now, don't immediately re-lock
            } else {
                AppLockManager.setLockEnabled(this, false)
            }
        }
    }

    private fun openRingtonePicker() {
        val bundledUri = Uri.parse("android.resource://$packageName/${com.rotationboard.app.R.raw.alarm_sound}")
        val existing = AlarmPrefs.getCustomSoundUri(this) ?: bundledUri
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, bundledUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose alarm sound")
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun updateBatteryBanner() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        binding.bannerBattery.visibility = if (ignoring) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) { /* give up quietly */ }
        }
    }

    private fun fireTestAlarm() {
        val serviceIntent = Intent(this, com.rotationboard.app.service.AlarmRingService::class.java).apply {
            putExtra("accountId", -1L)
            putExtra("email", "Test alarm")
            putExtra("project", "This is just a test — tap Dismiss")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
