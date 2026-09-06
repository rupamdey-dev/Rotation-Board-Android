package com.rotationboard.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.rotationboard.app.R
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.ui.AccountStatus
import com.rotationboard.app.ui.DashboardActivity
import com.rotationboard.app.ui.statusOf
import com.rotationboard.app.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.PendingIntent

// Home-screen widget showing what needs your attention next, without opening
// the app. Not live/per-second (Android widgets aren't meant for that — it
// would drain battery) — it refreshes whenever accounts change in the app,
// every ~30 minutes passively, and after each backup worker check.
class RotationBoardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id) }
    }

    private fun updateOne(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_rotation_board)

        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(android.R.id.background, openAppIntent)

        val userId = SessionManager.getUserId(context)
        if (userId == -1L) {
            views.setTextViewText(R.id.widgetHeadline, "Open app to log in")
            views.setTextViewText(R.id.widgetSummary, "")
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // Placeholder while the real data loads asynchronously.
        views.setTextViewText(R.id.widgetHeadline, "Loading…")
        views.setTextViewText(R.id.widgetSummary, "")
        appWidgetManager.updateAppWidget(appWidgetId, views)

        CoroutineScope(Dispatchers.IO).launch {
            val accounts = AppDatabase.getInstance(context).accountDao().getForUserOnce(userId)

            val ready = accounts.filter { statusOf(it) == AccountStatus.READY }
            val cooling = accounts.filter { statusOf(it) == AccountStatus.COOLING }
                .sortedBy { it.endTime }
            val idleCount = accounts.count { statusOf(it) == AccountStatus.IDLE }

            val headline: String
            when {
                accounts.isEmpty() -> headline = "No accounts yet"
                ready.isNotEmpty() -> {
                    headline = if (ready.size == 1) {
                        "✅ ${ready[0].email} is ready"
                    } else {
                        "✅ ${ready.size} accounts ready"
                    }
                }
                cooling.isNotEmpty() -> {
                    val next = cooling.first()
                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                    headline = "Next: ${next.email} @ ${sdf.format(Date(next.endTime!!))}"
                }
                else -> headline = "All idle"
            }

            val summary = "${ready.size} ready · ${cooling.size} cooling · $idleCount idle"

            val updatedViews = RemoteViews(context.packageName, R.layout.widget_rotation_board)
            updatedViews.setOnClickPendingIntent(android.R.id.background, openAppIntent)
            updatedViews.setTextViewText(R.id.widgetHeadline, headline)
            updatedViews.setTextViewText(R.id.widgetSummary, summary)
            appWidgetManager.updateAppWidget(appWidgetId, updatedViews)
        }
    }
}
