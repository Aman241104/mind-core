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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.mindcore.MainActivity
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

/** The edge drawer: slides in over whatever app you're in, with the app behind it blurred. */
class EdgeDrawerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) {
            // Real blur of the app behind the drawer (the system does it; no screen reading involved).
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 60 }
        }
        val right = intent.getBooleanExtra(EXTRA_RIGHT, true)
        setContent {
            val store = remember { SettingsStore(applicationContext) }
            val settings by store.settings.collectAsState(initial = AppSettings())
            MaterialTheme(colorScheme = colorSchemeFor(settings)) {
                Drawer(settings, right, onClose = { finish() }, context = this)
            }
        }
    }

    companion object {
        const val EXTRA_RIGHT = "right"
    }
}

@Composable
private fun Drawer(settings: AppSettings, right: Boolean, onClose: () -> Unit, context: Context) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val capturer = remember(settings.apiToken) {
        if (settings.apiToken.isBlank()) null else Capturer(context.applicationContext, Api(settings.serverUrl, settings.apiToken))
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
            .clickable(MutableInteractionSource(), indication = null, onClick = onClose),
        contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        AnimatedVisibility(shown, enter = slideInHorizontally(tween(260)) { if (right) it else -it }) {
            Column(
                Modifier.width(330.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(10.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f))
                    .clickable(MutableInteractionSource(), indication = null) {} // taps inside don't close it
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("mind-core", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (capturer == null) {
                    Text("Pair this phone first (mindcore pair on the laptop).", color = MaterialTheme.colorScheme.error)
                    return@Column
                }
                Copied(capturer, context)
                Screenshots(capturer, context)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { context.startActivity(capture(context, ShareActivity.MODE_VOICE)); onClose() }) { Text("🎙 Voice") }
                    OutlinedButton(onClick = { context.startActivity(capture(context, null)); onClose() }) { Text("✏️ Note") }
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onClose()
                }, modifier = Modifier.fillMaxWidth()) { Text("Open mind-core") }
            }
        }
    }
}

/** What's on the clipboard now. Readable here because this drawer is the app you're looking at. */
@Composable
private fun Copied(capturer: Capturer, context: Context) {
    val clip = remember {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
    }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Section("You copied") {
        if (clip.isNullOrBlank()) {
            Text("Nothing copied right now.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Section
        }
        Text(clip.take(220), style = MaterialTheme.typography.bodyMedium, maxLines = 5)
        Button(onClick = {
            status = "Saving…"
            scope.launch { status = runCatching { capturer.save(Capture.fromText(clip), "").message }.getOrElse { it.message } }
        }, enabled = status == null) { Text(status ?: "Save to mind-core") }
    }
}

/** Your latest screenshots; tap one to save it. Asks for photo access the first time. */
@Composable
private fun Screenshots(capturer: Capturer, context: Context) {
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(setOf<Uri>()) }
    Section("Latest screenshots") {
        if (!granted) {
            OutlinedButton(onClick = { ask.launch(perm) }) { Text("Show my screenshots") }
            return@Section
        }
        var shots by remember { mutableStateOf<List<Uri>>(emptyList()) }
        LaunchedEffect(Unit) { shots = withContext(Dispatchers.IO) { latestScreenshots(context, 8) } }
        if (shots.isEmpty()) Text("No screenshots found.", style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shots) { uri ->
                var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(uri) {
                    bmp = withContext(Dispatchers.IO) {
                        runCatching {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ -> d.setTargetSampleSize(6) }
                                .asImageBitmap()
                        }.getOrNull()
                    }
                }
                Box(
                    Modifier.size(width = 76.dp, height = 136.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(enabled = uri !in saved) {
                            saved = saved + uri
                            scope.launch { runCatching { capturer.save(Capture(images = listOf(uri)), "") } }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    bmp?.let { Image(it, contentDescription = "Screenshot", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    if (uri in saved) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Text("Saved ✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
        Box(Modifier.height(1.dp))
    }
}

private fun latestScreenshots(context: Context, limit: Int): List<Uri> {
    val out = mutableListOf<Uri>()
    val cols = arrayOf(MediaStore.Images.Media._ID)
    val where = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, where, arrayOf("%Screenshots%"),
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { c ->
        while (c.moveToNext() && out.size < limit) {
            out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
        }
    }
    return out
}

private fun capture(context: Context, mode: String?): Intent =
    Intent(context, ShareActivity::class.java).setAction(ShareActivity.ACTION_CAPTURE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { mode?.let { putExtra(ShareActivity.EXTRA_MODE, it) } }
