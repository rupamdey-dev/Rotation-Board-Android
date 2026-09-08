package com.rotationboard.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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
import com.rotationboard.app.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

// This is the app's home screen: just the list of accounts/projects/timers.
// Everything else (alarm sound, app lock toggle, battery/autostart fixes,
// test alarm) lives in SettingsActivity, reached via the Settings button.
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
            onSetTime = { acc -> openEdit(acc) }
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
