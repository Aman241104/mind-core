package app.mindcore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.NoteDetail
import app.mindcore.data.NoteLink
import app.mindcore.ui.blob.BlobState
import app.mindcore.ui.blob.LotusBlob
import app.mindcore.ui.theme.Pastels
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

// The note editor: plain markdown underneath (so it syncs to Obsidian later), shown as a tidy page with real
// checkboxes and tappable [[links]]. Tap the page to write; saves itself as you go.

/**
 * @param id the note to open, or null for a new one of [newKind].
 * @param linkNames names you can [[link]] to (your items and notes), for the link picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(
    api: Api,
    id: String?,
    newKind: String,
    linkNames: List<Pair<String, String>>, // name to kind
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var noteId by remember(id) { mutableStateOf(id) }
    var loaded by remember(id) { mutableStateOf(id == null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var kind by remember(id) { mutableStateOf(newKind) }
    var title by remember(id) { mutableStateOf("") }
    var body by remember(id) { mutableStateOf(TextFieldValue("")) }
    var stage by remember(id) { mutableStateOf<String?>(if (newKind == "idea") "spark" else null) }
    var color by remember(id) { mutableStateOf(if (newKind == "idea") 2 else 0) }
    var pinned by remember(id) { mutableStateOf(false) }
    var favorite by remember(id) { mutableStateOf(false) }
    var links by remember(id) { mutableStateOf<List<NoteLink>>(emptyList()) }
    var backlinks by remember(id) { mutableStateOf<List<NoteLink>>(emptyList()) }
    var editing by remember(id) { mutableStateOf(id == null) }
    var saved by remember(id) { mutableStateOf("") } // snapshot of what the server has
    var status by remember(id) { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var brainstorming by remember { mutableStateOf(false) }

    fun snapshot() = listOf(kind, title, body.text, stage, color, pinned, favorite).joinToString("\u0000")
    fun apply(d: NoteDetail) {
        links = d.links; backlinks = d.backlinks
    }

    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        runCatching { api.note(id) }.onSuccess { d ->
            val s = d.summary
            kind = s.kind; title = s.title; body = TextFieldValue(d.body); stage = s.stage; color = s.color
            pinned = s.pinned; favorite = s.favorite; apply(d)
            editing = d.body.isBlank() && s.title.isBlank()
            saved = snapshot(); loaded = true
        }.onFailure { error = it.message }
    }

    suspend fun save() {
        if (!loaded || snapshot() == saved) return
        if (noteId == null && title.isBlank() && body.text.isBlank()) return
        val fields = JSONObject().put("kind", kind).put("title", title).put("body", body.text)
            .put("stage", stage ?: JSONObject.NULL).put("color", color).put("pinned", pinned).put("favorite", favorite)
        val snap = snapshot()
        status = "Saving…"
        runCatching { noteId?.let { api.updateNote(it, fields) } ?: api.createNote(fields) }
            .onSuccess { d -> noteId = d.summary.id; saved = snap; status = "Saved"; apply(d) }
            .onFailure { status = "Not saved: ${it.message}" }
    }

    // Save a moment after you stop typing; links refresh a few seconds later (the server finds them in the background).
    LaunchedEffect(kind, title, body.text, stage, color, pinned, favorite, loaded) {
        if (!loaded || snapshot() == saved) return@LaunchedEffect
        delay(800)
        save()
        delay(4000)
        noteId?.let { nid -> runCatching { api.note(nid) }.onSuccess { apply(it) } }
    }

    fun leave() {
        scope.launch {
            withContext(NonCancellable) { withTimeoutOrNull(6000) { save() } }
            onBack()
        }
    }
    BackHandler { if (editing && loaded) editing = false else leave() }

    fun openLink(name: String) {
        val l = (links + backlinks).firstOrNull { it.title.equals(name, ignoreCase = true) }
        when {
            l == null -> status = "\"$name\" isn't linked yet (no note or item with that exact name)"
            l.isItem -> scope.launch { save(); onOpenItem(l.id) }
            else -> scope.launch { save(); onOpenNote(l.id) }
        }
    }

    /** Toggle "- [ ]" ↔ "- [x]" on one line of the body. */
    fun toggleTask(line: Int) {
        val lines = body.text.split("\n").toMutableList()
        val l = lines[line]
        lines[line] = if (Regex("""^\s*[-*] \[ \]""").containsMatchIn(l)) l.replaceFirst("[ ]", "[x]") else l.replaceFirst(Regex("""\[[xX]\]"""), "[ ]")
        body = body.copy(text = lines.joinToString("\n"))
    }

    /** Put [prefix] at the start of the cursor's line (or take it off if it's already there). */
    fun linePrefix(prefix: String) {
        val t = body.text
        val start = t.lastIndexOf('\n', (body.selection.start - 1).coerceAtLeast(0)).let { if (it < 0 || body.selection.start == 0) 0 else it + 1 }
        val has = t.startsWith(prefix, start)
        val nt = if (has) t.removeRange(start, start + prefix.length) else t.substring(0, start) + prefix + t.substring(start)
        val shift = if (has) -prefix.length else prefix.length
        body = TextFieldValue(nt, TextRange((body.selection.start + shift).coerceIn(0, nt.length)))
    }

    fun insert(text: String) {
        val t = body.text
        val s = body.selection.start.coerceIn(0, t.length); val e = body.selection.end.coerceIn(s, t.length)
        body = TextFieldValue(t.substring(0, s) + text + t.substring(e), TextRange(s + text.length))
    }

    val p = Pastels[color.mod(Pastels.size)]
    val bg = p.bg(); val ink = p.ink()

    CompositionLocalProvider(LocalContentColor provides ink) {
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().imePadding()) {
            // Top bar
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BarIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", ink) { leave() }
                Text(status, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.6f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                BarIcon(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (pinned) "Unpin" else "Pin", ink) { pinned = !pinned }
                BarIcon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (favorite) "Unstar" else "Star", ink) { favorite = !favorite }
                if (noteId != null) BarIcon(Icons.Rounded.Delete, "Move to trash", ink) { confirmDelete = true }
            }
            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else LotusBlob(BlobState.Thinking, size = 64.dp, onTap = null)
                }
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
                // Kind, stage and colour
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (kind == "idea") "Idea" else "Note", true, ink) {
                        kind = if (kind == "idea") "note" else "idea"; stage = if (kind == "idea") (stage ?: "spark") else null
                    }
                    if (kind == "idea") ideaStages.forEach { (k, label) -> Chip(label, stage == k, ink) { stage = k } }
                    Spacer(Modifier.width(6.dp))
                    Pastels.forEachIndexed { i, c ->
                        Box(Modifier.size(22.dp).clip(CircleShape).background(c.bg())
                            .border(if (i == color) 2.dp else 1.dp, ink.copy(alpha = if (i == color) 0.9f else 0.25f), CircleShape)
                            .clickable { color = i })
                    }
                }
                val titleFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { if (id == null) runCatching { titleFocus.requestFocus() } }
                BasicTextField(
                    title, { title = it.replace("\n", " ") },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(color = ink),
                    cursorBrush = SolidColor(ink),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp).focusRequester(titleFocus),
                    decorationBox = { inner ->
                        if (title.isEmpty()) Text(if (kind == "idea") "What's the idea?" else "Title", style = MaterialTheme.typography.headlineMedium,
                            color = ink.copy(alpha = 0.35f))
                        inner()
                    },
                )
                if (editing) {
                    val bodyFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { if (id != null) runCatching { bodyFocus.requestFocus() } }
                    BasicTextField(
                        body, { body = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
                        cursorBrush = SolidColor(ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).focusRequester(bodyFocus),
                        decorationBox = { inner ->
                            if (body.text.isEmpty()) Text("Write freely. \"- [ ]\" makes a checkbox, [[name]] links to anything you saved.",
                                style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = 0.35f))
                            inner()
                        },
                    )
                } else {
                    Rendered(body.text, ink, onToggle = ::toggleTask, onLink = ::openLink,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).clickable(indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { editing = true })
                }
                noteId?.let { nid -> if (body.text.length > 40) Box(Modifier.padding(top = 24.dp)) { MakeCardsButton(api, "note", nid, ink) } }
                Connections(links, backlinks, ink, onOpenItem = { i -> scope.launch { save(); onOpenItem(i) } },
                    onOpenNote = { n -> scope.launch { save(); onOpenNote(n) } })
                Spacer(Modifier.padding(bottom = 90.dp))
            }
            // Writing tools
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).clip(RoundedCornerShape(50))
                    .background(ink.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    BarIcon(Icons.Rounded.CheckBox, "Checklist line", ink) { linePrefix("- [ ] ") }
                    BarIcon(Icons.AutoMirrored.Rounded.FormatListBulleted, "Bullet line", ink) { linePrefix("- ") }
                    BarIcon(Icons.Rounded.Title, "Heading line", ink) { linePrefix("## ") }
                    BarIcon(Icons.Rounded.Link, "Link to something", ink) { picking = true }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.clip(RoundedCornerShape(50)).background(ink).clickable { editing = false }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Done, null, Modifier.size(18.dp), tint = bg)
                        Spacer(Modifier.width(6.dp))
                        Text("Done", style = MaterialTheme.typography.labelLarge, color = bg)
                    }
                } else {
                    Row(Modifier.clip(RoundedCornerShape(50)).clickable { brainstorming = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        LotusBlob(BlobState.Idle, size = 26.dp, onTap = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Brainstorm", style = MaterialTheme.typography.labelLarge, color = ink)
                    }
                    Text(wordsLine(body.text), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp))
                    Row(Modifier.clip(RoundedCornerShape(50)).background(ink).clickable { editing = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = bg)
                        Spacer(Modifier.width(6.dp))
                        Text("Write", style = MaterialTheme.typography.labelLarge, color = bg)
                    }
                }
            }
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LinkPicker(linkNames.filter { !it.first.equals(title, ignoreCase = true) }) { name -> insert("[[$name]]"); picking = false }
        }
    }
    if (brainstorming) {
        ModalBottomSheet(onDismissRequest = { brainstorming = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Brainstorm(
                ensureSaved = { save(); noteId },
                run = { nid, mode -> api.brainstorm(nid, mode) },
                onAdd = { heading, md ->
                    val t = body.text.trimEnd()
                    body = TextFieldValue((if (t.isEmpty()) "" else "$t\n\n") + "## $heading\n$md")
                    brainstorming = false
                },
            )
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Move to trash?") },
            text = { Text("You can bring it back within 30 days.") },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    confirmDelete = false
                    val nid = noteId ?: return@TextButton
                    scope.launch { runCatching { api.deleteNote(nid) }.onSuccess { onBack() }.onFailure { status = "Couldn't delete: ${it.message}" } }
                }) { Text("Move to trash") }
            },
            dismissButton = { androidx.compose.material3.TextButton({ confirmDelete = false }) { Text("Keep") } },
        )
    }
}

