package app.mindcore.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** A real web link we can open, or null (empty, "null", or not http/https). */
fun webLink(url: String?): String? {
    val u = url?.trim().orEmpty()
    if (u.isEmpty() || u == "null") return null
    return u.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}

/**
 * Open a link in the right app (browser, Instagram, YouTube...). Never crashes: an empty or broken link, or a
 * phone with no app for it, shows a short message instead.
 */
fun openLink(context: Context, url: String?) {
    val link = webLink(url) ?: run {
        Toast.makeText(context, "This one has no link to open", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open that link", Toast.LENGTH_SHORT).show()
    }
}
