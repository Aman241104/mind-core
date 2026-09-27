package app.mindcore.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Client for the mind-core API (cloud/src/index.ts). Plain HttpURLConnection + org.json: no extra libraries.

data class Verification(val repo: String?, val stars: Int?, val license: String?, val pushedAt: String?, val archived: Boolean)

/** Everything and how it connects: items and notes, joined by saves, your [[links]] and similar meaning. */
data class Graph(val nodes: List<GraphNode>, val edges: List<GraphEdge>)
data class GraphNode(val id: String, val label: String, val kind: String, val isNote: Boolean, val trust: String?, val favorite: Boolean)
/** type: mention (your link) | mentioned_together (same save) | related (note, by meaning) | similar (items, by meaning) */
data class GraphEdge(val a: String, val b: String, val type: String)

data class NoteSummary(
    val id: String, val kind: String, val title: String, val preview: String, val stage: String?, val color: Int,
    val pinned: Boolean, val favorite: Boolean, val words: Int, val tasks: Int, val tasksDone: Int, val updatedAt: String,
    val voiceUrl: String?,
)
/** A link out of a note (or into it, for backlinks). type: mention | related. */
data class NoteLink(val id: String, val title: String, val kind: String, val type: String, val isItem: Boolean)
/** One open "- [ ]" line in a note, for Today. */
data class NoteTask(val noteId: String, val noteTitle: String, val kind: String, val color: Int, val line: Int, val text: String)
data class NoteDetail(val summary: NoteSummary, val body: String, val links: List<NoteLink>, val backlinks: List<NoteLink>)

data class ApiItem(
    val id: String,
    val kind: String,
    val name: String,
    val url: String?,
    val oneLine: String?,
    val trust: String,
    val status: String,
    val verification: Verification?,
    val sourceCount: Int,
    val updatedAt: String,
    val userNote: String? = null,
    val deadline: String? = null, // YYYY-MM-DD
    val deadlineSource: String? = null, // post | note | voice | research | you
    val favorite: Boolean = false,
    val createdAt: String = "",
)

data class DayItem(val id: String, val name: String, val kind: String, val trust: String)

data class CalendarMonth(
    val month: String, val today: String,
    val saved: Map<String, List<DayItem>>, // day -> items saved that day
    val deadlines: Map<String, List<DayItem>>, // day -> items due that day
)

data class Upcoming(val id: String, val name: String, val kind: String, val deadline: String, val source: String?)

data class VoiceNote(val url: String, val transcript: String, val createdAt: String)

data class Source(val voiceUrl: String? = null, val url: String?, val creator: String?, val savedAt: String, val claims: List<String>, val promo: Boolean)

data class Related(val id: String, val name: String, val kind: String, val trust: String)

data class ItemDetail(val item: ApiItem, val sources: List<Source>, val related: List<Related>, val voiceNotes: List<VoiceNote> = emptyList())

data class Status(
    val laptopOnline: Boolean,
    val lastSeen: String?,
    val jobs: Map<String, Int>,
    val items: Map<String, Int>,
)

class ApiError(message: String) : Exception(message)

data class AskSource(val n: Int, val type: String, val itemId: String?, val url: String?, val title: String, val kind: String?)

data class AskAnswer(val answer: String, val sources: List<AskSource>, val found: Boolean)

data class ResearchState(val id: Int, val status: String, val answer: String?, val sources: List<Pair<String, String>>, val error: String?)

class Api(private val baseUrl: String, private val token: String) {

    suspend fun ask(question: String, history: List<Pair<String, String>>): AskAnswer {
        val h = JSONArray()
        history.forEach { (role, content) -> h.put(JSONObject().put("role", role).put("content", content)) }
        val o = JSONObject(call("POST", "/v1/ask", JSONObject().put("q", question).put("history", h)))
        val src = o.optJSONArray("sources") ?: JSONArray()
        return AskAnswer(
            answer = o.optString("answer"),
            sources = (0 until src.length()).map { i ->
                val s = src.getJSONObject(i)
                AskSource(s.getInt("n"), s.getString("type"), s.optStringOrNull("item_id"), s.optStringOrNull("url"),
                    s.getString("title"), s.optStringOrNull("kind"))
            },
            found = o.optBoolean("found", true),
        )
    }

    /** Returns the research id and whether the laptop is online to answer it. */
    suspend fun startResearch(question: String): Pair<Int, Boolean> {
        val o = JSONObject(call("POST", "/v1/research", JSONObject().put("question", question)))
        return o.getInt("id") to o.optBoolean("laptop_online")
    }

    suspend fun research(id: Int): ResearchState {
        val o = JSONObject(call("GET", "/v1/research/$id"))
        val src = o.optJSONArray("sources") ?: JSONArray()
        return ResearchState(
            id = id, status = o.getString("status"), answer = o.optStringOrNull("answer"),
            sources = (0 until src.length()).map { src.getJSONObject(it).let { s -> s.optString("title") to s.optString("url") } },
            error = o.optStringOrNull("error"),
        )
    }

