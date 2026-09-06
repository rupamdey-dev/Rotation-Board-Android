package com.rotationboard.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

// Many Android manufacturers (Xiaomi/MIUI, Vivo, Oppo, Realme, OnePlus,
// Huawei/Honor) run their OWN background-app-killing system on top of
// standard Android, completely separate from the normal "battery
// optimization" setting. An app has no official permission it can request
// for this — the only way in is deep-linking straight to that manufacturer's
// specific settings screen, which is what this does, with a safe fallback to
// the app's general settings page if the phone isn't one of these or the
// screen has moved in a newer OS version.
object OemSettingsHelper {

    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") -> candidates.add(
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            manufacturer.contains("vivo") -> candidates.add(
                componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                candidates.add(componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
                candidates.add(componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"))
                candidates.add(componentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
            }
            manufacturer.contains("oneplus") -> candidates.add(
                componentIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> candidates.add(
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            )
            manufacturer.contains("asus") -> candidates.add(
                componentIntent("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
            )
        }

        // Fallback: the app's own info page, where the user can dig into
        // battery/background permissions manually if nothing above matched.
        candidates.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )

        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.d(TAG, "OEM settings intent failed, trying next: ${e.message}")
            }
        }
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().apply {
            component = ComponentName(pkg, cls)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private const val TAG = "OemSettingsHelper"
}
