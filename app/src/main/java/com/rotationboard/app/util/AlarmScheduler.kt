package com.rotationboard.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rotationboard.app.data.AccountEntity
import com.rotationboard.app.receiver.AlarmReceiver
import com.rotationboard.app.ui.DashboardActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmScheduler {

    fun schedule(context: Context, account: AccountEntity) {
        val endTime = account.endTime ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("accountId", account.id)
            putExtra("email", account.email)
            putExtra("project", account.project)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            account.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = PendingIntent.getActivity(
            context,
            account.id.toInt(),
            Intent(context, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setAlarmClock is the API real alarm-clock apps use: it survives Doze/battery
        // optimization and fires at the exact time without needing special permissions.
        am.setAlarmClock(AlarmManager.AlarmClockInfo(endTime, showIntent), pi)

        val timeStr = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(endTime))
        var msg = "SCHEDULED: id=${account.id} ${account.email} for $timeStr"

        // Log the actual OS-reported permission state, since this can reveal
        // a silent block even when setAlarmClock() itself doesn't throw.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val canExact = am.canScheduleExactAlarms()
            msg += " | canScheduleExactAlarms=$canExact"
        }
        DebugLog.add(context, msg)
    }

    fun cancel(context: Context, accountId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            accountId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        DebugLog.add(context, "CANCELLED: id=$accountId")
    }
}
