package com.rotationboard.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rotationboard.app.data.AccountEntity
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.databinding.ActivityDashboardBinding
import com.rotationboard.app.util.AlarmPrefs
import com.rotationboard.app.util.AlarmScheduler
import com.rotationboard.app.util.AppLockManager
import com.rotationboard.app.util.OemSettingsHelper
import com.rotationboard.app.util.SessionManager
import com.rotationboard.app.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var adapter: AccountAdapter
    private var allAccounts: List<AccountEntity> = emptyList()
    private var searchQuery: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            adapter.notifyDataSetChanged()
            handler.postDelayed(this, 1000)
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op either way; UI just won't show notifications if denied */ }

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        AlarmPrefs.setCustomSoundUri(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.tvWho.text = "Logged in as ${SessionManager.getUsername(this)}"
        binding.btnLogout.setOnClickListener {
            SessionManager.clearSession(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        adapter = AccountAdapter(
            onEdit = { acc -> openEdit(acc) },
            onDelete = { acc -> confirmDelete(acc) },
            onSetTime = { acc -> openEdit(acc) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditAccountActivity::class.java))
        }

        binding.btnTestAlarm.setOnClickListener { fireTestAlarm() }
        binding.btnDebugLog.setOnClickListener {
            startActivity(Intent(this, DebugLogActivity::class.java))
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnFixBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }
        binding.btnAutostart.setOnClickListener {
            OemSettingsHelper.openAutoStartSettings(this)
        }
        binding.btnAlarmSound.setOnClickListener { openRingtonePicker() }

        setupAppLockSwitch()
        binding.btnUnlock.setOnClickListener { showBiometricPrompt() }

        requestNotifPermissionIfNeeded()
        updateBatteryBanner()

        val db = AppDatabase.getInstance(applicationContext)
        val userId = SessionManager.getUserId(this)
        lifecycleScope.launch {
            db.accountDao().observeForUser(userId).collect { list ->
                allAccounts = list
                applyFilterAndSort()
                WidgetUpdater.requestUpdate(applicationContext)
            }
        }
    }

    // ---------- App lock ----------

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

    private fun checkLockAndShowIfNeeded() {
        val shouldLock = AppLockManager.isLockEnabled(this) && !AppLockManager.isUnlockedThisSession
        binding.lockOverlay.visibility = if (shouldLock) android.view.View.VISIBLE else android.view.View.GONE
        if (shouldLock) showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLockManager.isUnlockedThisSession = true
                binding.lockOverlay.visibility = android.view.View.GONE
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Leave the overlay up; user can tap Unlock to retry.
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Rotation Board")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    // ---------- Alarm sound picker ----------

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

    // ---------- List filtering/sorting ----------

    private fun applyFilterAndSort() {
        val filtered = if (searchQuery.isEmpty()) {
            allAccounts
        } else {
            allAccounts.filter {
                it.email.contains(searchQuery, ignoreCase = true) ||
                    it.project.contains(searchQuery, ignoreCase = true)
            }
        }

        // Ready accounts first (they need action), then cooling ones soonest-first,
        // then idle ones — so the thing you're most likely to act on is always on top.
        val sorted = filtered.sortedWith(
            compareBy(
                { statusOf(it).ordinal },
                { it.endTime ?: Long.MAX_VALUE }
            )
        )

        adapter.submitList(sorted)
        updateEmptyState(sorted.isEmpty())
        updateSummary()
    }

    private fun updateSummary() {
        val ready = allAccounts.count { statusOf(it) == AccountStatus.READY }
        val cooling = allAccounts.count { statusOf(it) == AccountStatus.COOLING }
        val idle = allAccounts.count { statusOf(it) == AccountStatus.IDLE }
        binding.tvSummary.text = "$ready ready · $cooling cooling · $idle idle"
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.tvEmpty.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
    }

    // ---------- Permissions / battery / autostart ----------

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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

    private fun openEdit(acc: AccountEntity) {
        val intent = Intent(this, AddEditAccountActivity::class.java).apply {
            putExtra("accountId", acc.id)
        }
        startActivity(intent)
    }

    private fun confirmDelete(acc: AccountEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete account?")
            .setMessage("${acc.email} (${acc.project}) will be removed from your board.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    AlarmScheduler.cancel(this@DashboardActivity, acc.id)
                    AppDatabase.getInstance(applicationContext).accountDao().delete(acc)
                    WidgetUpdater.requestUpdate(applicationContext)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
        updateBatteryBanner()
        checkLockAndShowIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }
}