private fun wordsLine(text: String): String {
    val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    val tasks = Regex("""(?m)^\s*[-*] \[( |x|X)\]""").findAll(text).toList()
    val done = tasks.count { it.value.contains('x', ignoreCase = true) }
    return listOfNotNull("$words words", if (tasks.isNotEmpty()) "$done/${tasks.size} done" else null).joinToString(" · ")
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(11.dp).size(22.dp))
}

@Composable
private fun Chip(label: String, on: Boolean, ink: Color, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.labelMedium, color = if (on) ink else ink.copy(alpha = 0.6f),
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.pressScale(onClick).clip(RoundedCornerShape(50)).background(if (on) ink.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, ink.copy(alpha = if (on) 0f else 0.2f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** Markdown, lightly: headings, checkboxes you can tick, bullets, quotes, and [[links]]. */
@Composable
private fun Rendered(text: String, ink: Color, onToggle: (Int) -> Unit, onLink: (String) -> Unit, modifier: Modifier) {
    val type = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (text.isBlank()) {
            Text("Tap to write.", style = type.bodyLarge, color = ink.copy(alpha = 0.35f))
            return@Column
        }
        text.split("\n").forEachIndexed { i, raw ->
            val task = Regex("""^\s*[-*] \[( |x|X)\]\s?(.*)$""").find(raw)
            val bullet = Regex("""^\s*[-*]\s+(.*)$""").find(raw)
            when {
                raw.isBlank() -> Spacer(Modifier.padding(top = 4.dp))
                raw.startsWith("#") -> {
                    val level = raw.takeWhile { it == '#' }.length
                    Text(linked(raw.drop(level).trim(), ink, onLink), style = if (level == 1) type.headlineSmall else type.titleLarge,
                        color = ink, modifier = Modifier.padding(top = 8.dp))
                }
                task != null -> {
                    val done = task.groupValues[1] != " "
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(if (done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, if (done) "Done" else "To do",
                            tint = ink, modifier = Modifier.clip(CircleShape).clickable { onToggle(i) }.padding(2.dp).size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(linked(task.groupValues[2], ink, onLink), style = type.bodyLarge.copy(
                            textDecoration = if (done) TextDecoration.LineThrough else null), color = ink.copy(alpha = if (done) 0.5f else 1f),
                            modifier = Modifier.padding(top = 1.dp))
                    }
                }
                bullet != null -> Row {
                    Text("•", style = type.bodyLarge, color = ink, modifier = Modifier.width(18.dp).padding(start = 4.dp))
                    Text(linked(bullet.groupValues[1], ink, onLink), style = type.bodyLarge, color = ink)
                }
                raw.startsWith(">") -> Row(Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.width(3.dp).heightIn(min = 20.dp).background(ink.copy(alpha = 0.3f)))
                    Text(linked(raw.drop(1).trim(), ink, onLink), style = type.bodyMedium, color = ink.copy(alpha = 0.75f),
                        modifier = Modifier.padding(start = 10.dp))
                }
                else -> Text(linked(raw, ink, onLink), style = type.bodyLarge, color = ink)
            }
        }
    }
}

private fun linked(line: String, ink: Color, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in Regex("""\[\[([^\]]+)\]\]""").findAll(line)) {
        append(line.substring(last, m.range.first))
        val name = m.groupValues[1]
        withLink(LinkAnnotation.Clickable("note:$name") { onLink(name) }) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline, color = ink)) { append(name) }
        }
        last = m.range.last + 1
    }
    append(line.substring(last))
}

