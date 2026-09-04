package com.rotationboard.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        requestNotifPermissionIfNeeded()

        val db = AppDatabase.getInstance(applicationContext)
        val userId = SessionManager.getUserId(this)
        lifecycleScope.launch {
            db.accountDao().observeForUser(userId).collect { list ->
                adapter.submitList(list)
                updateEmptyState(list.isEmpty())
            }
        }
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
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }
}
