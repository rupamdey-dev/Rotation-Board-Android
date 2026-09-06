package com.rotationboard.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.databinding.ActivityDebugLogBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDebugLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRefreshLog.setOnClickListener { loadLog() }
        binding.btnCopyLog.setOnClickListener { copyLog() }
        binding.btnClearLog.setOnClickListener { confirmClear() }

        loadLog()
    }

    private fun loadLog() {
        lifecycleScope.launch {
            val entries = AppDatabase.getInstance(applicationContext).debugLogDao().getRecent()
            if (entries.isEmpty()) {
                binding.tvLog.text = "No log entries yet. Add an account and wait for its timer, then check back here."
                return@launch
            }
            val sdf = SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault())
            val text = entries.joinToString("\n\n") { "${sdf.format(Date(it.timestamp))}\n${it.message}" }
            binding.tvLog.text = text
        }
    }

    private fun copyLog() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("debug log", binding.tvLog.text.toString()))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear debug log?")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.getInstance(applicationContext).debugLogDao().clear()
                    loadLog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
