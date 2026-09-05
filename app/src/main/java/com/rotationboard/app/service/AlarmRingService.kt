package com.rotationboard.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rotationboard.app.App
import com.rotationboard.app.R
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.ui.AlarmActivity
import com.rotationboard.app.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// This is what makes it a REAL alarm rather than a notification: the sound is
// played on the STREAM_ALARM audio channel, which ignores silent mode/DND the
// same way the phone's built-in Clock app does, and it loops until dismissed.
//
// Every step below is independently wrapped so that a failure in one part
// (e.g. no default alarm ringtone set on the device) can never silently
// prevent the other parts (vibration, the visible notification) from firing.
class AlarmRingService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarm()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                val accountId = intent.getLongExtra("accountId", -1)
                snooze(accountId)
                return START_NOT_STICKY
            }
        }

        val email = intent?.getStringExtra("email") ?: "Account"
        val project = intent?.getStringExtra("project") ?: ""
        val accountId = intent?.getLongExtra("accountId", -1) ?: -1L

        showNotificationAndFullScreen(email, project, accountId)
        startAlarmSound()
        startVibration()

        return START_NOT_STICKY
    }

    private fun showNotificationAndFullScreen(email: String, project: String, accountId: Long) {
        try {
            val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
                putExtra("email", email)
                putExtra("project", project)
                putExtra("accountId", accountId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                accountId.toInt(),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, App.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("$email is ready")
                .setContentText(project)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .build()

            startForeground(1001, notification)

            // Belt-and-braces: also try to launch the full-screen activity directly.
            // On some OEM skins the full-screen-intent notification field is ignored
            // unless the user has separately granted "Display over other apps" /
            // "Full screen notifications" permission, so we don't rely on it alone.
            startActivity(fullScreenIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show alarm notification/full-screen UI", e)
        }
    }

    private fun startAlarmSound() {
        try {
            val uri: Uri? = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getValidRingtoneUri(this)

            if (uri == null) {
                Log.e(TAG, "No ringtone URI available on this device at all")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm sound", e)
        }
    }

    private fun startVibration() {
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val pattern = longArrayOf(0, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    private fun snooze(accountId: Long) {
        if (accountId == -1L) {
            stopAlarm()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getInstance(applicationContext).accountDao()
            val acc = dao.getById(accountId)
            if (acc != null) {
                val snoozed = acc.copy(endTime = System.currentTimeMillis() + SNOOZE_MS)
                dao.update(snoozed)
                AlarmScheduler.schedule(applicationContext, snoozed)
            }
            stopAlarm()
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player", e)
        }
        mediaPlayer = null
        vibrator?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { mediaPlayer?.release() } catch (e: Exception) { /* ignore */ }
        vibrator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        const val ACTION_STOP = "com.rotationboard.app.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE = "com.rotationboard.app.ACTION_SNOOZE_ALARM"
        const val SNOOZE_MS = 10 * 60 * 1000L // 10 minutes
    }
}
