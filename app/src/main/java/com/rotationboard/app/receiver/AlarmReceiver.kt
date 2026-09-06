package com.rotationboard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.service.AlarmRingService
import com.rotationboard.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val email = intent.getStringExtra("email") ?: ""
        val accountId = intent.getLongExtra("accountId", -1)
        DebugLog.add(context, "RECEIVER FIRED: id=$accountId $email")
        Log.d(TAG, "Alarm fired, waking device and starting ring service")

        // Grab a short wake lock so the CPU can't go back to sleep between
        // this broadcast and the foreground service actually starting to
        // play sound — on some phones there's just enough of a gap for that
        // to silently happen otherwise.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RotationBoard:AlarmWakeLock"
        )
        wakeLock.acquire(20_000L) // auto-releases after 20s as a safety net regardless

        val svcIntent = Intent(context, AlarmRingService::class.java).apply {
            putExtra("accountId", accountId)
            putExtra("email", email)
            putExtra("project", intent.getStringExtra("project"))
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent)
            } else {
                context.startService(svcIntent)
            }
            DebugLog.add(context, "RECEIVER: startForegroundService() call succeeded (no exception)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AlarmRingService", e)
            DebugLog.add(context, "RECEIVER ERROR: startForegroundService failed: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }

        // Mark this alarm as rung so the backup periodic worker (see
        // AlarmCheckWorker) doesn't fire it again a second time.
        if (accountId != -1L) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDatabase.getInstance(context).accountDao().markRung(accountId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to mark account rung", e)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
