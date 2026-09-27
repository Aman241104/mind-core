package app.mindcore.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.mindcore.data.VoiceRecorder
import kotlinx.coroutines.delay
import java.io.File
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes

/** Material "mic" (Apache-2.0 Material Symbols path); the full icon pack is too big to add for one icon. */
private val MicIcon: ImageVector = ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        addPathNodes(
            "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
                "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
        ),
        fill = SolidColor(androidx.compose.ui.graphics.Color.Black),
    )
}.build()

/**
 * Tap to record, tap to stop. Shows a live level meter and a timer; afterwards "Voice note 0:12" with ✕.
 * [onRecorded] gets the file (or null when discarded).
 */
@Composable
fun VoiceButton(label: String, onRecorded: (File?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var kept by remember { mutableIntStateOf(0) } // length of the kept recording, 0 = none
    var level by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { if (recorder.isRecording) recorder.discard() } }

    fun start() {
        runCatching { recorder.start() }.onSuccess { recording = true; seconds = 0; error = null }
            .onFailure { error = "Couldn't start the microphone" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else error = "mind-core needs the microphone for voice notes"
    }
    LaunchedEffect(recording) {
        while (recording) {
            delay(100)
            level = recorder.level()
            seconds = seconds.coerceAtLeast(0)
        }
    }
    LaunchedEffect(recording) { while (recording) { delay(1000); seconds++ } }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val scale by animateFloatAsState(if (recording) 1f + level * 0.35f else 1f, label = "level")
        Box(
            Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                .background(if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                .clickable {
                    when {
                        recording -> {
                            val len = recorder.stop()
                            recording = false
                            kept = len
                            onRecorded(if (len > 0) recorder.file else null)
                        }
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED -> start()
                        else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (recording) Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onError))
            else Icon(MicIcon, contentDescription = "Record voice note", tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(12.dp))
        val time = "%d:%02d".format((if (recording) seconds else kept) / 60, (if (recording) seconds else kept) % 60)
        Text(
            when {
                error != null -> error!!
                recording -> "Recording $time · tap to stop"
                kept > 0 -> "Voice note $time"
                else -> label
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (kept > 0 && !recording) Icon(
            Icons.Rounded.Close, contentDescription = "Delete voice note",
            modifier = Modifier.clip(CircleShape).clickable { recorder.discard(); kept = 0; onRecorded(null) }.padding(8.dp),
        )
    }
    if (recording) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f + level * 0.75f)))
}