    suspend fun addSaves(saves: JSONArray): JSONObject = JSONObject(call("POST", "/v1/saves", JSONObject().put("saves", saves)))

    suspend fun importWhatsapp(chat: String): JSONObject = JSONObject(call("POST", "/v1/import/whatsapp", raw = chat))

    suspend fun addVoiceNote(itemId: String, voiceUrl: String): String =
        JSONObject(call("POST", "/v1/items/$itemId/voice", JSONObject().put("voice_url", voiceUrl))).optString("transcript")

    suspend fun calendar(month: String): CalendarMonth {
        val o = JSONObject(call("GET", "/v1/calendar?month=$month"))
        fun dayItem(j: JSONObject) = DayItem(j.getString("id"), j.getString("name"), j.getString("kind"), j.optString("trust"))
        val saved = o.getJSONObject("saved").let { s -> s.keys().asSequence().associateWith { d ->
            s.getJSONArray(d).let { a -> (0 until a.length()).map { dayItem(a.getJSONObject(it)) } } } }
        val dl = o.getJSONArray("deadlines").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .groupBy({ it.getString("day") }, { dayItem(it) })
        return CalendarMonth(o.getString("month"), o.getString("today"), saved, dl)
    }

    suspend fun graph(): Graph {
        val o = JSONObject(call("GET", "/v1/graph"))
        val n = o.getJSONArray("nodes")
        val e = o.getJSONArray("edges")
        return Graph(
            (0 until n.length()).map { n.getJSONObject(it) }.map {
                GraphNode(it.getString("id"), it.getString("label"), it.getString("kind"), it.getString("group") == "note",
                    it.optStringOrNull("trust"), it.optBoolean("favorite"))
            },
            (0 until e.length()).map { e.getJSONObject(it) }.map { GraphEdge(it.getString("a"), it.getString("b"), it.getString("type")) },
        )
    }

    // ---------- notes + ideas ----------

    suspend fun notes(kind: String? = null, trash: Boolean = false): List<NoteSummary> {
        val q = listOfNotNull(kind?.let { "kind=$it" }, if (trash) "trash=1" else null).joinToString("&")
        val a = JSONArray(call("GET", "/v1/notes" + if (q.isEmpty()) "" else "?$q"))
        return (0 until a.length()).map { parseNoteSummary(a.getJSONObject(it)) }
    }

    suspend fun note(id: String): NoteDetail = parseNote(JSONObject(call("GET", "/v1/notes/$id")))

    suspend fun createNote(fields: JSONObject): NoteDetail = parseNote(JSONObject(call("POST", "/v1/notes", fields)))

    suspend fun updateNote(id: String, fields: JSONObject): NoteDetail = parseNote(JSONObject(call("PATCH", "/v1/notes/$id", fields)))

    suspend fun deleteNote(id: String, restore: Boolean = false) {
        call("DELETE", "/v1/notes/$id" + if (restore) "?restore=1" else "")
    }

    suspend fun tasks(): List<NoteTask> {
        val t = JSONObject(call("GET", "/v1/tasks")).getJSONArray("tasks")
        return (0 until t.length()).map { t.getJSONObject(it) }.map {
            NoteTask(it.getString("note_id"), it.optString("note_title"), it.optString("kind"), it.optInt("color"), it.getInt("line"), it.getString("text"))
        }
    }

    suspend fun setTask(t: NoteTask, done: Boolean) {
        call("POST", "/v1/notes/${t.noteId}/task", JSONObject().put("line", t.line).put("done", done).put("text", t.text))
    }

    /** Transcribe + tidy a recording into a new note or idea (takes a few seconds on the server). */
    suspend fun noteFromVoice(voiceUrl: String, kind: String): NoteDetail =
        parseNote(JSONObject(call("POST", "/v1/notes/voice", JSONObject().put("voice_url", voiceUrl).put("kind", kind))))

    private fun parseNoteSummary(o: JSONObject) = NoteSummary(
        o.getString("id"), o.getString("kind"), o.optString("title"), o.optString("preview"), o.optStringOrNull("stage"),
        o.optInt("color"), o.optBoolean("pinned"), o.optBoolean("favorite"), o.optInt("words"), o.optInt("tasks"),
        o.optInt("tasks_done"), o.optString("updated_at"), o.optStringOrNull("voice_url"),
    )

