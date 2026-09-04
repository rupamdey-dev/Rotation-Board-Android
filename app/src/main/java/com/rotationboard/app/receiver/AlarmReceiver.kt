package com.rotationboard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rotationboard.app.service.AlarmRingService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svcIntent = Intent(context, AlarmRingService::class.java).apply {
            putExtra("accountId", intent.getLongExtra("accountId", -1))
            putExtra("email", intent.getStringExtra("email"))
            putExtra("project", intent.getStringExtra("project"))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svcIntent)
        } else {
            context.startService(svcIntent)
        }
    }
}
