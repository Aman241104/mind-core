package app.mindcore.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Play back a voice note: a local file (right after recording, before saving) or a saved recording's URL.
 * Play/pause, a scrubber you can drag, and the time. Stops and frees the player when it leaves the screen.
 */
@Composable
fun VoicePlayer(source: String, modifier: Modifier = Modifier) {
    val player = remember(source) { MediaPlayer() }
    var prepared by remember(source) { mutableStateOf(false) }
    var loading by remember(source) { mutableStateOf(false) }
    var playing by remember(source) { mutableStateOf(false) }
    var failed by remember(source) { mutableStateOf(false) }
    var duration by remember(source) { mutableIntStateOf(0) }
    var position by remember(source) { mutableFloatStateOf(0f) } // ms
    var dragging by remember(source) { mutableStateOf(false) }

    DisposableEffect(source) {
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        player.setOnPreparedListener { duration = it.duration; prepared = true; loading = false; it.start(); playing = true }
        player.setOnCompletionListener { playing = false; position = 0f; it.seekTo(0) }
        player.setOnErrorListener { _, _, _ -> failed = true; loading = false; playing = false; true }
        onDispose { runCatching { player.stop() }; player.release() }
    }
    LaunchedEffect(playing, dragging) {
        while (playing && !dragging) {
            position = player.currentPosition.toFloat()
            delay(100)
        }
    }

    fun toggle() {
        when {
            failed -> Unit
            !prepared -> runCatching {
                loading = true
                player.setDataSource(source) // file path or https URL
                player.prepareAsync() // starts playing once ready
            }.onFailure { failed = true; loading = false }
            playing -> { player.pause(); playing = false }
            else -> { player.start(); playing = true }
        }
    }

    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).pressScale(::toggle).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play", tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(8.dp))
        if (failed) {
            Text("Couldn't play this recording", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f))
        } else {
            Slider(
                value = if (duration > 0) position / duration else 0f,
                onValueChange = { v -> dragging = true; position = v * duration },
                onValueChangeFinished = { if (prepared) player.seekTo(position.toInt()); dragging = false },
                enabled = prepared,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                time(if (playing || position > 0) position.toInt() else duration),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun time(ms: Int): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
