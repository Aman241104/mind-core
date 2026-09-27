package app.mindcore.ui

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.Graph
import app.mindcore.ui.blob.BlobState
import app.mindcore.ui.blob.LotusBlob
import app.mindcore.ui.theme.Pastels
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Graph view, like Obsidian's: everything you saved or wrote is a dot, lines are how they connect. A small
// force-directed layout (d3-force's model: many-body repulsion, spring links, a pull to the centre) settles live.
// Pinch to zoom, drag to pan, drag a dot to pull it around, tap one to see its neighbourhood.

/** Force layout over plain arrays; ~300 nodes costs about a millisecond a tick. */
private class Layout(val graph: Graph) {
    val n = graph.nodes.size
    private val index = graph.nodes.withIndex().associate { it.value.id to it.index }
    val src = IntArray(graph.edges.size) { index.getValue(graph.edges[it].a) }
    val dst = IntArray(graph.edges.size) { index.getValue(graph.edges[it].b) }
    val type = Array(graph.edges.size) { graph.edges[it].type }
    val degree = IntArray(n).also { d -> src.forEach { d[it]++ }; dst.forEach { d[it]++ } }
    val neighbors: Array<IntArray> = run {
        val lists = Array(n) { ArrayList<Int>() }
        for (e in src.indices) { lists[src[e]].add(dst[e]); lists[dst[e]].add(src[e]) }
        Array(n) { i -> lists[i].sortedByDescending { degree[it] }.toIntArray() }
    }
    val radius = FloatArray(n) { 7f + sqrt(degree[it].toFloat()) * 3.2f }
    val x = FloatArray(n)
    val y = FloatArray(n)
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)
    var active = BooleanArray(n) { true }
    var activeEdge = BooleanArray(src.size) { true }
    var alpha = 1f
    var pinned = -1
    val wake = Channel<Unit>(Channel.CONFLATED)

    init { // d3's phyllotaxis start: spread out evenly, no randomness, same picture every time
        val golden = PI * (3 - sqrt(5.0))
        for (i in 0 until n) {
            val r = 14f * sqrt(0.5f + i)
            x[i] = r * cos(i * golden).toFloat(); y[i] = r * sin(i * golden).toFloat()
        }
    }

    fun reheat(to: Float) { alpha = max(alpha, to); wake.trySend(Unit) }

    fun pin(i: Int, wx: Float, wy: Float) { pinned = i; x[i] = wx; y[i] = wy; vx[i] = 0f; vy[i] = 0f; reheat(0.3f) }

    fun tick() {
        // Springs: your own links pull hardest and shortest, "similar" is loose and long.
        for (e in src.indices) {
            if (!activeEdge[e]) continue
            val i = src[e]; val j = dst[e]
            var dx = x[j] + vx[j] - x[i] - vx[i]
            var dy = y[j] + vy[j] - y[i] - vy[i]
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val (rest, pull) = when (type[e]) { "mention" -> 50f to 1f; "similar" -> 120f to 0.35f; "related" -> 90f to 0.5f; else -> 70f to 0.7f }
            val f = (len - rest) / len * alpha * pull / min(degree[i], degree[j]).coerceAtLeast(1)
            dx *= f; dy *= f
            val b = degree[i].toFloat() / (degree[i] + degree[j])
            vx[j] -= dx * b; vy[j] -= dy * b
            vx[i] += dx * (1 - b); vy[i] += dy * (1 - b)
        }
        // Everything pushes everything away (falls off with distance).
        for (i in 0 until n) {
            if (!active[i]) continue
            for (j in i + 1 until n) {
                if (!active[j]) continue
                val dx = x[j] - x[i]; val dy = y[j] - y[i]
                val l2 = (dx * dx + dy * dy).coerceAtLeast(1f)
                val w = -70f * alpha / l2
                vx[i] += dx * w; vy[i] += dy * w
                vx[j] -= dx * w; vy[j] -= dy * w
            }
        }
        for (i in 0 until n) {
            if (!active[i]) continue
            vx[i] -= x[i] * 0.045f * alpha; vy[i] -= y[i] * 0.045f * alpha // keeps loners from drifting off
            if (i == pinned) { vx[i] = 0f; vy[i] = 0f; continue }
            vx[i] *= 0.6f; vy[i] *= 0.6f
            x[i] += vx[i]; y[i] += vy[i]
        }
        alpha += (0f - alpha) * 0.0228f // ~300 ticks from hot to settled
    }

    val settled get() = alpha < 0.004f && pinned < 0
}

