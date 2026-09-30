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
data class BoardSummary(val id: String, val title: String, val cards: Int, val updatedAt: String)
/** type: text (a sticky) | note | item. title/kind/subtitle come from the note or item it points at. */
data class BoardCard(
    val id: String, val type: String, val refId: String?, val text: String, val x: Float, val y: Float, val color: Int,
    val title: String? = null, val kind: String? = null, val subtitle: String? = null,
)
data class Board(val id: String, val title: String, val cards: List<BoardCard>, val edges: List<Pair<String, String>>)

data class Resurfaced(val type: String, val id: String, val title: String, val kind: String, val subtitle: String?, val reason: String, val color: Int)

data class Flashcard(val id: String, val q: String, val a: String, val sourceType: String, val sourceId: String, val sourceTitle: String)

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

data class ItemDetail(
    val item: ApiItem, val sources: List<Source>, val related: List<Related>, val voiceNotes: List<VoiceNote> = emptyList(),
    val notes: List<NoteLink> = emptyList(), // your notes that link here
)

data class Status(
    val laptopOnline: Boolean,
    val lastSeen: String?,
    val jobs: Map<String, Int>,
    val items: Map<String, Int>,
)

// IELTS data classes

data class IeltsResource(
    val id: String, val skill: String, val kind: String, val title: String,
    val url: String?, val note: String?, val bandFocus: String?,
    val orderHint: Int, val createdAt: String,
)

data class IeltsTask(
    val id: String, val skill: String, val taskType: String, val title: String,
    val prompt: String, val difficulty: String, val createdAt: String,
)

data class IeltsFeedback(
    val band: Double?, val strengths: List<String>, val fixes: List<String>, val note: String?,
)

data class AttemptResult(
    val id: String, val band: Double?, val scoreRaw: Int?, val scoreTotal: Int?,
    val feedback: IeltsFeedback?, val note: String?, val taskId: String?, val skill: String,
)

data class IeltsAttempt(
    val id: String, val taskId: String?, val mode: String, val skill: String,
    val scoreRaw: Int?, val scoreTotal: Int?, val band: Double?, val feedback: IeltsFeedback?,
    val createdAt: String,
)

data class IeltsSkillProgress(
    val skill: String, val band: Double, val createdAt: String,
)

data class IeltsProgress(
    val bySkill: List<IeltsSkillProgress>, val overallEstimate: Double?,
)

data class UniNews(
    val id: String, val university: String, val country: String, val sourceUrl: String,
    val headline: String, val summary: String?, val kind: String, val detectedAt: String,
    val seen: Boolean,
)

data class PlanAction(val id: String, val whenText: String, val action: String, val why: String?)

data class PlanUniversity(
    val id: String, val rank: Int, val name: String, val country: String,
    val tuition: String?, val scholarship: String?, val whyFits: String?, val wishlisted: Boolean,
)

data class PlanMarket(val id: String, val country: String, val postStudyVisa: String, val outlook: String, val sourceNote: String?)

data class PlanScholarship(
    val id: String, val name: String, val place: String,
    val amount: String?, val eligibility: String?, val deadline: String?,
)

class ApiError(message: String) : Exception(message)

