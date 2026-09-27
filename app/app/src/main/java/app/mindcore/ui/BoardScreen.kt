package app.mindcore.ui

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.BoardCard
import app.mindcore.ui.blob.BlobState
import app.mindcore.ui.blob.LotusBlob
import app.mindcore.ui.theme.Pastels
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Brainstorm boards: an endless canvas of sticky ideas, notes and saved things. Drag cards, pinch to zoom,
// connect two cards with a line, and ask Swan for more ideas (they land next to the card they grow from).

private fun cardId() = (1..8).map { "0123456789abcdef".random() }.joinToString("")

/** Something you can drop on a board: a note or a saved item. */
data class BoardPick(val type: String, val id: String, val title: String, val kind: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    api: Api,
    boardId: String,
    picks: List<BoardPick>,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val motionOn = remember { ValueAnimator.areAnimatorsEnabled() }
    var loaded by remember(boardId) { mutableStateOf(false) }
    var error by remember(boardId) { mutableStateOf<String?>(null) }
    var title by remember(boardId) { mutableStateOf("") }
    val cards = remember(boardId) { mutableStateListOf<BoardCard>() }
    val edges = remember(boardId) { mutableStateListOf<Pair<String, String>>() }
    val sizes = remember(boardId) { mutableStateMapOf<String, IntSize>() }
    var version by remember(boardId) { mutableIntStateOf(0) } // bumps on every change; autosave watches it
    var savedVersion by remember(boardId) { mutableIntStateOf(0) }
    var selected by remember(boardId) { mutableStateOf<String?>(null) }
    var connecting by remember(boardId) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BoardCard?>(null) }
    var picking by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    // Camera: screen = offset + world * scale. World units are px at 100%.
    var view by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }

    fun changed() { version++ }
    fun center(c: BoardCard): Offset {
        val s = sizes[c.id] ?: IntSize(0, 0)
        return Offset(c.x + s.width / 2f, c.y + s.height / 2f)
    }
    fun viewCenterWorld() = Offset((view.width / 2f - ox) / scale, (view.height / 2f - oy) / scale)

    LaunchedEffect(boardId) {
        runCatching { api.board(boardId) }.onSuccess { b ->
            title = b.title; cards.clear(); cards.addAll(b.cards); edges.clear(); edges.addAll(b.edges); loaded = true
        }.onFailure { error = it.message }
    }
    // Frame everything once the board and the screen size are known.
    LaunchedEffect(loaded, view) {
        if (!loaded || view.width == 0) return@LaunchedEffect
        if (cards.isEmpty()) { scale = 1f; ox = view.width / 2f; oy = view.height / 2.6f; return@LaunchedEffect }
        delay(50) // let cards measure
        val w = with(density) { 180.dp.toPx() }
        val x0 = cards.minOf { it.x }; val x1 = cards.maxOf { it.x + (sizes[it.id]?.width ?: w.toInt()) }
        val y0 = cards.minOf { it.y }; val y1 = cards.maxOf { it.y + (sizes[it.id]?.height ?: w.toInt()) }
        val pad = with(density) { 40.dp.toPx() }
        val top = with(density) { 90.dp.toPx() }
        scale = minOf((view.width - 2 * pad) / (x1 - x0), (view.height - top - with(density) { 160.dp.toPx() }) / (y1 - y0)).coerceIn(0.3f, 1.2f)
        ox = view.width / 2f - (x0 + x1) / 2 * scale
        oy = top + (view.height - top - with(density) { 120.dp.toPx() }) / 2f - (y0 + y1) / 2 * scale
    }

    suspend fun save() {
        if (!loaded || version == savedVersion) return
        val v = version
        runCatching { api.saveBoard(boardId, title, cards.toList(), edges.toList()) }
            .onSuccess { savedVersion = v }.onFailure { note = "Not saved: ${it.message}" }
    }
    LaunchedEffect(version) { if (version != savedVersion) { delay(900); save() } }
    fun leave() = scope.launch { withContext(NonCancellable) { withTimeoutOrNull(6000) { save() } }; onBack() }
    BackHandler {
        when {
            connecting -> connecting = false
            selected != null -> selected = null
            else -> leave()
        }
    }

    fun update(id: String, f: (BoardCard) -> BoardCard) {
        val i = cards.indexOfFirst { it.id == id }
        if (i >= 0) { cards[i] = f(cards[i]); changed() }
    }
    fun addCard(c: BoardCard, from: String? = null) {
        cards += c
        from?.let { edges += it to c.id }
        changed()
    }
    fun tapCard(c: BoardCard) {
        val src = selected
        if (connecting && src != null && src != c.id) {
            val pair = src to c.id
            val existing = edges.firstOrNull { (it.first == src && it.second == c.id) || (it.first == c.id && it.second == src) }
            if (existing != null) edges.remove(existing) else edges += pair
            connecting = false; selected = c.id; changed()
        } else {
            selected = if (selected == c.id) null else c.id
            connecting = false
        }
    }

    /** Swan's ideas go in a ring around the card they grow from (or the middle of the screen). */
    fun askSwan() {
        if (thinking) return
        thinking = true; note = null
        scope.launch {
            save()
            runCatching { api.suggestCards(boardId) }.onSuccess { ideas ->
                val cardW = with(density) { 170.dp.toPx() }
                val groups = ideas.groupBy { it.second }
                groups.forEach { (from, list) ->
                    val anchor = cards.firstOrNull { it.id == from }?.let(::center) ?: viewCenterWorld()
                    val r = cardW * 1.45f
                    val start = (0..359).random() * PI / 180
                    list.forEachIndexed { k, (text, _) ->
                        val a = start + k * 2 * PI / list.size.coerceAtLeast(3)
                        addCard(
                            BoardCard(cardId(), "text", null, text, anchor.x + (r * cos(a)).toFloat() - cardW / 2,
                                anchor.y + (r * sin(a)).toFloat() - cardW / 4, color = 3),
                            from = from,
                        )
                    }
                }
                note = "Swan added ${ideas.size} ideas"
            }.onFailure { note = it.message }
            thinking = false
        }
    }

    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.surface).onSizeChanged { view = it }) {
        if (!loaded) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                LotusBlob(if (error == null) BlobState.Thinking else BlobState.Alert, size = 72.dp, onTap = null)
                error?.let { Text(it, color = scheme.error, modifier = Modifier.padding(top = 12.dp)) }
            }
        } else {
            // The canvas: dot grid + lines. Empty space pans/zooms; a tap on it clears the selection.
            val dot = scheme.onSurface.copy(alpha = 0.10f)
            val line = scheme.onSurface
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(boardId) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val s = (scale * zoom).coerceIn(0.25f, 2.5f)
                            val z = s / scale
                            ox = centroid.x - (centroid.x - ox) * z + pan.x
                            oy = centroid.y - (centroid.y - oy) * z + pan.y
                            scale = s
                        }
                    }
                    .pointerInput(boardId) { detectTapGestures(onTap = { selected = null; connecting = false }) },
            ) {
                val step = 32.dp.toPx() * scale
                if (step > 8f) {
                    var x = ox % step; while (x < size.width) { var y = oy % step; while (y < size.height) { drawCircle(dot, 1.3.dp.toPx(), Offset(x, y)); y += step }; x += step }
                }
                val byId = cards.associateBy { it.id }
                edges.forEach { (a, b) ->
                    val ca = byId[a] ?: return@forEach; val cb = byId[b] ?: return@forEach
                    val p = center(ca); val q = center(cb)
                    val lit = selected == a || selected == b
                    drawLine(line, Offset(ox + p.x * scale, oy + p.y * scale), Offset(ox + q.x * scale, oy + q.y * scale),
                        strokeWidth = (if (lit) 2.2f else 1.4f) * density.density, alpha = if (lit) 0.7f else 0.3f, cap = StrokeCap.Round)
                }
            }
            // Cards live in world space: placed and scaled with the camera.
            cards.forEach { c ->
                key(c.id) {
                    val appear = remember { Animatable(if (motionOn) 0.85f else 1f) }
                    LaunchedEffect(Unit) { appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
                    Box(
                        Modifier
                            .offset { IntOffset((ox + c.x * scale).toInt(), (oy + c.y * scale).toInt()) }
                            .graphicsLayer { scaleX = scale * appear.value; scaleY = scale * appear.value; transformOrigin = TransformOrigin(0f, 0f); alpha = appear.value.coerceIn(0f, 1f) }
                            .onSizeChanged { sizes[c.id] = it }
                            .pointerInput(c.id) {
                                detectDragGestures(onDragStart = { selected = c.id }) { change, drag ->
                                    change.consume()
                                    // Drag deltas arrive in the card's own (scaled) space, so they're already world units.
                                    update(c.id) { it.copy(x = it.x + drag.x, y = it.y + drag.y) }
                                }
                            }
                            .pointerInput(c.id) { detectTapGestures(onTap = { tapCard(c) }, onDoubleTap = { if (c.type == "text") editing = cards.firstOrNull { it.id == c.id } }) },
                    ) {
                        BoardCardView(c, selected == c.id, connecting && selected == c.id)
                    }
                }
            }
        }

        // Top bar: back, title, Swan's status.
        Row(Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { leave() }, size = 44.dp)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (title.isEmpty()) Text("Name this board", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurfaceVariant)
                BasicTextField(title, { title = it.replace("\n", " "); changed() }, singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth())
            }
        }
        note?.let {
            LaunchedEffect(it) { delay(3500); note = null }
            Text(it, style = MaterialTheme.typography.labelLarge, color = scheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                    .clip(RoundedCornerShape(50)).background(scheme.inverseSurface).padding(horizontal = 16.dp, vertical = 9.dp))
        }
        if (loaded && cards.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LotusBlob(BlobState.Idle, size = 72.dp)
                Text("Drop a first thought, add things you saved, or let Swan start you off.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            }
        }

        // Bottom: card actions when one is selected, otherwise the add tools.
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
            val sel = cards.firstOrNull { it.id == selected }
            AnimatedVisibility(sel != null, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
                var last by remember { mutableStateOf<BoardCard?>(null) }
                if (sel != null) last = sel
                val c = last ?: return@AnimatedVisibility
                ToolBar {
                    if (connecting) {
                        Text("Tap another card to connect or unlink", style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                        Tool(null, "Cancel") { connecting = false }
                    } else {
                        if (c.type == "text") Tool(Icons.Rounded.Edit, "Edit") { editing = cards.firstOrNull { it.id == c.id } }
                        else Tool(Icons.AutoMirrored.Rounded.OpenInNew, "Open") {
                            scope.launch { save(); c.refId?.let { if (c.type == "note") onOpenNote(it) else onOpenItem(it) } }
                        }
                        Tool(Icons.Rounded.Link, "Connect") { connecting = true }
                        if (c.type != "item") Tool(Icons.Rounded.Palette, "Colour") { update(c.id) { it.copy(color = (it.color + 1) % Pastels.size) } }
                        Tool(Icons.Rounded.Delete, "Remove") {
                            cards.removeAll { it.id == c.id }; edges.removeAll { it.first == c.id || it.second == c.id }
                            selected = null; changed()
                        }
                    }
                }
            }
            AnimatedVisibility(sel == null, enter = fadeIn() + scaleIn(initialScale = 0.95f), exit = fadeOut()) {
                ToolBar {
                    Tool(Icons.Rounded.Add, "Sticky") {
                        val p = viewCenterWorld()
                        val c = BoardCard(cardId(), "text", null, "", p.x - with(density) { 85.dp.toPx() } + (-40..40).random(), p.y - 40 + (-40..40).random(), (0 until Pastels.size).random())
                        editing = c
                    }
                    Tool(Icons.Rounded.PostAdd, "Add saved") { picking = true }
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(scheme.inverseSurface).clickable(enabled = !thinking) { askSwan() }
                            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LotusBlob(if (thinking) BlobState.Thinking else BlobState.Idle, size = 30.dp, onTap = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (thinking) "Thinking…" else "Swan, add ideas", style = MaterialTheme.typography.labelLarge, color = scheme.inverseOnSurface)
                    }
                }
            }
        }
    }

    editing?.let { c ->
        var text by remember(c.id) { mutableStateOf(c.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (cards.any { it.id == c.id }) "Edit card" else "New card") },
            text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth(), placeholder = { Text("A thought, a question, a step…") }) },
            confirmButton = {
                TextButton({
                    val t = text.trim()
                    if (t.isNotEmpty()) {
                        if (cards.any { it.id == c.id }) update(c.id) { it.copy(text = t) } else { addCard(c.copy(text = t)); selected = c.id }
                    }
                    editing = null
                }) { Text("Done") }
            },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } },
        )
    }
    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            PickSheet(picks.filter { p -> cards.none { it.refId == p.id } }) { p ->
                val at = viewCenterWorld()
                addCard(BoardCard(cardId(), p.type, p.id, "", at.x - with(density) { 85.dp.toPx() } + (-60..60).random(), at.y + (-60..60).random(),
                    0, title = p.title, kind = p.kind))
                picking = false
            }
        }
    }
}

