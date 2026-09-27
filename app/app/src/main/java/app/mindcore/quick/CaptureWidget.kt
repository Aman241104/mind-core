package app.mindcore.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.mindcore.MainActivity
import app.mindcore.R
import app.mindcore.ShareActivity

/** Home-screen widget: "＋ Save" opens the capture sheet, the rest opens the app. */
class CaptureWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val capture = PendingIntent.getActivity(
            context, 1,
            Intent(context, ShareActivity::class.java).setAction(ShareActivity.ACTION_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_capture).apply {
            setOnClickPendingIntent(R.id.widget_capture, capture)
            setOnClickPendingIntent(R.id.widget_root, open)
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
