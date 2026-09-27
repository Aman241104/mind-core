package app.mindcore.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.ApiItem
import app.mindcore.data.ItemDetail
import kotlinx.coroutines.launch

private val statuses = listOf("new" to "New", "want" to "Want", "trying" to "Trying", "done" to "Done", "skip" to "Skip")

@Composable
fun ItemScreen(id: String, api: Api, onBack: () -> Unit, onOpenItem: (String) -> Unit, onChanged: (ApiItem) -> Unit) {
    var detail by remember(id) { mutableStateOf<ItemDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(id) {
        try { detail = api.item(id) } catch (e: Exception) { error = e.message }
    }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back",
                modifier = Modifier.padding(top = 8.dp).clip(CircleShape).clickable(onClick = onBack).padding(10.dp).size(26.dp),
            )
        }
        val d = detail
        if (d == null) {
            item {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }
        val it = d.item
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(kindLabels[it.kind] ?: it.kind, kindColor(it.kind))
                    Spacer(Modifier.width(8.dp))
                    Pill(trustLabel(it.trust), trustColor(it.trust))
                }
                Text(it.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                it.oneLine?.let { line -> Text(line, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        it.verification?.let { v ->
            item {
                Block("Checked on GitHub") {
                    Fact("Repo", v.repo ?: "not found")
                    v.stars?.let { s -> Fact("Stars", compact(s)) }
                    Fact("License", v.license?.takeIf { l -> l != "NOASSERTION" } ?: "none / custom")
                    v.pushedAt?.let { p -> Fact("Last update", p.take(10)) }
                    if (v.archived) Fact("Status", "Archived, no longer maintained")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                it.url?.let { url ->
                    Button(onClick = { open(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if ("github.com" in url) "Open on GitHub" else "Open link")
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    statuses.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = it.status == value,
                            onClick = {
                                scope.launch {
                                    try {
                                        val updated = api.setStatus(it.id, value)
                                        detail = updated
                                        onChanged(updated.item)
                                    } catch (e: Exception) { error = e.message }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, statuses.size),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }
        }
        item { SectionTitle("Where you saved it") }
        items(d.sources) { s ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { open(s.url) },
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.creator ?: s.url.substringAfter("//").substringBefore("/"),
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (s.promo) Pill("Promo", MaterialTheme.colorScheme.outline)
                    }
                    Text("Saved ${s.savedAt.take(10)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    if (s.claims.isNotEmpty()) {
                        Text("What the post claimed (not fact-checked yet):", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        s.claims.forEach { c -> Text("• $c", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        if (d.related.isNotEmpty()) {
            item { SectionTitle("Related") }
            items(d.related) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onOpenItem(r.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(kindLabels[r.kind] ?: r.kind, kindColor(r.kind))
                    Spacer(Modifier.width(10.dp))
                    Text(r.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp).windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
)

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
