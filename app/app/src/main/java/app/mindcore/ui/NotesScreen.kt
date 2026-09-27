package app.mindcore.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.Capturer
import app.mindcore.data.NoteSummary
import app.mindcore.ui.blob.BlobState
import app.mindcore.ui.blob.LotusBlob
import app.mindcore.ui.theme.Pastel
import app.mindcore.ui.theme.Pastels
import kotlinx.coroutines.launch
import java.io.File

// Notes + Ideas: a pastel board of everything you've written. Ideas carry a stage, from spark to done.

/** The notes list, kept across tab switches; refreshed on open and after editing. */
class NotesState {
    var notes by mutableStateOf<List<NoteSummary>>(emptyList())
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var filter by mutableStateOf("all") // all | note | idea | todo | pinned | trash
    var stage by mutableStateOf<String?>(null)
    var trash by mutableStateOf<List<NoteSummary>>(emptyList())
    var tasks by mutableStateOf<List<app.mindcore.data.NoteTask>>(emptyList())

    suspend fun refresh(api: Api?) {
        api ?: return
        loading = true
        runCatching { api.notes() }.onSuccess { notes = it; error = null; loaded = true }.onFailure { error = it.message }
        runCatching { api.tasks() }.onSuccess { tasks = it }
        if (filter == "trash") runCatching { api.notes(trash = true) }.onSuccess { trash = it }
        loading = false
    }

    /** Tick a to-do from Today: gone from the list right away, put back if the server says no. */
    suspend fun complete(api: Api, t: app.mindcore.data.NoteTask) {
        val before = tasks
        tasks = tasks - t
        runCatching { api.setTask(t, true) }.onFailure { tasks = before; error = it.message }
            .onSuccess { runCatching { api.notes() }.onSuccess { notes = it } }
    }
}

val ideaStages = listOf("spark" to "Spark", "growing" to "Growing", "ready" to "Ready", "done" to "Done", "parked" to "Parked")

@Composable
fun pastelFor(index: Int): Pastel = Pastels[index.mod(Pastels.size)]

@Composable
fun Pastel.bg(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) dark else light

