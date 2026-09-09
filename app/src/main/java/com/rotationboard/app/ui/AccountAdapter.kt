package com.rotationboard.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.rotationboard.app.data.AccountEntity
import com.rotationboard.app.databinding.ItemAccountBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AccountStatus { READY, COOLING, IDLE }

fun statusOf(acc: AccountEntity): AccountStatus {
    val endTime = acc.endTime ?: return AccountStatus.IDLE
    return if (endTime > System.currentTimeMillis()) AccountStatus.COOLING else AccountStatus.READY
}

class AccountAdapter(
    private val onEdit: (AccountEntity) -> Unit,
    private val onDelete: (AccountEntity) -> Unit,
    private val onSetTime: (AccountEntity) -> Unit,
    private val onVoiceSetTime: (AccountEntity) -> Unit
) : RecyclerView.Adapter<AccountAdapter.VH>() {

    private var items: List<AccountEntity> = emptyList()

    fun submitList(list: List<AccountEntity>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class VH(private val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(acc: AccountEntity) {
            binding.tvEmail.text = acc.email
            binding.tvProject.text = acc.project

            when (statusOf(acc)) {
                AccountStatus.IDLE -> {
                    binding.tvTimer.text = "IDLE"
                    binding.tvReadyBy.text = ""
                    binding.btnPrimary.text = "Start"
                }
                AccountStatus.COOLING -> {
                    val endTime = acc.endTime!!
                    binding.tvTimer.text = formatDuration(endTime - System.currentTimeMillis())
                    binding.tvReadyBy.text = "Ready at ${formatClock(endTime)}"
                    binding.btnPrimary.text = "Edit time"
                }
                AccountStatus.READY -> {
                    binding.tvTimer.text = "READY"
                    binding.tvReadyBy.text = "Was ready at ${formatClock(acc.endTime!!)}"
                    binding.btnPrimary.text = "Start again"
                }
            }

            binding.btnPrimary.setOnClickListener { onSetTime(acc) }
            binding.btnMic.setOnClickListener { onVoiceSetTime(acc) }
            binding.btnEdit.setOnClickListener { onEdit(acc) }
            binding.btnDelete.setOnClickListener { onDelete(acc) }
            binding.btnCopy.setOnClickListener { copyToClipboard(acc.email) }
        }

        private fun copyToClipboard(text: String) {
            val ctx = binding.root.context
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("email", text))
            Toast.makeText(ctx, "Copied $text", Toast.LENGTH_SHORT).show()
        }

        private fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val h = TimeUnit.SECONDS.toHours(totalSec)
            val m = TimeUnit.SECONDS.toMinutes(totalSec) % 60
            val s = totalSec % 60
            return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        }

        private fun formatClock(ts: Long): String {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(ts))
        }
    }
}
