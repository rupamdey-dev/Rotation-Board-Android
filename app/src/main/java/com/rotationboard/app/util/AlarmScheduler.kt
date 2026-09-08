package com.rotationboard.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rotationboard.app.data.AccountEntity
import com.rotationboard.app.receiver.AlarmReceiver
import com.rotationboard.app.ui.DashboardActivity

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
    }
}
