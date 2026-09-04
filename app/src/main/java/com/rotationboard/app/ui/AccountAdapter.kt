package com.rotationboard.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rotationboard.app.data.AccountEntity
import com.rotationboard.app.databinding.ItemAccountBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AccountAdapter(
    private val onEdit: (AccountEntity) -> Unit,
    private val onDelete: (AccountEntity) -> Unit,
    private val onSetTime: (AccountEntity) -> Unit
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

            val now = System.currentTimeMillis()
            val endTime = acc.endTime
            when {
                endTime == null -> {
                    binding.tvTimer.text = "IDLE"
                    binding.tvReadyBy.text = ""
                    binding.btnPrimary.text = "Start"
                }
                endTime > now -> {
                    binding.tvTimer.text = formatDuration(endTime - now)
                    binding.tvReadyBy.text = "Ready at ${formatClock(endTime)}"
                    binding.btnPrimary.text = "Edit time"
                }
                else -> {
                    binding.tvTimer.text = "READY"
                    binding.tvReadyBy.text = "Was ready at ${formatClock(endTime)}"
                    binding.btnPrimary.text = "Start again"
                }
            }

            binding.btnPrimary.setOnClickListener { onSetTime(acc) }
            binding.btnEdit.setOnClickListener { onEdit(acc) }
            binding.btnDelete.setOnClickListener { onDelete(acc) }
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
