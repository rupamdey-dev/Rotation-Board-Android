package com.rotationboard.app.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Safety net: runs roughly every 15 minutes (the shortest interval Android
// allows for periodic background work) and fires any account whose cooldown
// has finished but whose alarm never actually rang — e.g. because the OS or
// phone manufacturer silently killed the exact AlarmManager broadcast. This
// won't catch things instantly, but it guarantees you're never left waiting
// more than ~15 minutes with no alert at all.
//
// Logs on EVERY run (not just when it finds something overdue) so the debug
// log can confirm whether this worker is even being allowed to execute
// periodically on this specific device at all.
class AlarmCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val timeStr = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val now = System.currentTimeMillis()
            val overdue = db.accountDao().getOverdueUnrung(now)

            DebugLog.add(applicationContext, "WORKER RAN at $timeStr, found ${overdue.size} overdue-unrung")

            overdue.forEach { acc ->
                Log.d(TAG, "Backup worker firing missed alarm for ${acc.email}")
                DebugLog.add(applicationContext, "WORKER: firing missed alarm for id=${acc.id} ${acc.email}")
                val intent = Intent(applicationContext, AlarmReceiver::class.java).apply {
                    putExtra("accountId", acc.id)
                    putExtra("email", acc.email)
                    putExtra("project", acc.project)
                }
                applicationContext.sendBroadcast(intent)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup worker failed", e)
            DebugLog.add(applicationContext, "WORKER ERROR at $timeStr: ${e.javaClass.simpleName}: ${e.message}")
            Result.retry()
        } finally {
            com.rotationboard.app.widget.WidgetUpdater.requestUpdate(applicationContext)
        }
    }

    companion object {
        private const val TAG = "AlarmCheckWorker"
    }
}