data class AskSource(val n: Int, val type: String, val itemId: String?, val url: String?, val title: String, val kind: String?, val noteId: String? = null)

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

    /**
     * Ask, with the answer streamed in as it's written: [onDelta] gets each new piece (on the IO thread).
     * Returns the finished answer with only the sources it actually cited.
     */
    suspend fun askStream(question: String, history: List<Pair<String, String>>, onDelta: suspend (String) -> Unit): AskAnswer =
        withContext(Dispatchers.IO) {
            val h = JSONArray()
            history.forEach { (role, content) -> h.put(JSONObject().put("role", role).put("content", content)) }
            val conn = URL(baseUrl.trimEnd('/') + "/v1/ask").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000 // between pieces, not total
                conn.setRequestProperty("authorization", "Bearer $token")
                conn.setRequestProperty("content-type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(JSONObject().put("q", question).put("history", h).put("stream", true).toString().toByteArray()) }
                val code = conn.responseCode
                if (code == 401) throw ApiError("The server didn't accept this phone's key. Pair again from the laptop.")
                if (code >= 400) {
                    val text = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    throw ApiError(runCatching { JSONObject(text).getString("error") }.getOrNull() ?: "Server error $code")
                }
                var all = emptyList<AskSource>()
                val answer = StringBuilder()
                var cited = emptyList<Int>()
                var found = true
                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val o = JSONObject(line)
                        when (o.optString("type")) {
                            "sources" -> all = o.getJSONArray("sources").let { src ->
                                (0 until src.length()).map { i ->
                                    val s = src.getJSONObject(i)
                                    AskSource(s.getInt("n"), s.getString("type"), s.optStringOrNull("item_id"), s.optStringOrNull("url"),
                                        s.getString("title"), s.optStringOrNull("kind"), s.optStringOrNull("note_id"))
                                }
                            }
                            "delta" -> { val t = o.optString("text"); answer.append(t); onDelta(t) }
                            "done" -> {
                                cited = o.getJSONArray("cited").let { c -> (0 until c.length()).map { c.getInt(it) } }
                                found = o.optBoolean("found", true)
                            }
                            "error" -> throw ApiError(o.optString("error", "The answer stopped halfway"))
                        }
                    }
                }
                AskAnswer(answer.toString().trim(), all.filter { it.n in cited }.sortedBy { cited.indexOf(it.n) }, found)
            } finally {
                conn.disconnect()
            }
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

    /** Swan's suggestions for a note, as markdown. mode: expand | questions | next | connect */
    suspend fun brainstorm(noteId: String, mode: String): String =
        JSONObject(call("POST", "/v1/notes/$noteId/brainstorm", JSONObject().put("mode", mode))).getString("text")

    // ---------- boards ----------

    suspend fun boards(): List<BoardSummary> {
        val a = JSONArray(call("GET", "/v1/boards"))
        return (0 until a.length()).map { a.getJSONObject(it) }.map { BoardSummary(it.getString("id"), it.optString("title"), it.optInt("cards"), it.optString("updated_at")) }
    }

    suspend fun board(id: String): Board {
        val o = JSONObject(call("GET", "/v1/boards/$id"))
        val c = o.getJSONArray("cards"); val e = o.getJSONArray("edges")
        return Board(
            o.getString("id"), o.optString("title"),
            (0 until c.length()).map { c.getJSONObject(it) }.map {
                BoardCard(it.getString("id"), it.getString("type"), it.optStringOrNull("ref_id"), it.optString("text"),
                    it.optDouble("x", 0.0).toFloat(), it.optDouble("y", 0.0).toFloat(), it.optInt("color"),
                    it.optStringOrNull("title"), it.optStringOrNull("kind"), it.optStringOrNull("subtitle"))
            },
            (0 until e.length()).map { e.getJSONObject(it) }.map { it.getString("a") to it.getString("b") },
        )
    }

    suspend fun createBoard(title: String): Board = JSONObject(call("POST", "/v1/boards", JSONObject().put("title", title))).let { board(it.getString("id")) }

    suspend fun saveBoard(id: String, title: String, cards: List<BoardCard>, edges: List<Pair<String, String>>) {
        val c = JSONArray(); cards.forEach {
            c.put(JSONObject().put("id", it.id).put("type", it.type).put("ref_id", it.refId ?: JSONObject.NULL).put("text", it.text)
                .put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("color", it.color))
        }
        val e = JSONArray(); edges.forEach { (a, b) -> e.put(JSONObject().put("a", a).put("b", b)) }
        call("PUT", "/v1/boards/$id", JSONObject().put("title", title).put("cards", c).put("edges", e))
    }

    suspend fun deleteBoard(id: String) { call("DELETE", "/v1/boards/$id") }

    /** Swan's new ideas for a board: (text, id of the card it grows from or null). */
    suspend fun suggestCards(id: String): List<Pair<String, String?>> {
        val a = JSONObject(call("POST", "/v1/boards/$id/suggest", JSONObject())).getJSONArray("ideas")
        return (0 until a.length()).map { a.getJSONObject(it) }.map { it.getString("text") to it.optStringOrNull("from") }
    }

    /** A few things worth a second look today (an idea at rest, something you meant to try, an older find). */
    suspend fun resurface(): List<Resurfaced> {
        val a = JSONObject(call("GET", "/v1/resurface")).getJSONArray("items")
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Resurfaced(it.getString("type"), it.getString("id"), it.getString("title"), it.optString("kind"),
                it.optStringOrNull("subtitle"), it.getString("reason"), it.optInt("color"))
        }
    }

    // ---------- flashcards ----------

    /** Make cards from a note or item (replaces earlier ones from it). Returns how many. */
    suspend fun makeCards(type: String, id: String): Int =
        JSONObject(call("POST", "/v1/flashcards", JSONObject().put("type", type).put("id", id))).getInt("made")

    suspend fun dueCards(): List<Flashcard> {
        val a = JSONObject(call("GET", "/v1/flashcards/due")).getJSONArray("due")
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Flashcard(it.getString("id"), it.getString("q"), it.getString("a"), it.getString("source_type"), it.getString("source_id"),
                it.optString("source_title"))
        }
    }

    /** grade: again | hard | good | easy. Returns the next due date. */
    suspend fun reviewCard(id: String, grade: String): String =
        JSONObject(call("POST", "/v1/flashcards/$id/review", JSONObject().put("grade", grade))).getString("due")

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
            notes = (o.optJSONArray("notes") ?: JSONArray()).let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { NoteLink(it.getString("id"), it.optString("title"), it.optString("kind"), it.optString("type"), false) }
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

    // ---------- IELTS prep ----------

    suspend fun ieltsResources(skill: String? = null): List<IeltsResource> {
        val path = "/v1/ielts/resources" + (skill?.let { "?skill=$it" } ?: "")
        val arr = JSONObject(call("GET", path)).getJSONArray("resources")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            IeltsResource(
                o.getString("id"), o.getString("skill"), o.getString("kind"), o.getString("title"),
                o.optStringOrNull("url"), o.optStringOrNull("note"), o.optStringOrNull("band_focus"),
                o.optInt("order_hint"), o.getString("created_at"),
            )
        }
    }

    suspend fun ieltsTasks(skill: String? = null, taskType: String? = null): List<IeltsTask> {
        val q = listOfNotNull(skill?.let { "skill=$it" }, taskType?.let { "task_type=$it" }).joinToString("&")
        val arr = JSONObject(call("GET", "/v1/ielts/tasks" + if (q.isEmpty()) "" else "?$q")).getJSONArray("tasks")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            IeltsTask(
                o.getString("id"), o.getString("skill"), o.getString("task_type"), o.getString("title"),
                o.getString("prompt"), o.getString("difficulty"), o.getString("created_at"),
            )
        }
    }

    suspend fun ieltsTask(id: String): IeltsTask {
        val o = JSONObject(call("GET", "/v1/ielts/tasks/$id")).getJSONObject("task")
        return IeltsTask(
            o.getString("id"), o.getString("skill"), o.getString("task_type"), o.getString("title"),
            o.getString("prompt"), o.getString("difficulty"), o.getString("created_at"),
        )
    }

    suspend fun submitIeltsAttempt(taskId: String?, mode: String, skill: String, response: String): AttemptResult {
        val b = JSONObject().put("mode", mode).put("skill", skill).put("response", response)
        taskId?.let { b.put("task_id", it) }
        val o = JSONObject(call("POST", "/v1/ielts/attempts", b))
        return AttemptResult(
            id = o.getString("id"),
            band = o.optDoubleOrNull("band"),
            scoreRaw = o.optIntOrNull("score_raw"),
            scoreTotal = o.optIntOrNull("score_total"),
            feedback = o.optJSONObject("feedback")?.let { parseIeltsFeedback(it) },
            note = o.optStringOrNull("note"),
            taskId = taskId,
            skill = skill,
        )
    }

    suspend fun ieltsAttempts(skill: String? = null): List<IeltsAttempt> {
        val path = "/v1/ielts/attempts" + (skill?.let { "?skill=$it" } ?: "")
        val arr = JSONObject(call("GET", path)).getJSONArray("attempts")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val fbStr = o.optStringOrNull("feedback")
            val fb = fbStr?.let { runCatching { JSONObject(it) }.getOrNull() }
            IeltsAttempt(
                o.getString("id"), o.optStringOrNull("task_id"), o.getString("mode"), o.getString("skill"),
                o.optIntOrNull("score_raw"), o.optIntOrNull("score_total"), o.optDoubleOrNull("band"),
                fb?.let { parseIeltsFeedback(it) }, o.getString("created_at"),
            )
        }
    }

    suspend fun ieltsProgress(): IeltsProgress {
        val o = JSONObject(call("GET", "/v1/ielts/progress"))
        val arr = o.optJSONArray("by_skill") ?: JSONArray()
        val list = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            IeltsSkillProgress(
                s.getString("skill"), s.optDoubleOrNull("band") ?: 0.0, s.getString("created_at"),
            )
        }
        return IeltsProgress(list, o.optDoubleOrNull("overall_estimate"))
    }

    suspend fun news(unseenOnly: Boolean = false): List<UniNews> {
        val path = "/v1/news" + if (unseenOnly) "?unseen=1" else ""
        val arr = JSONObject(call("GET", path)).getJSONArray("news")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            UniNews(
                o.getString("id"), o.getString("university"), o.getString("country"), o.getString("source_url"),
                o.getString("headline"), o.optStringOrNull("summary"), o.getString("kind"),
                o.getString("detected_at"), o.optInt("seen", 0) == 1,
            )
        }
    }

    suspend fun markNewsSeen(id: String) { call("POST", "/v1/news/$id/seen") }

    // ---------- MS Abroad Plan (in-app, no browser needed) ----------

    suspend fun planActions(): List<PlanAction> {
        val arr = JSONObject(call("GET", "/v1/plan/actions")).getJSONArray("actions")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlanAction(o.getString("id"), o.getString("when_text"), o.getString("action"), o.optStringOrNull("why"))
        }
    }

    suspend fun planUniversities(): List<PlanUniversity> {
        val arr = JSONObject(call("GET", "/v1/plan/universities")).getJSONArray("universities")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlanUniversity(
                o.getString("id"), o.getInt("rank"), o.getString("name"), o.getString("country"),
                o.optStringOrNull("tuition"), o.optStringOrNull("scholarship"), o.optStringOrNull("why_fits"),
                o.optInt("wishlisted", 0) == 1,
            )
        }
    }

    suspend fun toggleWishlist(id: String): Boolean =
        JSONObject(call("POST", "/v1/plan/universities/$id/wishlist")).getBoolean("wishlisted")

    suspend fun planMarket(): List<PlanMarket> {
        val arr = JSONObject(call("GET", "/v1/plan/market")).getJSONArray("market")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlanMarket(o.getString("id"), o.getString("country"), o.getString("post_study_visa"),
                o.getString("outlook"), o.optStringOrNull("source_note"))
        }
    }

    suspend fun planScholarships(): List<PlanScholarship> {
        val arr = JSONObject(call("GET", "/v1/plan/scholarships")).getJSONArray("scholarships")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlanScholarship(
                o.getString("id"), o.getString("name"), o.getString("place"),
                o.optStringOrNull("amount"), o.optStringOrNull("eligibility"), o.optStringOrNull("deadline"),
            )
        }
    }

    private fun parseIeltsFeedback(o: JSONObject): IeltsFeedback {
        val strengthsArr = o.optJSONArray("strengths") ?: JSONArray()
        val fixesArr = o.optJSONArray("fixes") ?: JSONArray()
        return IeltsFeedback(
            o.optDoubleOrNull("band"),
            (0 until strengthsArr.length()).map { i -> strengthsArr.getString(i) },
            (0 until fixesArr.length()).map { i -> fixesArr.getString(i) },
            o.optStringOrNull("note"),
        )
    }

    // Extension functions
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else getDouble(key)
}

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

private fun JSONObject.toIntMap(): Map<String, Int> = keys().asSequence().associateWith { getInt(it) }
