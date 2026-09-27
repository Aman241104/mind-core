package app.mindcore.quick

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import app.mindcore.ShareActivity

/** Quick Settings tile: pull down the shade, tap "Save to mind-core", the capture sheet opens. */
class CaptureTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, ShareActivity::class.java)
            .setAction(ShareActivity.ACTION_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