@Composable
private fun ToolBar(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun Tool(icon: ImageVector?, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BoardCardView(c: BoardCard, selected: Boolean, connecting: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val p = Pastels[c.color.mod(Pastels.size)]
    val (bg, ink) = if (c.type == "item") scheme.surfaceContainerHigh to scheme.onSurface else p.bg() to p.ink()
    val ring = when { connecting -> scheme.primary; selected -> ink; else -> Color.Transparent }
    Column(
        Modifier.width(170.dp).clip(RoundedCornerShape(18.dp)).background(bg).border(2.dp, ring, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (c.type) {
            "text" -> Text(c.text, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 8, overflow = TextOverflow.Ellipsis)
            "note" -> {
                Text(if (c.kind == "idea") "Idea" else "Note", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f))
                Text(c.title?.ifBlank { null } ?: "Untitled", style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(kindColor(c.kind ?: "")))
                    Spacer(Modifier.width(6.dp))
                    Text(kindLabels[c.kind] ?: "Saved", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                Text(c.title ?: "(removed)", style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                c.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun PickSheet(picks: List<BoardPick>, onPick: (BoardPick) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, picks) { (if (q.isBlank()) picks else picks.filter { it.title.contains(q.trim(), ignoreCase = true) }).take(80) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).heightIn(max = 560.dp)) {
        Text("Add to the board", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp)) {
            BasicTextField(q, { q = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> if (q.isEmpty()) Text("Search notes and saved things", color = scheme.onSurfaceVariant); inner() })
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
            items(shown, key = { it.type + it.id }) { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(p) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(
                        when (p.kind) { "note" -> Pastels[0].ink(); "idea" -> Pastels[2].ink(); else -> kindColor(p.kind) }))
                    Spacer(Modifier.width(12.dp))
                    Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (p.type == "note") (if (p.kind == "idea") "Idea" else "Note") else kindLabels[p.kind] ?: p.kind,
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
