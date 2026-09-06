package com.rotationboard.app.util

import android.content.Context
import android.util.Log
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.data.DebugLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// A visible-in-app log of every step of the alarm pipeline (scheduled, fired,
// backup-checked, rang) so problems can be diagnosed from the phone itself —
// no computer, no adb, no logcat access needed. View it via the "Debug log"
// button on the dashboard, and use "Copy all" to paste it elsewhere.
object DebugLog {
    fun add(context: Context, message: String) {
        // Also mirror to logcat for anyone who does have adb access.
        Log.d("RotationBoard", message)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(context).debugLogDao().insert(
                    DebugLogEntity(timestamp = System.currentTimeMillis(), message = message)
                )
            } catch (e: Exception) {
                Log.e("DebugLog", "Failed to write debug log entry", e)
            }
        }
    }
}
