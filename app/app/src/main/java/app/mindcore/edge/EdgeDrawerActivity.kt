package app.mindcore.edge

import android.Manifest
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.mindcore.MainActivity
import app.mindcore.R
import app.mindcore.ShareActivity
import app.mindcore.data.Api
import app.mindcore.data.Capture
import app.mindcore.data.Capturer
import app.mindcore.settings.AppSettings
import app.mindcore.settings.SettingsStore
import app.mindcore.ui.theme.colorSchemeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The edge drawer. It floats over other apps, so it can't bend their pixels the way in-app liquid glass does
 * (Android doesn't let an app sample another app's window). Instead: the system blurs the app behind
 * (FLAG_BLUR_BEHIND), and the panel is drawn as glass on top of that: translucent tint, a bright rim that
 * fades around the edge, a soft top sheen and depth shading.
 */
class EdgeDrawerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        if (Build.VERSION.SDK_INT >= 31) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 70 }
        }
        val right = intent.getBooleanExtra(EXTRA_RIGHT, true)
        setContent {
            val store = remember { SettingsStore(applicationContext) }
            val settings by store.settings.collectAsState(initial = AppSettings())
            MaterialTheme(colorScheme = colorSchemeFor(settings)) {
                Drawer(settings, right, context = this, finish = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_RIGHT = "right"
    }
}

private val DrawerWidth = 336.dp

@Composable
private fun Drawer(settings: AppSettings, right: Boolean, context: Context, finish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val widthPx = with(density) { (DrawerWidth + 24.dp).toPx() }
    val side = if (right) 1f else -1f
    // 0 = closed (off screen), 1 = open. A spring drives it; your finger drives it while dragging.
    val open = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 380f)) }
    fun close(then: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch {
            open.animateTo(0f, spring(dampingRatio = 1f, stiffness = 700f))
            then()
            finish()
        }
    }
    BackHandler { close() }
    val capturer = remember(settings.apiToken) {
        if (settings.apiToken.isBlank()) null else Capturer(context.applicationContext, Api(settings.serverUrl, settings.apiToken))
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = open.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(MutableInteractionSource(), indication = null) { close() },
        contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            Modifier
                .width(DrawerWidth).fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(12.dp)
                .graphicsLayer {
                    val p = open.value
                    translationX = side * (1f - p) * widthPx
                    val s = 0.94f + 0.06f * p.coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (right) 1f else 0f, 0.5f)
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (open.value < 0.75f) close()
                            else scope.launch { open.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                        },
                    ) { _, dx ->
                        // Dragging toward the edge closes it, and the panel follows your finger.
                        scope.launch { open.snapTo((open.value - side * dx / widthPx).coerceIn(0f, 1.04f)) }
                    }
                }
                .glassPanel(RoundedCornerShape(36.dp))
                .clickable(MutableInteractionSource(), indication = null) {} // taps inside don't close it
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Stagger(0, open.value) { Header { close() } }
            if (capturer == null) {
                Text("Pair this phone first: run mindcore pair on the laptop.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            Stagger(1, open.value) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(Icons.Rounded.Mic, "Voice note", Modifier.weight(1f)) {
                        close { context.startActivity(capture(context, ShareActivity.MODE_VOICE)) }
                    }
                    ActionTile(Icons.Rounded.EditNote, "Note", Modifier.weight(1f)) { close { context.startActivity(capture(context, null)) } }
                    ActionTile(Icons.Rounded.OpenInNew, "Open app", Modifier.weight(1f)) {
                        close { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
            Stagger(2, open.value) { Copied(capturer, context) }
            Stagger(3, open.value) { Screenshots(capturer, context) }
        }
    }
}

/** Glass drawn over the system blur: tint, top sheen, a rim that's brightest at the top-left, and depth. */
private fun Modifier.glassPanel(shape: RoundedCornerShape) = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(Color(0xFF2A2327).copy(alpha = 0.62f), Color(0xFF141012).copy(alpha = 0.72f)),
        ),
    )
    .drawWithContent {
        drawContent()
        // Soft sheen across the top, like light catching the glass.
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), endY = size.height * 0.22f))
    }
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.18f)),
            start = Offset.Zero, end = Offset.Infinite,
        ),
        shape,
    )

