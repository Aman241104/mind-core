package app.mindcore.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mindcore.data.Capture
import app.mindcore.ui.blob.BlobState
import app.mindcore.ui.blob.LotusBlob
import app.mindcore.data.Capturer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Done(val message: String) : SaveState
    data class Failed(val message: String) : SaveState
}

/**
 * The capture sheet. From the share menu it arrives filled in (`editable = false`); from the ＋ button
 * it starts empty with a text box, a Paste chip and a screenshot picker.
 */
@Composable
fun CaptureContent(
    initial: Capture,
    capturer: Capturer?,
    editable: Boolean,
    autoVoice: Boolean = false,
    autoPaste: Boolean = false,
    onDone: () -> Unit,
) {
    var capture by remember { mutableStateOf(initial) }
    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SaveState>(SaveState.Idle) }
    var voice by remember { mutableStateOf<java.io.File?>(null) }
    var saveAs by remember { mutableStateOf("save") } // save | note | idea (text only)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) capture = capture.copy(images = (capture.images + uris).distinct())
    }
    // The sheet has focus now, so reading the clipboard is allowed (Android blocks it in the background).
    LaunchedEffect(autoPaste) { if (autoPaste) clipboardText(context)?.let { input = it } }
    // What will be saved = what arrived + whatever is typed in the box.
    val typed = Capture.fromText(input)
    val merged = capture.copy(
        links = (capture.links + typed.links).distinct(),
        text = listOfNotNull(capture.text, typed.text).joinToString("\n").ifBlank { null },
    )

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Save to mind-core", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (capturer == null) {
            Text("This phone isn't paired yet. Run `mindcore pair` on the laptop.", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        merged.chatExport?.let { Row1("WhatsApp chat export", "Every link inside gets imported. Duplicates are skipped.") }
        merged.links.forEach { Row1(hostLabel(it), it.substringAfter("://").substringAfter("/").take(60)) }
        if (merged.links.isEmpty() && !merged.text.isNullOrBlank() && !editable) Row1("Note", merged.text!!.take(140))
        if (merged.images.isNotEmpty()) Thumbs(merged.images)
        merged.pdfs.forEach { Row1("PDF book", "The laptop reads its title, contents and first pages.") }

        if (editable) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text("Paste a link or write a note") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { clipboardText(context)?.let { input = listOf(input, it).filter(String::isNotBlank).joinToString("\n") } },
                    label = { Text("Paste") })
                AssistChip(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    label = { Text("Add screenshots") })
            }
        }
        // Say why you saved it, a deadline, anything: it's transcribed and read with the save.
        if (merged.chatExport == null) VoiceButton("Add a voice note", onRecorded = { voice = it }, autoStart = autoVoice)
        if (merged.chatExport == null && !(editable && merged.links.isEmpty() && merged.images.isEmpty())) {
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Why you saved it (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }

        // Just words (no links, pictures or recording)? Keep them as your own note or idea instead.
        val textOnly = merged.links.isEmpty() && merged.images.isEmpty() && merged.pdfs.isEmpty() && merged.chatExport == null &&
            voice == null && !merged.text.isNullOrBlank()
        if (textOnly) {
            PillTabs(listOf("save" to "Find things in it", "note" to "Note", "idea" to "Idea"), saveAs, { saveAs = it },
                contentPadding = PaddingValues(0.dp))
        }
        AnimatedContent(state, label = "save") { s ->
            when (s) {
                SaveState.Idle, is SaveState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s is SaveState.Failed) Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            state = SaveState.Saving
                            scope.launch {
                                state = try {
                                    val r = if (textOnly && saveAs != "save") capturer.saveAsNote(merged.text!!, saveAs)
                                        else capturer.save(merged, note, voice)
                                    SaveState.Done(r.message)
                                } catch (e: Exception) {
                                    SaveState.Failed(e.message ?: "Couldn't save")
                                }
                            }
                        },
                        enabled = !merged.isEmpty || voice != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (s is SaveState.Failed) "Try again" else "Save") }
                }
                SaveState.Saving -> Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    LotusBlob(BlobState.Thinking, size = 60.dp, onTap = null)
                }
                is SaveState.Done -> {
                    // Swan pops with sparks, then the sheet closes.
                    LaunchedEffect(Unit) { delay(1300); onDone() }
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        LotusBlob(BlobState.Burst, size = 60.dp, onTap = null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.message, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Row1(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center) {
            Text(title.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Thumbs(uris: List<Uri>) {
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uris) { uri ->
            var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(uri) {
                bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ ->
                            d.setTargetSampleSize(4)
                        }.asImageBitmap()
                    }.getOrNull()
                }
            }
            Box(Modifier.size(width = 72.dp, height = 120.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop) }
            }
        }
    }
}

private fun hostLabel(url: String): String {
    val host = url.substringAfter("://").substringBefore("/").removePrefix("www.").removePrefix("m.")
    return when {
        "instagram" in host -> if ("/reel" in url) "Instagram reel" else "Instagram post"
        "youtu" in host -> "YouTube"
        "github" in host -> "GitHub"
        "chatgpt" in host -> "ChatGPT chat"
        "claude.ai" in host -> "Claude chat"
        "gemini" in host -> "Gemini chat"
        "x.com" in host || "twitter" in host -> "X post"
        "linkedin" in host -> "LinkedIn"
        else -> host
    }
}

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}
