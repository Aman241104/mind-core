package app.mindcore.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import app.mindcore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Release(val versionCode: Int, val versionName: String, val notes: String, val size: Long, val sha256: String)

/**
 * Checks your server for a newer build, downloads it, checks its SHA-256, and hands it to Android's
 * package installer (which shows the normal "Update this app?" screen).
 */
class Updater(private val context: Context, private val baseUrl: String, private val token: String) {

    /** A newer release, or null when this build is the latest. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val conn = open("/v1/app/latest")
        try {
            if (conn.responseCode == 404) return@withContext null
            if (conn.responseCode >= 400) throw IllegalStateException("update check failed (${conn.responseCode})")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val r = Release(o.getInt("versionCode"), o.getString("versionName"), o.optString("notes"),
                o.getLong("size"), o.getString("sha256"))
            if (r.versionCode > BuildConfig.VERSION_CODE) r else null
        } finally {
            conn.disconnect()
        }
    }

    /** Download with progress (0..1). Throws if the file doesn't match the published checksum. */
    suspend fun download(r: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "update-${r.versionCode}.apk")
        val conn = open("/v1/app/apk/${r.versionCode}")
        val sha = MessageDigest.getInstance("SHA-256")
        try {
            if (conn.responseCode >= 400) throw IllegalStateException("download failed (${conn.responseCode})")
            conn.inputStream.use { input ->
                out.outputStream().use { file ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        file.write(buf, 0, n)
                        sha.update(buf, 0, n)
                        total += n
                        onProgress((total.toFloat() / r.size).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        val got = sha.digest().joinToString("") { "%02x".format(it) }
        if (got != r.sha256) {
            out.delete()
            throw IllegalStateException("Download was corrupted (checksum mismatch). Try again.")
        }
        out
    }

    /** Android asks once whether mind-core may install updates; this opens that setting if needed. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("mind-core.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) } }
            val status = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(status.intentSender)
        }
    }

    private fun open(path: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("authorization", "Bearer $token")
        }
}

/** Android reports install progress here; "pending user action" means: show the confirm screen. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app restarts into the new version
            else -> Toast.makeText(
                context, "Update failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}", Toast.LENGTH_LONG,
            ).show()
        }
    }
}
