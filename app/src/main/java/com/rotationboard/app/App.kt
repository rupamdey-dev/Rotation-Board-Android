package com.rotationboard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rotationboard.app.util.AlarmCheckWorker
import com.rotationboard.app.util.AppLockManager
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Account ready alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a Gmail account's cooldown finishes"
                enableVibration(true)
                setSound(alarmSound, attrs)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val workRequest = PeriodicWorkRequestBuilder<AlarmCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "alarm_check_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Whole app (not just one activity) left the foreground — require
                // re-authentication next time it's opened, if app lock is enabled.
                AppLockManager.onAppBackgrounded()
            }
        })
    }

    companion object {
        const val CHANNEL_ID = "account_ready_channel"
    }
}
