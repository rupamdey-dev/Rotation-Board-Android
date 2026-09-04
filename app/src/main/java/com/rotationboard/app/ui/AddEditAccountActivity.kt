package com.rotationboard.app.ui

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rotationboard.app.data.AccountEntity
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.databinding.ActivityAddEditAccountBinding
import com.rotationboard.app.util.AlarmScheduler
import com.rotationboard.app.util.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddEditAccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddEditAccountBinding
    private var editingAccount: AccountEntity? = null
    private var pickedHour = 13
    private var pickedMinute = 0
    private var mode = "hours" // or "time"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val accountId = intent.getLongExtra("accountId", -1L)

        binding.btnModeHours.setOnClickListener { setMode("hours") }
        binding.btnModeTime.setOnClickListener { setMode("time") }
        binding.btnPickTime.setOnClickListener { showTimePicker() }
        setMode("hours")
        updatePickTimeLabel()

        if (accountId != -1L) {
            binding.tvTitle.text = "Edit account"
            lifecycleScope.launch {
                val acc = AppDatabase.getInstance(applicationContext).accountDao().getById(accountId)
                acc?.let { fillForEdit(it) }
            }
        } else {
            binding.tvTitle.text = "Add account"
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun fillForEdit(acc: AccountEntity) {
        editingAccount = acc
        binding.etEmail.setText(acc.email)
        binding.etProject.setText(acc.project)
        binding.etHours.setText(acc.timerHours.toString())
        if (acc.timerMode == "time" && acc.timerTimeStr.isNotEmpty()) {
            val parts = acc.timerTimeStr.split(":")
            pickedHour = parts[0].toInt()
            pickedMinute = parts[1].toInt()
            setMode("time")
            updatePickTimeLabel()
        }
    }

    private fun setMode(m: String) {
        mode = m
        val isHours = m == "hours"
        binding.tilHours.visibility = if (isHours) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnPickTime.visibility = if (isHours) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnModeHours.isSelected = isHours
        binding.btnModeTime.isSelected = !isHours
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                pickedHour = hour
                pickedMinute = minute
                updatePickTimeLabel()
            },
            pickedHour,
            pickedMinute,
            false
        ).show()
    }

    private fun updatePickTimeLabel() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, pickedHour)
            set(Calendar.MINUTE, pickedMinute)
        }
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        binding.btnPickTime.text = "Pick time: ${sdf.format(cal.time)}"
    }

    private fun computeEndTime(): Long {
        if (mode == "time") {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, pickedHour)
            cal.set(Calendar.MINUTE, pickedMinute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1) // next occurrence of that clock time
            }
            return cal.timeInMillis
        }
        val hours = binding.etHours.text.toString().toFloatOrNull()?.takeIf { it > 0 } ?: 5f
        return System.currentTimeMillis() + (hours * 3600 * 1000).toLong()
    }

    private fun save() {
        val email = binding.etEmail.text.toString().trim()
        val project = binding.etProject.text.toString().trim().ifEmpty { "Untagged" }
        if (email.isEmpty()) {
            binding.etEmail.error = "Required"
            return
        }
        val hours = binding.etHours.text.toString().toFloatOrNull() ?: 5f
        val timeStr = if (mode == "time") String.format(Locale.US, "%02d:%02d", pickedHour, pickedMinute) else ""
        val endTime = computeEndTime()
        val userId = SessionManager.getUserId(this)

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).accountDao()
            val existing = editingAccount

            val savedId: Long
            val finalAccount: AccountEntity

            if (existing != null) {
                val updated = existing.copy(
                    email = email,
                    project = project,
                    timerMode = mode,
                    timerHours = hours,
                    timerTimeStr = timeStr,
                    endTime = endTime
                )
                dao.update(updated)
                savedId = updated.id
                finalAccount = updated
            } else {
                val newAccount = AccountEntity(
                    userId = userId,
                    email = email,
                    project = project,
                    timerMode = mode,
                    timerHours = hours,
                    timerTimeStr = timeStr,
                    endTime = endTime
                )
                savedId = dao.insert(newAccount)
                finalAccount = newAccount.copy(id = savedId)
            }

            AlarmScheduler.schedule(this@AddEditAccountActivity, finalAccount)
            finish()
        }
    }
}
