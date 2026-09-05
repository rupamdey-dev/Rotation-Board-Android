package com.rotationboard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.rotationboard.app.service.AlarmRingService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
            putExtra("accountId", intent.getLongExtra("accountId", -1))
            putExtra("email", intent.getStringExtra("email"))
            putExtra("project", intent.getStringExtra("project"))
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent)
            } else {
                context.startService(svcIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AlarmRingService", e)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
