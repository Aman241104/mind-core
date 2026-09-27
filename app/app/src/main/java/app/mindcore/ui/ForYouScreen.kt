package app.mindcore.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mindcore.data.ApiItem
import app.mindcore.data.Library
import app.mindcore.ui.blob.BlobState
import app.mindcore.ui.blob.LotusBlob
import app.mindcore.update.UpdateBanner
import app.mindcore.update.UpdateState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForYouScreen(
    library: Library,
    updates: UpdateState,
    onSettings: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenKind: (String?) -> Unit,
    onCalendar: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val items = library.items
    val newestFirst = remember(items) { items.sortedByDescending { it.updatedAt } }
    val top = remember(items) {
        newestFirst.take(30).filter { it.trust == "verified" }.maxByOrNull { it.verification?.stars ?: 0 }
    }
    val fresh = remember(items, top) { newestFirst.filter { it.id != top?.id }.take(10) }
    val toCheck = remember(items) { newestFirst.filter { it.trust == "check" || it.trust == "unconfirmed" }.take(4) }
    val continuing = remember(items) { items.filter { it.status == "want" || it.status == "trying" }.take(8) }
    val kinds = remember(items) { items.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value } }

    PullToRefreshBox(isRefreshing = library.loading, onRefresh = { scope.launch { library.refresh() } }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item { Header(onSettings, onCalendar) }
            item { UpdateBanner(updates, Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) }
            item { Processing(library) }
            if (!library.paired) item {
                Box(Modifier.padding(16.dp)) {
                    MessageCard("Not connected yet", "Pair this phone from the laptop with `mindcore pair`.")
                }
            }
            library.error?.let { message -> item { Box(Modifier.padding(16.dp)) { MessageCard("Couldn't reach the server", message) } } }

            if (library.upcoming.isNotEmpty()) {
                item { SectionHeader("Coming up", "Calendar", onCalendar) }
                items(library.upcoming.take(5), key = { "due-" + it.id }) { u ->
                    val label = dueLabel(LocalDate.parse(u.deadline), LocalDate.now())
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(u.id) }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(LocalDate.parse(u.deadline).dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(LocalDate.parse(u.deadline).format(DateTimeFormatter.ofPattern("MMM")),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(u.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${kindLabels[u.kind] ?: u.kind} · $label", style = MaterialTheme.typography.bodySmall,
                                color = if (label.endsWith("ago")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            top?.let { item { TopFind(it) { onOpen(it.id) } } }

            if (fresh.isNotEmpty()) {
                item { SectionHeader("Fresh finds", "See all") { onOpenKind(null) } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(fresh, key = { it.id }) { FreshTile(it) { onOpen(it.id) } }
                    }
                }
            }

            if (continuing.isNotEmpty()) {
                item { SectionHeader("Continue") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(continuing, key = { it.id }) { FreshTile(it) { onOpen(it.id) } }
                    }
                }
            }

            if (kinds.isNotEmpty()) {
                item { SectionHeader("Browse") }
                // Two columns of big numbers; tapping opens Library already filtered.
                kinds.chunked(2).forEach { pair ->
                    item {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (kind, count) ->
                                KindTile(kind, count, Modifier.weight(1f)) { onOpenKind(kind) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (toCheck.isNotEmpty()) {
                item { SectionHeader("Check these claims") }
                items(toCheck, key = { "check-" + it.id }) { CheckRow(it) { onOpen(it.id) } }
            }
        }
    }
}

@Composable
private fun Header(onSettings: () -> Unit, onCalendar: () -> Unit) {
    Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            Text("For You", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
        Icon(
            Icons.Rounded.DateRange, contentDescription = "Calendar",
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onCalendar).padding(12.dp).size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.Settings, contentDescription = "Settings",
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onSettings).padding(12.dp).size(24.dp),
        )
    }
}

/** Laptop dot + backlog progress. Collapses to one quiet line when nothing is queued. */
@Composable
private fun Processing(library: Library) {
    val status = library.status ?: return
    val waiting = (status.jobs["pending"] ?: 0) + (status.jobs["leased"] ?: 0)
    val done = status.jobs["done"] ?: 0
    val failed = status.jobs["failed"] ?: 0
    val total = waiting + done + failed
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Swan shows what the laptop is doing: working through saves, asleep when it's offline.
            LotusBlob(
                when { !status.laptopOnline -> BlobState.Sleep; waiting > 0 -> BlobState.Orbit; else -> BlobState.Idle },
                size = 36.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    waiting > 0 -> "Reading your saves: ${done + failed} of $total"
                    else -> "All caught up"
                } + if (status.laptopOnline) "" else " · laptop offline",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (waiting > 0 && total > 0) {
            val progress by animateFloatAsState((done + failed).toFloat() / total, label = "progress")
            LinearProgressIndicator(
                progress = { progress }, strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
        }
    }
}

/** The one big card: strongest verified find among the newest saves. */
@Composable
private fun TopFind(item: ApiItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().pressable(onClick)
            .clip(RoundedCornerShape(28.dp)).background(scheme.primaryContainer).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Top find", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer.copy(alpha = 0.75f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                item.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            item.verification?.stars?.let { stars ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    Text(compact(stars), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer)
                }
            }
        }
        item.oneLine?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = scheme.onPrimaryContainer, maxLines = 3) }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(item.verification?.repo, item.verification?.license?.takeIf { it != "NOASSERTION" }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            FilledTonalButton(onClick = onClick) { Text("Open") }
        }
    }
}

/** Compact square tile for horizontal rows. */
@Composable
private fun FreshTile(item: ApiItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = kindColor(item.kind)
    Column(
        Modifier.width(156.dp).aspectRatio(0.9f).pressable(onClick).clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHigh).padding(16.dp),
    ) {
        Text((kindLabels[item.kind] ?: item.kind).uppercase(), style = MaterialTheme.typography.labelSmall, color = accent,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(trustColor(item.trust), CircleShape))
            Spacer(Modifier.width(6.dp))
            val stars = item.verification?.stars
            if (stars != null) Icon(Icons.Rounded.Star, contentDescription = "stars", tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp).padding(end = 2.dp))
            Text(
                stars?.let { compact(it) } ?: trustLabel(item.trust),
                style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KindTile(kind: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val accent = kindColor(kind)
    Column(
        modifier.pressable(onClick).clip(RoundedCornerShape(24.dp))
            .background(accent.copy(alpha = 0.13f)).padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = accent)
        Text(pluralKind(kind, count), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A plain row, not a card: these are a to-do list. */
@Composable
private fun CheckRow(item: ApiItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(trustColor(item.trust), CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${kindLabels[item.kind] ?: item.kind} · ${trustLabel(item.trust).lowercase()} · ${ago(item.updatedAt)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) Text(
            action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Press feedback: a quick spring down to 97%, no ripple. */
@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 600f), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }.clickable(source, indication = null, onClick = onClick)
}

private fun pluralKind(kind: String, n: Int): String {
    val label = kindLabels[kind] ?: kind
    return if (n == 1) label else if (label.endsWith("y")) label.dropLast(1) + "ies" else label + "s"
}

/** SQLite "YYYY-MM-DD HH:MM:SS" in UTC → "3 h ago". */
internal fun ago(sqlTime: String): String = runCatching {
    val then = LocalDateTime.parse(sqlTime.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
    val mins = ChronoUnit.MINUTES.between(then, java.time.Instant.now())
    when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} h ago"
        else -> "${mins / (60 * 24)} d ago"
    }
}.getOrDefault("")