/** Links out ([[mentions]] + related by meaning) and backlinks, as chips. */
@Composable
private fun Connections(links: List<NoteLink>, backlinks: List<NoteLink>, ink: Color, onOpenItem: (String) -> Unit, onOpenNote: (String) -> Unit) {
    val mentions = links.filter { it.type == "mention" }
    val related = links.filter { it.type != "mention" && mentions.none { m -> m.id == it.id } }
    val groups = listOf("Links" to mentions, "Mentioned in" to backlinks.distinctBy { it.id }, "Related" to related)
        .filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) return
    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        groups.forEach { (label, list) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { l ->
                        Text(
                            l.title, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.pressScale { if (l.isItem) onOpenItem(l.id) else onOpenNote(l.id) }
                                .clip(RoundedCornerShape(50)).background(ink.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Search your items and notes and insert a [[link]]. */
@Composable
private fun LinkPicker(names: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, names) {
        val s = q.trim()
        (if (s.isEmpty()) names else names.filter { it.first.contains(s, ignoreCase = true) }).take(60)
    }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).heightIn(max = 520.dp)) {
        Text("Link to…", style = MaterialTheme.typography.headlineSmall)
        Row(
            Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = scheme.onSurface).merge(MaterialTheme.typography.bodyLarge),
                cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> if (q.isEmpty()) Text("Search your notes and saved things", color = scheme.onSurfaceVariant); inner() })
        }
        if (q.isNotBlank() && names.none { it.first.equals(q.trim(), ignoreCase = true) }) {
            Text("Link \"${q.trim()}\" (a new name)", style = MaterialTheme.typography.bodyLarge, color = scheme.primary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(q.trim()) }.padding(12.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
            items(shown, key = { it.second + it.first }) { (name, kind) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(name) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(
                        when (kind) { "note" -> Pastels[0].ink(); "idea" -> Pastels[2].ink(); else -> kindColor(kind) }))
                    Spacer(Modifier.width(12.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(kindLabels[kind] ?: kind.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val brainstormModes = listOf(
    "expand" to "Grow it", "questions" to "Question it", "next" to "Next steps", "connect" to "Connect my saves",
)

/** Pick how Swan should help; the answer shows as a page you can add to the note (or ask again). */
@Composable
private fun Brainstorm(ensureSaved: suspend () -> String?, run: suspend (String, String) -> String, onAdd: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("expand") }
    var result by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun go() {
        working = true; error = null; result = null
        scope.launch {
            runCatching {
                val nid = ensureSaved() ?: error("Write a few words first")
                run(nid, mode)
            }.onSuccess { result = it }.onFailure { error = it.message }
            working = false
        }
    }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LotusBlob(if (working) BlobState.Thinking else if (result != null) BlobState.Wink else BlobState.Idle, size = 48.dp, onTap = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Brainstorm with Swan", style = MaterialTheme.typography.headlineSmall)
                Text(if (working) "Thinking it through with what you've saved…" else "Uses this note and the things you saved.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        PillTabs(brainstormModes, mode, { mode = it; result = null }, contentPadding = PaddingValues(0.dp))
        error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
        result?.let { md ->
            Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(20.dp))
                .background(scheme.surfaceContainerHigh).padding(16.dp)) {
                Rendered(md, scheme.onSurface, onToggle = {}, onLink = {}, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val label = brainstormModes.first { it.first == mode }.second
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (working) scheme.surfaceContainerHigh else if (result == null) scheme.inverseSurface else scheme.surfaceContainerHigh)
                    .clickable(enabled = !working) { go() }.padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(if (working) "Thinking…" else if (result == null) label else "Try again", style = MaterialTheme.typography.labelLarge,
                    color = if (result == null && !working) scheme.inverseOnSurface else scheme.onSurface)
            }
            result?.let { md ->
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(scheme.inverseSurface).clickable { onAdd(label, md) }.padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { Text("Add to note", style = MaterialTheme.typography.labelLarge, color = scheme.inverseOnSurface) }
            }
        }
    }
}
