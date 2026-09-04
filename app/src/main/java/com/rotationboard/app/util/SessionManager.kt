package com.rotationboard.app.util

import android.content.Context

object SessionManager {
    private const val PREFS = "session_prefs"
    private const val KEY_USER_ID = "logged_in_user_id"
    private const val KEY_USERNAME = "logged_in_username"

    fun saveSession(context: Context, userId: Long, username: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun getUserId(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_USER_ID, -1L)
    }

    fun getUsername(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USERNAME, null)
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun isLoggedIn(context: Context): Boolean = getUserId(context) != -1L
}
