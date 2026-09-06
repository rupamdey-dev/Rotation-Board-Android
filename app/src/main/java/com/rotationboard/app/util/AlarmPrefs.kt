package com.rotationboard.app.util

import android.content.Context
import android.net.Uri

object AlarmPrefs {
    private const val PREFS = "alarm_prefs"
    private const val KEY_SOUND_URI = "custom_sound_uri"

    fun getCustomSoundUri(context: Context): Uri? {
        val str = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SOUND_URI, null)
        return str?.let { Uri.parse(it) }
    }

    fun setCustomSoundUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SOUND_URI, uri?.toString())
            .apply()
    }
}
