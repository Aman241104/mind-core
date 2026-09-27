package app.mindcore.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mindcore.data.Api
import app.mindcore.data.ApiItem
import app.mindcore.data.Status
import app.mindcore.data.Upcoming
import app.mindcore.settings.SettingsStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * What the widgets show, saved as a small JSON file. Widgets only ever read this file (they must never
 * wait on the network); the app, the capture sheet and an hourly worker write it and then refresh them.
 */
data class WidgetSnapshot(
    val fresh: List<Row>,
    val upcoming: List<Due>,
    val pick: Row?,
    val laptopOnline: Boolean,
    val processing: Int,
    val savedThisWeek: Int,
    val updatedAt: Long,
) {
    data class Row(val id: String, val name: String, val kind: String, val detail: String)
    data class Due(val id: String, val name: String, val kind: String, val date: String)

    companion object {
        val EMPTY = WidgetSnapshot(emptyList(), emptyList(), null, false, 0, 0, 0)
    }
}

object WidgetData {
    private fun file(context: Context) = File(context.filesDir, "widget_snapshot.json")

    fun read(context: Context): WidgetSnapshot = runCatching {
        val o = JSONObject(file(context).readText())
        fun row(j: JSONObject) = WidgetSnapshot.Row(j.getString("id"), j.getString("name"), j.getString("kind"), j.optString("detail"))
        WidgetSnapshot(
            fresh = o.getJSONArray("fresh").let { a -> (0 until a.length()).map { row(a.getJSONObject(it)) } },
            upcoming = o.getJSONArray("upcoming").let { a -> (0 until a.length()).map { a.getJSONObject(it) }
                .map { WidgetSnapshot.Due(it.getString("id"), it.getString("name"), it.getString("kind"), it.getString("date")) } },
            pick = o.optJSONObject("pick")?.let(::row),
            laptopOnline = o.optBoolean("laptopOnline"),
            processing = o.optInt("processing"),
            savedThisWeek = o.optInt("savedThisWeek"),
            updatedAt = o.optLong("updatedAt"),
        )
    }.getOrDefault(WidgetSnapshot.EMPTY)

    /** Build the snapshot from data the app already has, save it, and redraw every widget. */
    suspend fun write(context: Context, items: List<ApiItem>, upcoming: List<Upcoming>, status: Status?) {
        val newest = items.sortedByDescending { it.updatedAt }
        // "stars:72k" is drawn as a star icon + number by the widget; otherwise the one-liner.
        fun detail(it: ApiItem) = it.verification?.stars?.let { s -> "stars:" + compactNumber(s) } ?: it.oneLine.orEmpty()
        val weekAgo = LocalDate.now().minusDays(7).toString()
        val o = JSONObject()
            .put("fresh", JSONArray(newest.take(8).map { JSONObject().put("id", if (it.kind == "task") "note:${it.id}" else it.id).put("name", it.name).put("kind", it.kind).put("detail", detail(it)) }))
            .put("upcoming", JSONArray(upcoming.take(6).map { JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind).put("date", it.deadline) }))
            .put("laptopOnline", status?.laptopOnline ?: false)
            .put("processing", (status?.jobs?.get("pending") ?: 0) + (status?.jobs?.get("leased") ?: 0))
            .put("savedThisWeek", items.count { it.updatedAt >= weekAgo })
            .put("updatedAt", System.currentTimeMillis())
        dailyPick(items)?.let { o.put("pick", JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind).put("detail", it.oneLine ?: "")) }
        file(context).writeText(o.toString())
        refreshAll(context)
    }

    /**
     * Readwise-style daily review: one thing you saved but haven't done, the same all day, a new one tomorrow.
     * Prefers items you marked "want", then verified repos/tools/courses.
     */
    private fun dailyPick(items: List<ApiItem>): ApiItem? {
        val open = items.filter { it.status == "want" || it.status == "new" }
            .filter { it.kind in setOf("repo", "tool", "course", "cert") }
        val pool = open.filter { it.status == "want" }.ifEmpty { open.filter { it.trust == "verified" } }.ifEmpty { open }
        if (pool.isEmpty()) return null
        val day = LocalDate.now().toEpochDay()
        return pool.sortedBy { it.id }[(day % pool.size).toInt()]
    }

    suspend fun refreshAll(context: Context) {
        CaptureWidget().updateAll(context)
        UpcomingWidget().updateAll(context)
        FreshWidget().updateAll(context)
        PickWidget().updateAll(context)
    }

    /** Fetch from the server (used by the hourly worker, and after actions taken from a widget). */
    suspend fun sync(context: Context): Boolean {
        val s = SettingsStore(context).settings.first()
        if (s.apiToken.isBlank()) return false
        val api = Api(s.serverUrl, s.apiToken)
        return runCatching {
            write(context, api.items(), runCatching { api.upcoming() }.getOrDefault(emptyList()), runCatching { api.status() }.getOrNull())
        }.isSuccess
    }

    /** Keeps "days left" and the daily pick current even if you don't open the app. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "widget-sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetSyncWorker>(1, TimeUnit.HOURS).build(),
        )
    }
}

class WidgetSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (WidgetData.sync(applicationContext)) Result.success() else Result.retry()
}

internal fun compactNumber(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> "%.1fk".format(n / 1000f)
    else -> n.toString()
}
