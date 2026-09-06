package com.rotationboard.app.util

import android.content.Context

// Tracks whether app lock is enabled (persisted) and whether the user has
// already unlocked during this app "session" (in memory only — resets
// whenever the whole app process goes to the background, via the
// ProcessLifecycleObserver registered in App.kt).
object AppLockManager {
    private const val PREFS = "lock_prefs"
    private const val KEY_ENABLED = "enabled"

    @Volatile
    var isUnlockedThisSession: Boolean = false

    fun isLockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setLockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (!enabled) isUnlockedThisSession = true // nothing to lock, treat as unlocked
    }

    fun onAppBackgrounded() {
        isUnlockedThisSession = false
    }
}