    private fun parseNote(o: JSONObject): NoteDetail {
        fun links(key: String) = o.optJSONArray(key)?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map {
                NoteLink(it.getString("id"), it.optString("title"), it.optString("kind"), it.optString("type"), it.optString("dst_type") == "item")
            }
        }.orEmpty()
        return NoteDetail(parseNoteSummary(o), o.optString("body"), links("links"), links("backlinks"))
    }

    suspend fun upcoming(): List<Upcoming> {
        val a = JSONObject(call("GET", "/v1/upcoming")).getJSONArray("items")
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Upcoming(it.getString("id"), it.getString("name"), it.getString("kind"), it.getString("deadline"), it.optStringOrNull("source"))
        }
    }

    suspend fun setDeadline(itemId: String, date: String?) {
        call("PUT", "/v1/items/$itemId/deadline", JSONObject().put("date", date ?: JSONObject.NULL))
    }

    /** Starts online research for the item's deadline; poll research(id) until done. */
    suspend fun findDeadline(itemId: String): Int = JSONObject(call("POST", "/v1/items/$itemId/find-deadline")).getInt("id")

    suspend fun signUpload(): JSONObject = JSONObject(call("POST", "/v1/uploads/sign"))

    private suspend fun call(method: String, path: String, body: JSONObject? = null, raw: String? = null): String = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("authorization", "Bearer $token")
            if (body != null || raw != null) {
                conn.doOutput = true
                conn.setRequestProperty("content-type", if (raw != null) "text/plain; charset=utf-8" else "application/json")
                conn.outputStream.use { it.write((raw ?: body.toString()).toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 401) throw ApiError("The server didn't accept this phone's key. Pair again from the laptop.")
            if (code >= 400) {
                // The API answers {"error": "..."}; show that sentence, not raw JSON.
                val msg = runCatching { JSONObject(text).getString("error") }.getOrNull() ?: text.take(200)
                throw ApiError(if (code >= 500 && code != 503) "Server error $code: $msg" else msg)
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    suspend fun items(kind: String? = null, limit: Int = 300): List<ApiItem> {
        val q = buildString {
            append("/v1/items?shelf=learning&limit=$limit")
            if (kind != null) append("&kind=$kind")
        }
        val arr = JSONArray(call("GET", q))
        return (0 until arr.length()).map { parseItem(arr.getJSONObject(it)) }
    }

    suspend fun item(id: String): ItemDetail = parseDetail(JSONObject(call("GET", "/v1/items/$id")))

    suspend fun setFavorite(id: String, favorite: Boolean): ItemDetail =
        parseDetail(JSONObject(call("PATCH", "/v1/items/$id", JSONObject().put("favorite", favorite))))

    suspend fun setStatus(id: String, status: String): ItemDetail =
        parseDetail(JSONObject(call("PATCH", "/v1/items/$id", JSONObject().put("status", status))))

    suspend fun status(): Status {
        val o = JSONObject(call("GET", "/v1/status"))
        val laptop = o.getJSONObject("laptop")
        return Status(
            laptopOnline = laptop.optBoolean("online"),
            lastSeen = laptop.optStringOrNull("last_seen"),
            jobs = o.getJSONObject("jobs").toIntMap(),
            items = o.getJSONObject("items").toIntMap(),
        )
    }

    private fun parseDetail(o: JSONObject): ItemDetail {
        val sources = o.getJSONArray("sources")
        val related = o.getJSONArray("related")
        return ItemDetail(
            item = parseItem(o),
            sources = (0 until sources.length()).map { i ->
                val s = sources.getJSONObject(i)
                val claims = s.optJSONArray("claims") ?: JSONArray()
                Source(
                    url = s.optStringOrNull("url"), // notes / voice / text saves have no link
                    voiceUrl = s.optStringOrNull("voice_url"),
                    creator = s.optStringOrNull("creator"),
                    savedAt = s.getString("saved_at"),
                    claims = (0 until claims.length()).map { claims.getString(it) },
                    promo = s.optInt("promo", 0) == 1,
                )
            },
            voiceNotes = (o.optJSONArray("voice_notes") ?: JSONArray()).let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { VoiceNote(it.getString("url"), it.getString("transcript"), it.getString("created_at")) }
            },
            related = (0 until related.length()).map { i ->
                val r = related.getJSONObject(i)
                Related(r.getString("id"), r.getString("name"), r.getString("kind"), r.getString("trust"))
            },
        )
    }

    private fun parseItem(o: JSONObject): ApiItem {
        val v = o.optJSONObject("verification")
        return ApiItem(
            id = o.getString("id"),
            kind = o.getString("kind"),
            name = o.getString("name"),
            url = o.optStringOrNull("url"),
            oneLine = o.optStringOrNull("one_line"),
            trust = o.getString("trust"),
            status = o.getString("status"),
            verification = v?.let {
                Verification(
                    repo = it.optStringOrNull("repo"),
                    stars = if (it.has("stars")) it.optInt("stars") else null,
                    license = it.optStringOrNull("license"),
                    pushedAt = it.optStringOrNull("pushed_at"),
                    archived = it.optBoolean("archived"),
                )
            },
            sourceCount = o.optInt("source_count", 1),
            updatedAt = o.getString("updated_at"),
            userNote = o.optStringOrNull("user_note"),
            deadline = o.optStringOrNull("deadline"),
            deadlineSource = o.optStringOrNull("deadline_source"),
            favorite = o.optInt("favorite", 0) == 1,
            createdAt = o.optString("created_at"),
        )
    }
}

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

private fun JSONObject.toIntMap(): Map<String, Int> = keys().asSequence().associateWith { getInt(it) }
