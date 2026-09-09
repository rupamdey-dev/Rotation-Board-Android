package com.rotationboard.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rotationboard.app.data.AccountEntity
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.databinding.ActivityDashboardBinding
import com.rotationboard.app.util.AlarmScheduler
import com.rotationboard.app.util.AppLockManager
import com.rotationboard.app.util.SessionManager
import com.rotationboard.app.util.VoiceTimeParser
import com.rotationboard.app.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.Executor

// This is the app's home screen: just the list of accounts/projects/timers.
// Everything else (alarm sound, app lock toggle, battery/autostart fixes,
// test alarm) lives in SettingsActivity, reached via the Settings button.
class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var adapter: AccountAdapter
    private var allAccounts: List<AccountEntity> = emptyList()
    private var searchQuery: String = ""
    private var pendingVoiceAccountId: Long? = null

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

    private val micPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchVoiceRecognizer() else Toast.makeText(this, "Microphone permission is needed for voice input", Toast.LENGTH_SHORT).show()
    }

    private val voiceRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val accountId = pendingVoiceAccountId
        pendingVoiceAccountId = null
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (accountId == null || text == null) return@registerForActivityResult

        val parsed = VoiceTimeParser.parse(text)
        if (parsed == null) {
            Toast.makeText(this, "Couldn't understand \"$text\" as a time — try again", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        applyVoiceTime(accountId, parsed.first, parsed.second, text)
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
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        adapter = AccountAdapter(
            onEdit = { acc -> openEdit(acc) },
            onDelete = { acc -> confirmDelete(acc) },
            onSetTime = { acc -> openEdit(acc) },
            onVoiceSetTime = { acc -> startVoiceSetTime(acc) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditAccountActivity::class.java))
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnUnlock.setOnClickListener { showBiometricPrompt() }

        requestNotifPermissionIfNeeded()

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
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    // ---------- Voice time input ----------

    private fun startVoiceSetTime(acc: AccountEntity) {
        pendingVoiceAccountId = acc.id
        val hasMicPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMicPerm) {
            launchVoiceRecognizer()
        } else {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchVoiceRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a time, e.g. \"nine forty five pm\"")
        }
        try {
            voiceRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input isn't available on this device", Toast.LENGTH_SHORT).show()
            pendingVoiceAccountId = null
        }
    }

    private fun applyVoiceTime(accountId: Long, hour: Int, minute: Int, heardText: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).accountDao()
            val acc = dao.getById(accountId) ?: return@launch

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1) // next occurrence of that clock time
            }

            val timeStr = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
            val updated = acc.copy(
                timerMode = "time",
                timerTimeStr = timeStr,
                endTime = cal.timeInMillis,
                rung = false
            )
            dao.update(updated)
            AlarmScheduler.schedule(this@DashboardActivity, updated)
            WidgetUpdater.requestUpdate(applicationContext)

            val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            Toast.makeText(
                this@DashboardActivity,
                "✅ ${acc.email} set for ${sdf.format(cal.time)} (heard: \"$heardText\")",
                Toast.LENGTH_LONG
            ).show()
        }
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

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
        checkLockAndShowIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }
}