/** Content slides and fades in one after another as the drawer opens. */
@Composable
private fun Stagger(index: Int, progress: Float, content: @Composable () -> Unit) {
    val start = 0.25f + index * 0.12f
    val t = ((progress - start) / (1f - start)).coerceIn(0f, 1f)
    Box(Modifier.graphicsLayer { alpha = t; translationY = (1f - t) * 24.dp.toPx() }) { content() }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("mind-core", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Save without leaving this app", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
        }
        GlassIconButton(Icons.Rounded.Close, "Close", onClose)
    }
}

@Composable
private fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).pressScale(onClick).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

/** Control-Center style tile: icon on a tinted glass square, label under it. */
@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.pressScale(onClick).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun GlassCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
        }
        content()
    }
}

/** What's on the clipboard now. Readable here because this drawer is the app you're looking at. */
@Composable
private fun Copied(capturer: Capturer, context: Context) {
    val clip = remember {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
    }
    var state by remember { mutableStateOf<String?>(null) } // null idle, "…" saving, else result
    val scope = rememberCoroutineScope()
    GlassCard(Icons.Rounded.ContentPaste, "You copied") {
        if (clip.isNullOrBlank()) {
            Text("Nothing copied right now.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
            return@GlassCard
        }
        Text(clip.take(220), style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
        AnimatedContent(state, label = "save") { s ->
            when (s) {
                null -> PrimaryPill("Save to mind-core") {
                    state = "…"
                    scope.launch { state = runCatching { capturer.save(Capture.fromText(clip), "").message }.getOrElse { it.message ?: "Couldn't save" } }
                }
                "…" -> Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                }
                else -> Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(s, color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun PrimaryPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).pressScale(onClick).clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.primary).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

/** Your latest screenshots; tap one to save it. Asks for photo access the first time. */
@Composable
private fun Screenshots(capturer: Capturer, context: Context) {
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(setOf<Uri>()) }
    GlassCard(Icons.Rounded.Image, "Latest screenshots") {
        if (!granted) {
            PrimaryPill("Show my screenshots") { ask.launch(perm) }
            return@GlassCard
        }
        var shots by remember { mutableStateOf<List<Uri>?>(null) }
        LaunchedEffect(Unit) { shots = withContext(Dispatchers.IO) { latestScreenshots(context, 10) } }
        when {
            shots == null -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            shots!!.isEmpty() -> Text("No screenshots yet.", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shots!!) { uri -> Shot(uri, uri in saved, context) {
                    saved = saved + uri
                    scope.launch { runCatching { capturer.save(Capture(images = listOf(uri)), "") } }
                } }
            }
        }
    }
}

@Composable
private fun Shot(uri: Uri, saved: Boolean, context: Context, onSave: () -> Unit) {
    var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ -> d.setTargetSampleSize(6) }.asImageBitmap()
            }.getOrNull()
        }
    }
    val dim by animateFloatAsState(if (saved) 1f else 0f, tween(250), label = "saved")
    Box(
        Modifier.width(84.dp).aspectRatio(9f / 18f).pressScale(onClick = { if (!saved) onSave() }).clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(it, contentDescription = "Screenshot", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (dim > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Saved", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

/** Press feedback: a quick spring down to 95%. */
@Composable
private fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f), label = "press")
    return graphicsLayer { scaleX = s; scaleY = s }.clickable(source, indication = null, onClick = onClick)
}

private fun latestScreenshots(context: Context, limit: Int): List<Uri> {
    val out = mutableListOf<Uri>()
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("%Screenshots%"),
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { c -> while (c.moveToNext() && out.size < limit) out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) }
    return out
}

private fun capture(context: Context, mode: String?): Intent =
    Intent(context, ShareActivity::class.java).setAction(ShareActivity.ACTION_CAPTURE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { mode?.let { putExtra(ShareActivity.EXTRA_MODE, it) } }
