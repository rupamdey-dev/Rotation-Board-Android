package com.rotationboard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Android clears all AlarmManager alarms on reboot, so we re-register every
// still-pending cooldown alarm as soon as the phone finishes booting.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val accounts = db.accountDao().getAllScheduled()
                accounts.forEach { acc ->
                    if ((acc.endTime ?: 0L) > System.currentTimeMillis()) {
                        AlarmScheduler.schedule(context, acc)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
