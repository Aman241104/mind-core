package app.mindcore.ui.blob

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/** Every blob state live, plus a big one that cycles through them. Tap a tile to play it on the big blob. */
@Composable
fun BlobGallery(onBack: () -> Unit) {
    var big by remember { mutableStateOf(BlobState.Idle) }
    var cycling by remember { mutableStateOf(true) }
    LaunchedEffect(cycling) {
        var i = 0
        while (cycling) {
            big = BlobState.entries[i % BlobState.entries.size]
            delay(2400)
            i++
        }
    }
    LazyVerticalGrid(
        GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(3) }) { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(span = { GridItemSpan(3) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back",
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(10.dp).size(26.dp))
                Spacer(Modifier.width(4.dp))
                Text("Meet Swan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        item(span = { GridItemSpan(3) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                // Fake voice level so Listening can be seen without a microphone.
                var t by remember { mutableStateOf(0f) }
                LaunchedEffect(Unit) { while (true) { delay(60); t += 0.06f } }
                LotusBlob(big, size = 200.dp, level = { abs(sin(t * 7f)) * 0.8f })
                Text(big.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (cycling) "Cycling through every state · tap one to hold it" else "Tap the big blob to wink · tap a tile to switch",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(BlobState.entries) { s ->
            Column(
                Modifier.clip(RoundedCornerShape(24.dp))
                    .background(if (s == big && !cycling) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { cycling = false; big = s }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.2f), contentAlignment = Alignment.Center) {
                    LotusBlob(s, size = 72.dp, level = { 0.5f }, onTap = null)
                }
                Text(s.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
