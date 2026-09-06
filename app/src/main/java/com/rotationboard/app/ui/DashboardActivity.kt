package com.rotationboard.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rotationboard.app.data.AccountEntity
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.databinding.ActivityDashboardBinding
import com.rotationboard.app.util.AlarmScheduler
import com.rotationboard.app.util.SessionManager
import kotlinx.coroutines.launch

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

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnFixBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }

        requestNotifPermissionIfNeeded()
        updateBatteryBanner()

        val db = AppDatabase.getInstance(applicationContext)
        val userId = SessionManager.getUserId(this)
        lifecycleScope.launch {
            db.accountDao().observeForUser(userId).collect { list ->
                allAccounts = list
                applyFilterAndSort()
            }
        }
    }

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
        // (AccountStatus is declared READY, COOLING, IDLE, so its ordinal already
        // matches this priority order.)
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
            // Some OEM ROMs block this screen; fall back to the general battery settings page.
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
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
        updateBatteryBanner()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }
}
