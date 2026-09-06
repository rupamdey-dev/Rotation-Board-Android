package com.rotationboard.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
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
import com.rotationboard.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// This is what makes it a REAL alarm rather than a notification: the sound is
// played on the STREAM_ALARM audio channel (ignores silent mode/DND, same as
// the phone's Clock app) using a sound file bundled INSIDE the app, so it can
// never silently fail because of a missing/null system ringtone on some device.
//
// Every step below is independently wrapped so a failure in one part can
// never silently prevent the other parts (vibration, the visible notification)
// from firing.
class AlarmRingService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var originalAlarmVolume: Int? = null

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

        Log.d(TAG, "Ringing for $email / $project")
        DebugLog.add(this, "SERVICE onStartCommand: ringing for id=$accountId $email")

        showNotificationAndFullScreen(email, project, accountId)
        boostAlarmVolumeIfMuted()
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
            DebugLog.add(this, "SERVICE: startForeground() succeeded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post foreground notification", e)
            DebugLog.add(this, "SERVICE ERROR: startForeground failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Belt-and-braces: also try to launch the full-screen activity directly,
        // independent of the notification's full-screen-intent field, since some
        // OEM skins ignore that field unless a separate permission is granted.
        try {
            val directIntent = Intent(this, AlarmActivity::class.java).apply {
                putExtra("email", email)
                putExtra("project", project)
                putExtra("accountId", accountId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(directIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch full-screen alarm activity directly", e)
        }
    }

    private fun boostAlarmVolumeIfMuted() {
        // A very common reason "the alarm doesn't ring" on Android: phones have a
        // SEPARATE alarm-volume slider from the ringer/media volume, and it's easy
        // to have it sitting at zero without realizing. We temporarily raise it for
        // this ring, then restore whatever it was once dismissed/snoozed.
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (current <= 0) {
                originalAlarmVolume = current
                val target = (max * 0.7).toInt().coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
                Log.d(TAG, "Alarm stream was muted (0/$max) — temporarily raised to $target")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check/boost alarm volume", e)
        }
    }

    private fun restoreAlarmVolume() {
        try {
            val orig = originalAlarmVolume ?: return
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, orig, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore alarm volume", e)
        } finally {
            originalAlarmVolume = null
        }
    }

    private fun startAlarmSound() {
        // Primary source: the sound file bundled inside the app (res/raw/alarm_sound.wav).
        // This can never be null/missing the way a system ringtone URI sometimes is.
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound, buildAlarmAttributes(), 0)
            if (mediaPlayer != null) {
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
                Log.d(TAG, "Playing bundled alarm sound")
                DebugLog.add(this, "SERVICE: bundled sound started OK")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bundled alarm sound failed, falling back to system ringtone", e)
            DebugLog.add(this, "SERVICE ERROR: bundled sound failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Fallback: try the system's alarm/notification/ringtone in that order.
        try {
            val uri: Uri? = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getValidRingtoneUri(this)

            if (uri == null) {
                Log.e(TAG, "No system ringtone URI available either — no sound source at all")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(buildAlarmAttributes())
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Fallback MediaPlayer error: what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback system ringtone also failed", e)
        }
    }

    private fun buildAlarmAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

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
                val snoozed = acc.copy(endTime = System.currentTimeMillis() + SNOOZE_MS, rung = false)
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
        restoreAlarmVolume()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { mediaPlayer?.release() } catch (e: Exception) { /* ignore */ }
        vibrator?.cancel()
        restoreAlarmVolume()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        const val ACTION_STOP = "com.rotationboard.app.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE = "com.rotationboard.app.ACTION_SNOOZE_ALARM"
        const val SNOOZE_MS = 10 * 60 * 1000L // 10 minutes
    }
}