@Composable
fun GraphScreen(api: Api, onBack: () -> Unit, onOpen: (String) -> Unit, focus: String? = null) {
    var graph by remember { mutableStateOf<Graph?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { api.graph() }.onSuccess { graph = it }.onFailure { error = it.message } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val g = graph
        if (g == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                LotusBlob(if (error == null) BlobState.Orbit else BlobState.Alert, size = 88.dp, onTap = null)
                Spacer(Modifier.height(16.dp))
                Text(error ?: "Mapping how everything connects…", style = MaterialTheme.typography.bodyMedium,
                    color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
        } else {
            GraphCanvas(g, focus, onOpen)
        }
        TopBar(g, onBack)
    }
}

// Search + filters live in GraphCanvas; the bar here is just back + title, so it shows while loading too.
@Composable
private fun TopBar(g: Graph?, onBack: () -> Unit) {
    Row(Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 44.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text("Graph", style = MaterialTheme.typography.headlineSmall)
            if (g != null) Text("${g.nodes.size} things · ${g.edges.size} links", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GraphCanvas(g: Graph, focus: String?, onOpen: (String) -> Unit) {
    val layout = remember(g) { Layout(g) }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motionOn = remember { ValueAnimator.areAnimatorsEnabled() }

    // Colours per kind; notes and ideas use the pastel note colours.
    val kinds = remember(g) { g.nodes.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value } }
    val colors = HashMap<String, Color>().apply {
        kinds.forEach { (k, _) ->
            put(k, when (k) { "note" -> Pastels[0].let { if (dark) it.light else it.inkLight }; "idea" -> Pastels[2].let { if (dark) it.light else it.inkLight }; else -> kindColor(k) })
        }
    }

    var hidden by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showSimilar by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableIntStateOf(-1) }

    // Camera: screen = offset + world * scale.
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var userMoved by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) } // bumps each tick so the canvas redraws

    fun fit(smooth: Float) {
        if (size.width == 0) return
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in 0 until layout.n) if (layout.active[i]) {
            x0 = min(x0, layout.x[i]); x1 = max(x1, layout.x[i]); y0 = min(y0, layout.y[i]); y1 = max(y1, layout.y[i])
        }
        if (x0 > x1) return
        val padTop = with(density) { 150.dp.toPx() }
        val pad = with(density) { 32.dp.toPx() }
        val w = size.width - 2 * pad
        val h = size.height - padTop - pad
        val s = min(w / (x1 - x0 + 1), h / (y1 - y0 + 1)).coerceIn(0.15f, 2.5f)
        val tx = size.width / 2f - (x0 + x1) / 2 * s
        val ty = padTop + h / 2f - (y0 + y1) / 2 * s
        scale += (s - scale) * smooth; ox += (tx - ox) * smooth; oy += (ty - oy) * smooth
    }

    /** Glide the camera so node [i] sits in the upper-middle (the detail card covers the bottom). */
    fun focusOn(i: Int) {
        userMoved = true
        val s0 = scale; val x0 = ox; val y0 = oy
        val s1 = max(scale, 1.1f)
        val tx = size.width / 2f - layout.x[i] * s1
        val ty = size.height * 0.42f - layout.y[i] * s1
        scope.launch {
            val t = Animatable(0f)
            t.animateTo(1f, if (motionOn) tween(450, easing = FastOutSlowInEasing) else tween(0)) {
                scale = s0 + (s1 - s0) * value; ox = x0 + (tx - x0) * value; oy = y0 + (ty - y0) * value
            }
        }
    }

    // Filters change which dots take part; the layout re-settles around what's left.
    LaunchedEffect(hidden, showSimilar) {
        layout.active = BooleanArray(layout.n) { g.nodes[it].kind !in hidden }
        layout.activeEdge = BooleanArray(layout.src.size) {
            layout.active[layout.src[it]] && layout.active[layout.dst[it]] && (showSimilar || layout.type[it] != "similar")
        }
        if (selected >= 0 && !layout.active[selected]) selected = -1
        userMoved = false
        layout.reheat(if (frame == 0) 1f else 0.5f)
    }

    // The simulation: two ticks a frame while hot, then sleeps until something reheats it.
    LaunchedEffect(layout) {
        if (!motionOn) {
            repeat(320) { layout.tick() }
            layout.alpha = 0f
            fit(1f); frame++
        }
        while (isActive) {
            if (layout.settled) {
                layout.wake.receive()
                if (!motionOn) { repeat(200) { layout.tick() }; layout.alpha = 0f; if (!userMoved) fit(1f); frame++; continue }
            }
            androidx.compose.runtime.withFrameNanos { }
            layout.tick(); layout.tick()
            if (!userMoved) fit(if (frame < 2) 1f else 0.12f)
            frame++
        }
    }
    LaunchedEffect(layout, focus) {
        val i = g.nodes.indexOfFirst { it.id == focus }
        if (i >= 0) { selected = i; kotlinx.coroutines.delay(900); focusOn(i) }
    }

    val matches = remember(query, g) {
        if (query.isBlank()) emptySet() else g.nodes.indices.filter { g.nodes[it].label.contains(query.trim(), ignoreCase = true) }.toSet()
    }
    val neighborhood = if (selected >= 0) layout.neighbors[selected].toSet() + selected else emptySet()
    val lit: Set<Int>? = when { selected >= 0 -> neighborhood; matches.isNotEmpty() -> matches; else -> null }

    fun hitTest(p: Offset): Int {
        val minR = with(density) { 22.dp.toPx() }
        var best = -1; var bestD = Float.MAX_VALUE
        for (i in 0 until layout.n) {
            if (!layout.active[i]) continue
            val dx = ox + layout.x[i] * scale - p.x; val dy = oy + layout.y[i] * scale - p.y
            val d = sqrt(dx * dx + dy * dy)
            if (d < max(layout.radius[i] * scale, minR) && d < bestD) { best = i; bestD = d }
        }
        return best
    }

    val measurer = rememberTextMeasurer(cacheSize = 64)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurface)
    val labels = remember(g, labelStyle) { HashMap<Int, TextLayoutResult>() }
    val edgeColor = scheme.onSurface
    val ring = scheme.surface

    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(layout) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    userMoved = true
                    val s = (scale * zoom).coerceIn(0.1f, 6f)
                    val z = s / scale
                    ox = centroid.x - (centroid.x - ox) * z + pan.x
                    oy = centroid.y - (centroid.y - oy) * z + pan.y
                    scale = s
                }
            }
            .pointerInput(layout) {
                detectTapGestures(
                    onTap = { p -> val i = hitTest(p); selected = if (i == selected) -1 else i; if (i >= 0) focusOn(i) },
                    onDoubleTap = { p ->
                        userMoved = true
                        val s0 = scale; val x0 = ox; val y0 = oy; val s1 = (scale * 2f).coerceAtMost(6f)
                        scope.launch {
                            Animatable(0f).animateTo(1f, tween(if (motionOn) 300 else 0, easing = FastOutSlowInEasing)) {
                                val s = s0 + (s1 - s0) * value
                                scale = s; ox = p.x - (p.x - x0) * (s / s0); oy = p.y - (p.y - y0) * (s / s0)
                            }
                        }
                    },
                )
            }
            // Last in the chain, so it sees touches first: a drag that starts on a dot moves the dot, not the view.
            .pointerInput(layout) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val i = hitTest(down.position)
                    if (i < 0) return@awaitEachGesture
                    val start = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() } ?: return@awaitEachGesture
                    layout.pin(i, (start.position.x - ox) / scale, (start.position.y - oy) / scale)
                    drag(start.id) { c ->
                        layout.pin(i, (c.position.x - ox) / scale, (c.position.y - oy) / scale)
                        c.consume()
                    }
                    layout.pinned = -1
                    layout.reheat(0.1f)
                }
            },
    ) {
        frame // read, so each tick redraws
        val px = 1.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(4 * px, 4 * px))
        // Lines first, then dots on top.
        for (e in layout.src.indices) {
            if (!layout.activeEdge[e]) continue
            val i = layout.src[e]; val j = layout.dst[e]
            val on = lit == null || (selected >= 0 && (i == selected || j == selected)) || (selected < 0 && i in lit && j in lit)
            val similar = layout.type[e] == "similar"
            val a = when { selected >= 0 && on -> 0.75f; lit != null -> 0.05f; similar -> 0.16f; else -> 0.26f }
            drawLine(
                color = if (selected >= 0 && on) colors[g.nodes[selected].kind] ?: edgeColor else edgeColor,
                start = Offset(ox + layout.x[i] * scale, oy + layout.y[i] * scale),
                end = Offset(ox + layout.x[j] * scale, oy + layout.y[j] * scale),
                strokeWidth = (if (layout.type[e] == "mention") 1.8f else 1.1f) * px * (if (selected >= 0 && on) 1.5f else 1f),
                alpha = a, cap = StrokeCap.Round, pathEffect = if (similar) dash else null,
            )
        }
        val labelZoom = 11.dp.toPx() // a dot gets its name once it's drawn at least this big
        for (i in 0 until layout.n) {
            if (!layout.active[i]) continue
            val cx = ox + layout.x[i] * scale; val cy = oy + layout.y[i] * scale
            val r = (layout.radius[i] * scale).coerceAtLeast(2.5f * px)
            if (cx < -r - 200 || cx > size.width + r + 200 || cy < -r - 40 || cy > size.height + r + 40) continue
            val node = g.nodes[i]
            val c = colors[node.kind] ?: scheme.outline
            val dim = lit != null && i !in lit
            if (i == selected) drawCircle(c, r + 9 * px, Offset(cx, cy), alpha = 0.22f)
            drawCircle(c, r, Offset(cx, cy), alpha = if (dim) 0.18f else 1f)
            drawCircle(ring, r, Offset(cx, cy), style = Stroke(1.5f * px), alpha = if (dim) 0.2f else 0.9f)
            if (node.favorite && !dim) drawCircle(c, r + 4 * px, Offset(cx, cy), style = Stroke(1.2f * px))

            val named = i == selected || (lit != null && i in lit) || (lit == null && r >= labelZoom)
            if (named) {
                val t = labels.getOrPut(i) {
                    measurer.measure(node.label, labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = 150.dp.roundToPx()))
                }
                drawText(t, topLeft = Offset(cx - t.size.width / 2f, cy + r + 4 * px), alpha = if (i == selected) 1f else 0.85f)
            }
        }
    }

    // Top: search + filters (under the title bar).
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to scheme.surface, 0.8f to scheme.surface.copy(alpha = 0.92f), 1f to Color.Transparent))
            .statusBarsPadding().padding(top = 60.dp, bottom = 16.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Find a dot", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    BasicTextField(
                        query, { query = it; selected = -1 }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            matches.maxByOrNull { layout.degree[it] }?.let { selected = it; focusOn(it) }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Text(if (matches.isEmpty()) "none" else "${matches.size}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Icon(Icons.Rounded.Close, "Clear search", Modifier.padding(start = 6.dp).size(20.dp).clip(CircleShape).pressScale { query = "" },
                        tint = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Rounded.CenterFocusStrong, "Fit everything", { selected = -1; query = ""; userMoved = false; layout.reheat(0.05f) }, size = 44.dp)
        }
        Row(
            Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            kinds.forEach { (k, count) ->
                val on = k !in hidden
                FilterDot(labelFor(k), count, colors[k] ?: scheme.outline, on) { hidden = if (on) hidden + k else hidden - k }
            }
            FilterDot("Similar links", null, scheme.onSurfaceVariant, showSimilar, dashed = true) { showSimilar = !showSimilar }
        }
    }

    // Bottom: the tapped dot and what it connects to.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = selected >= 0,
            enter = slideInVertically(tween(280)) { it / 2 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(160)),
        ) {
            var last by remember { mutableIntStateOf(0) }
            if (selected >= 0) last = selected
            val i = last
            val node = g.nodes[i]
            val c = colors[node.kind] ?: scheme.outline
            Column(
                Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainerHigh).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(node.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(labelFor(node.kind), node.trust?.takeIf { !node.isNote }?.let(::trustLabel),
                            "${layout.degree[i]} ${if (layout.degree[i] == 1) "link" else "links"}")
                        Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                    CircleIconButton(Icons.Rounded.Close, "Close", { selected = -1 }, size = 36.dp)
                }
                val near = layout.neighbors[i].filter { layout.active[it] }
                if (near.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        near.take(16).forEach { j ->
                            val nj = g.nodes[j]
                            Row(
                                Modifier.pressScale { selected = j; focusOn(j) }.clip(RoundedCornerShape(50))
                                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(colors[nj.kind] ?: scheme.outline))
                                Spacer(Modifier.width(6.dp))
                                Text(nj.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 150.dp))
                            }
                        }
                    }
                } else {
                    Text("Nothing linked yet. Save more around this and it'll connect.", style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant)
                }
                if (!node.isNote) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).pressScale { onOpen(node.id) }.clip(RoundedCornerShape(50))
                            .background(scheme.inverseSurface),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) { Text("Open", style = MaterialTheme.typography.labelLarge, color = scheme.inverseOnSurface) }
                }
            }
        }
    }
}

private fun labelFor(kind: String) = when (kind) {
    "note" -> "Note"; "idea" -> "Idea"
    else -> kindLabels[kind] ?: kind.replaceFirstChar { it.uppercase() }
}

@Composable
private fun FilterDot(label: String, count: Int?, color: Color, on: Boolean, dashed: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.pressScale(onClick).clip(RoundedCornerShape(50))
            .background(if (on) scheme.surfaceContainerHigh else Color.Transparent)
            .border(1.dp, if (on) Color.Transparent else scheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dashed) {
            Canvas(Modifier.width(14.dp).height(8.dp)) {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())), alpha = if (on) 1f else 0.4f)
            }
        } else {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color.copy(alpha = if (on) 1f else 0.3f)))
        }
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) scheme.onSurface else scheme.onSurfaceVariant)
        if (count != null) Text(" $count", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}