@Composable
fun Pastel.ink(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) inkDark else inkLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(api: Api?, state: NotesState, onOpen: (String) -> Unit, onNew: (kind: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var talking by remember { mutableStateOf(false) }
    val all = state.notes
    val shown = all.filter {
        when (state.filter) {
            "note" -> it.kind == "note"
            "idea" -> it.kind == "idea" && (state.stage == null || it.stage == state.stage)
            "todo" -> it.tasks > it.tasksDone
            "pinned" -> it.pinned || it.favorite
            else -> true
        }
    }
    val counts = mapOf(
        "all" to all.size, "note" to all.count { it.kind == "note" }, "idea" to all.count { it.kind == "idea" },
        "todo" to all.count { it.tasks > it.tasksDone }, "pinned" to all.count { it.pinned || it.favorite },
    )

    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = { scope.launch { state.refresh(api) } }) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(Modifier.padding(start = 6.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${counts["idea"]} ideas · ${counts["note"]} notes", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Notes", style = MaterialTheme.typography.displaySmall)
                    }
                    CircleIconButton(MicIcon, "Talk it out", { talking = true })
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(Icons.Rounded.Add, "New note", { onNew(if (state.filter == "idea") "idea" else "note") },
                        container = MaterialTheme.colorScheme.inverseSurface, content = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillTabs(
                        listOf("all" to "All", "idea" to "Ideas", "note" to "Notes", "todo" to "To-do", "pinned" to "Pinned", "trash" to "Trash"),
                        state.filter, {
                            state.filter = it; if (it != "idea") state.stage = null
                            if (it == "trash" && api != null) scope.launch { runCatching { api.notes(trash = true) }.onSuccess { t -> state.trash = t } }
                        },
                        counts = counts, contentPadding = PaddingValues(horizontal = 2.dp),
                    )
                    if (state.filter == "idea") {
                        PillTabs(listOf<Pair<String?, String>>(null to "Every stage") + ideaStages, state.stage, { state.stage = it },
                            contentPadding = PaddingValues(horizontal = 2.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            when {
                api == null -> item(span = StaggeredGridItemSpan.FullLine) { Empty("Pair with the laptop first (Settings).") }
                !state.loaded && state.error != null -> item(span = StaggeredGridItemSpan.FullLine) { Empty(state.error!!) }
                !state.loaded -> item(span = StaggeredGridItemSpan.FullLine) {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { LotusBlob(BlobState.Thinking, size = 64.dp, onTap = null) }
                }
                state.filter == "trash" -> if (state.trash.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                    Empty("Trash is empty. Deleted notes stay here for 30 days.")
                } else items(state.trash, key = { "t" + it.id }) { n ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.graphicsLayer { alpha = 0.6f }) { NoteCard(n) {} }
                        Text("Restore", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(50)).pressScale {
                                scope.launch {
                                    runCatching { api!!.deleteNote(n.id, restore = true) }
                                        .onSuccess { state.trash = state.trash - n; state.refresh(api) }
                                }
                            }.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                shown.isEmpty() -> item(span = StaggeredGridItemSpan.FullLine) {
                    Empty(
                        when (state.filter) {
                            "idea" -> "No ideas here yet. Tap + for a spark, or the mic to talk one out."
                            "todo" -> "No open tasks. Start a line with \"- [ ]\" in any note to add one."
                            "pinned" -> "Pin or star a note to keep it here."
                            else -> "Nothing written yet. Tap + to start, or the mic to talk it out and Swan will write it up."
                        },
                    )
                }
                else -> items(shown, key = { it.id }) { NoteCard(it) { onOpen(it.id) } }
            }
        }
    }

    if (talking && api != null) {
        ModalBottomSheet(onDismissRequest = { talking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            TalkItOut(api, onDone = { id -> talking = false; scope.launch { state.refresh(api) }; onOpen(id) })
        }
    }
}

@Composable
private fun Empty(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LotusBlob(BlobState.Idle, size = 72.dp)
        Spacer(Modifier.height(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
internal fun NoteCard(n: NoteSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = pastelFor(n.color)
    val ink = p.ink()
    Column(
        modifier.fillMaxWidth().pressScale(onClick).clip(RoundedCornerShape(24.dp)).background(p.bg()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n.kind == "idea") {
                Icon(Icons.Rounded.Lightbulb, null, Modifier.size(15.dp), tint = ink.copy(alpha = 0.8f))
                Spacer(Modifier.width(4.dp))
                Text(ideaStages.firstOrNull { it.first == n.stage }?.second ?: "Idea", style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.8f))
            } else {
                Text(ago(n.updatedAt), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
            }
            Spacer(Modifier.weight(1f))
            if (n.favorite) Icon(Icons.Rounded.Star, "Starred", Modifier.size(16.dp), tint = ink)
            if (n.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(16.dp), tint = ink)
        }
        if (n.title.isNotBlank()) {
            Text(n.title, style = MaterialTheme.typography.titleLarge, color = ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (n.preview.isNotBlank()) {
            Text(n.preview, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.85f),
                maxLines = if (n.title.isBlank()) 8 else 5, overflow = TextOverflow.Ellipsis)
        }
        val meta = buildList {
            if (n.tasks > 0) add("${n.tasksDone}/${n.tasks} done")
            if (n.words > 0) add("${n.words} words")
            if (n.voiceUrl != null) add("voice")
        }
        if (meta.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (n.tasks > 0) Icon(Icons.Rounded.CheckBox, null, Modifier.size(14.dp), tint = ink.copy(alpha = 0.7f))
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f))
        }
    }
}

/** Record a thought; Swan transcribes it and writes it up as a note or an idea. */
@Composable
private fun TalkItOut(api: Api, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("idea") }
    var file by remember { mutableStateOf<File?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LotusBlob(if (working) BlobState.Thinking else BlobState.Idle, size = 48.dp, onTap = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Talk it out", style = MaterialTheme.typography.headlineSmall)
                Text(if (working) "Swan is writing it up…" else "Ramble freely. Swan keeps every fact and turns to-dos into a checklist.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PillTabs(listOf("idea" to "As an idea", "note" to "As a note"), kind, { kind = it }, contentPadding = PaddingValues(0.dp))
        if (!working) VoiceButton("Tap the mic and start talking", onRecorded = { file = it })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        val f = file
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
                .background(if (f != null && !working) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                .pressScale {
                    if (f == null || working) return@pressScale
                    working = true; error = null
                    scope.launch {
                        runCatching {
                            val url = Capturer(context.applicationContext, api).uploadAudio(f)
                            api.noteFromVoice(url, kind)
                        }.onSuccess { onDone(it.summary.id) }.onFailure { error = it.message ?: "Couldn't make the note"; working = false }
                    }
                },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (working) "Writing…" else if (kind == "idea") "Make it an idea" else "Make it a note",
                style = MaterialTheme.typography.labelLarge,
                color = if (f != null && !working) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
