package app.mindcore.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Update state shared by the For You banner and the Settings row. */
class UpdateState(val updater: Updater?) {
    var release by mutableStateOf<Release?>(null)
        private set
    var progress by mutableStateOf<Float?>(null) // null = not downloading
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set

    suspend fun check(manual: Boolean = false) {
        val u = updater ?: return
        checking = true
        try {
            release = u.check()
            message = if (manual && release == null) "You're on the latest version." else null
        } catch (e: Exception) {
            if (manual) message = e.message
        } finally {
            checking = false
        }
    }

    suspend fun update() {
        val u = updater ?: return
        val r = release ?: return
        if (!u.canInstall()) {
            message = "Allow mind-core to install updates, then tap Update again."
            u.openInstallPermission()
            return
        }
        try {
            progress = 0f
            val apk = u.download(r) { progress = it }
            message = "Installing…"
            u.install(apk)
        } catch (e: Exception) {
            message = e.message ?: "Update failed"
        } finally {
            progress = null
        }
    }
}

@Composable
fun UpdateBanner(state: UpdateState, modifier: Modifier = Modifier) {
    val r = state.release ?: return
    val scope = rememberCoroutineScope()
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Update available · ${r.versionName}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 4)
            }
            if (state.progress == null) Button(onClick = { scope.launch { state.update() } }) { Text("Update") }
        }
        state.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }
    }
}
