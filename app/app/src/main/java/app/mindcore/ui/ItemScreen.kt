package app.mindcore.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.Capturer
import app.mindcore.data.ApiItem
import app.mindcore.data.ItemDetail
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val statuses = listOf("new" to "New", "want" to "Want", "trying" to "Trying", "done" to "Done", "skip" to "Skip")

@Composable
fun ItemScreen(id: String, api: Api, onBack: () -> Unit, onOpenItem: (String) -> Unit, onChanged: (ApiItem) -> Unit, onShowInGraph: (String) -> Unit = {}) {
    var detail by remember(id) { mutableStateOf<ItemDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(id) {
        try { detail = api.item(id) } catch (e: Exception) { error = e.message }
    }
    fun open(url: String?) = openLink(context, url)

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back",
                modifier = Modifier.padding(top = 8.dp).clip(CircleShape).clickable(onClick = onBack).padding(10.dp).size(26.dp),
            )
        }
        val d = detail
        if (d == null) {
            item {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }
        val it = d.item
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(kindLabels[it.kind] ?: it.kind, kindColor(it.kind))
                    Spacer(Modifier.width(8.dp))
                    Pill(trustLabel(it.trust), trustColor(it.trust))
                    Spacer(Modifier.weight(1f))
                    FavoriteButton(it.favorite) {
                        scope.launch {
                            runCatching { api.setFavorite(it.id, !it.favorite) }.onSuccess { d -> detail = d; onChanged(d.item) }
                        }
                    }
                }
                Text(it.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                it.oneLine?.let { line -> Text(line, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        it.verification?.let { v ->
            item {
                Block("Checked on GitHub") {
                    Fact("Repo", v.repo ?: "not found")
                    v.stars?.let { s -> Fact("Stars", compact(s)) }
                    Fact("License", v.license?.takeIf { l -> l != "NOASSERTION" } ?: "none / custom")
                    v.pushedAt?.let { p -> Fact("Last update", p.take(10)) }
                    if (v.archived) Fact("Status", "Archived, no longer maintained")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                webLink(it.url)?.let { url ->
                    Button(onClick = { open(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if ("github.com" in url) "Open on GitHub" else "Open link")
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    statuses.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = it.status == value,
                            onClick = {
                                scope.launch {
                                    try {
                                        val updated = api.setStatus(it.id, value)
                                        detail = updated
                                        onChanged(updated.item)
                                    } catch (e: Exception) { error = e.message }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, statuses.size),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }
        }
        item {
            var busy by remember(id) { mutableStateOf<String?>(null) }
            Block("Your notes") {
                // Each voice note: its recording (tap to listen again) and what it said.
                d.voiceNotes.forEach { v ->
                    VoicePlayer(v.url)
                    Text(v.transcript, style = MaterialTheme.typography.bodyMedium)
                }
                // Typed notes (voice-note lines are already shown above with their players).
                it.userNote?.lines()?.filterNot { l -> d.voiceNotes.isNotEmpty() && l.startsWith("Voice note, ") }
                    ?.joinToString("\n")?.takeIf { n -> n.isNotBlank() }?.let { n -> Text(n, style = MaterialTheme.typography.bodyMedium) }
                    ?: Text("Nothing yet. Add a voice note: why you saved it, when to try it, any deadline.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy != null) Text(busy!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                VoiceButton("Add voice note", onRecorded = { file ->
                    if (file == null) return@VoiceButton
                    busy = "Saving and transcribing…"
                    scope.launch {
                        busy = try {
                            val url = Capturer(context.applicationContext, api).uploadAudio(file)
                            api.addVoiceNote(it.id, url)
                            detail = api.item(id)
                            null
                        } catch (e: Exception) { e.message ?: "Couldn't save the voice note" }
                    }
                })
            }
        }
        item { DeadlineBlock(it, api) { scope.launch { detail = api.item(id); onChanged(detail!!.item) } } }
        item { SectionTitle("Where you saved it") }
        items(d.sources) { s ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                // Only saves that came from a link are clickable (notes and voice saves have none).
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .then(if (webLink(s.url) != null) Modifier.clickable { open(s.url) } else Modifier),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.creator ?: s.url?.substringAfter("//")?.substringBefore("/") ?: "Your note",
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (s.promo) Pill("Promo", MaterialTheme.colorScheme.outline)
                    }
                    Text("Saved ${s.savedAt.take(10)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    s.voiceUrl?.let { v -> VoicePlayer(v) }
                    if (s.claims.isNotEmpty()) {
                        Text("What the post claimed (not fact-checked yet):", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        s.claims.forEach { c -> Text("• $c", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        if (d.related.isNotEmpty()) {
            item { SectionTitle("Related") }
            items(d.related) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onOpenItem(r.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(kindLabels[r.kind] ?: r.kind, kindColor(r.kind))
                    Spacer(Modifier.width(10.dp))
                    Text(r.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(50)).clickable { onShowInGraph(id) }
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)).padding(14.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("See in graph", style = MaterialTheme.typography.labelLarge)
            }
        }
        item { MakeCardsButton(api, "item", id) }
        item { Spacer(Modifier.height(24.dp).windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

private val deadlineFrom = mapOf(
    "post" to "from the post", "note" to "from your note", "voice" to "from your voice note",
    "research" to "found online", "you" to "set by you",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineBlock(item: ApiItem, api: Api, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var status by remember(item.id) { mutableStateOf<String?>(null) }
    Block("Deadline") {
        val d = item.deadline
        if (d != null) {
            val date = LocalDate.parse(d)
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
            Text("${dueLabel(date, LocalDate.now())} · ${deadlineFrom[item.deadlineSource] ?: ""}", style = MaterialTheme.typography.bodySmall,
                color = if (date.isBefore(LocalDate.now())) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("No deadline. Say one in a voice note, pick a date, or look it up.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = true }) { Text(if (d == null) "Pick date" else "Change") }
            if (d != null) OutlinedButton(onClick = { scope.launch { api.setDeadline(item.id, null); onChanged() } }) { Text("Clear") }
            if (item.kind in setOf("job", "course", "cert", "other") && status == null) OutlinedButton(onClick = {
                status = "Looking it up on your laptop…"
                scope.launch {
                    status = try {
                        val rid = api.findDeadline(item.id)
                        var r = api.research(rid)
                        while (r.status == "pending" || r.status == "leased") { delay(4000); r = api.research(rid) }
                        onChanged()
                        if (r.status == "done") r.answer?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(200) else "Couldn't find it: ${r.error}"
                    } catch (e: Exception) { e.message }
                }
            }) { Text("Find online") }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = item.deadline?.let { LocalDate.parse(it).toEpochDay() * 86_400_000L },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    state.selectedDateMillis?.let { ms ->
                        scope.launch { api.setDeadline(item.id, LocalDate.ofEpochDay(ms / 86_400_000L).toString()); onChanged() }
                    }
                }) { Text("Set deadline") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
)

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Make flashcards" → Swan writes cards from this note or item; they show up in Review on Today. */
@Composable
internal fun MakeCardsButton(api: Api, type: String, id: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    val scope = rememberCoroutineScope()
    var state by remember(id) { mutableStateOf("") } // "" | working | message
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).clickable(enabled = state != "working") {
            state = "working"
            scope.launch {
                state = runCatching { api.makeCards(type, id) }.fold(
                    { n -> "Made $n flashcards. Review them from Today." }, { it.message ?: "Couldn't make cards" })
            }
        }.border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(50)).padding(14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == "working") app.mindcore.ui.blob.LotusBlob(app.mindcore.ui.blob.BlobState.Thinking, size = 22.dp, onTap = null)
        else Icon(Icons.Rounded.Style, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(when (state) { "" -> "Make flashcards"; "working" -> "Writing cards…"; else -> state },
            style = MaterialTheme.typography.labelLarge, color = color)
    }
}
