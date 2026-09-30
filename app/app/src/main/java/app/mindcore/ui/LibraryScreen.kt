package app.mindcore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindcore.data.ApiItem
import app.mindcore.data.Library
import app.mindcore.ui.blob.BlobState
import app.mindcore.ui.blob.LotusBlob
import kotlinx.coroutines.launch

enum class Sort(val label: String) {
    Newest("Newest saved"), Oldest("Oldest saved"), Updated("Recently updated"), AZ("Name A–Z"), ZA("Name Z–A"),
    Stars("Most GitHub stars"), Saved("Saved most often"), Deadline("Deadline soonest"),
}

private val statusLabels = linkedMapOf("new" to "New", "want" to "Want", "trying" to "Trying", "done" to "Done", "skip" to "Skipped")
private val trustLabels = linkedMapOf("verified" to "Verified", "check" to "Check claims", "unconfirmed" to "Unconfirmed", "dead" to "Archived")

/** What the Library shows. Lives above the tabs so it survives switching tabs. */
@Stable
class LibraryQuery {
    var text by mutableStateOf("")
    var kinds by mutableStateOf(setOf<String>())
    var statuses by mutableStateOf(setOf<String>())
    var trusts by mutableStateOf(setOf<String>())
    var favoritesOnly by mutableStateOf(false)
    var withDeadline by mutableStateOf(false)
    var sort by mutableStateOf(Sort.Newest)

    val activeCount get() = kinds.size + statuses.size + trusts.size + (if (favoritesOnly) 1 else 0) + (if (withDeadline) 1 else 0)

    fun clear() {
        kinds = emptySet(); statuses = emptySet(); trusts = emptySet(); favoritesOnly = false; withDeadline = false
    }

    fun apply(items: List<ApiItem>): List<ApiItem> {
        val q = text.trim().lowercase()
        val filtered = items.filter { it ->
            (q.isEmpty() || it.name.lowercase().contains(q) || it.oneLine.orEmpty().lowercase().contains(q) ||
                it.userNote.orEmpty().lowercase().contains(q) || it.verification?.repo.orEmpty().lowercase().contains(q)) &&
                (kinds.isEmpty() || it.kind in kinds) &&
                (statuses.isEmpty() || it.status in statuses) &&
                (trusts.isEmpty() || it.trust in trusts) &&
                (!favoritesOnly || it.favorite) &&
                (!withDeadline || it.deadline != null)
        }
        return when (sort) {
            Sort.Newest -> filtered.sortedByDescending { it.createdAt }
            Sort.Oldest -> filtered.sortedBy { it.createdAt }
            Sort.Updated -> filtered.sortedByDescending { it.updatedAt }
            Sort.AZ -> filtered.sortedBy { it.name.lowercase() }
            Sort.ZA -> filtered.sortedByDescending { it.name.lowercase() }
            Sort.Stars -> filtered.sortedByDescending { it.verification?.stars ?: -1 }
            Sort.Saved -> filtered.sortedByDescending { it.sourceCount }
            Sort.Deadline -> filtered.sortedWith(compareBy(nullsLast()) { it.deadline })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(library: Library, query: LibraryQuery, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val shown = remember(library.items, query.text, query.kinds, query.statuses, query.trusts, query.favoritesOnly, query.withDeadline, query.sort) {
        query.apply(library.items)
    }
    val favCount = library.items.count { it.favorite }

    PullToRefreshBox(isRefreshing = library.loading, onRefresh = { scope.launch { library.refresh() } }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item { Spacer(Modifier.height(64.dp)) }
            item {
                Column(Modifier.padding(start = 4.dp, top = 24.dp)) {
                    Text("Library", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (shown.size == library.items.size) "${library.items.size} items · ${query.sort.label.lowercase()}"
                        else "${shown.size} of ${library.items.size} · ${query.sort.label.lowercase()}",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Search + filter + sort
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = query.text, onValueChange = { query.text = it },
                        placeholder = { Text("Search names, notes, repos") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = if (query.text.isNotEmpty()) ({
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.clickable { query.text = "" })
                        }) else null,
                        singleLine = true,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(28.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    RoundIcon(Icons.Rounded.FilterList, "Filters", badge = query.activeCount) { showFilters = true }
                    Spacer(Modifier.width(6.dp))
                    Column {
                        RoundIcon(Icons.AutoMirrored.Rounded.Sort, "Sort") { showSort = true }
                        DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                            Sort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label, fontWeight = if (s == query.sort) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { query.sort = s; showSort = false },
                                )
                            }
                        }
                    }
                }
            }
            // Quick chips: favorites + types, then the active filters with × to remove
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = query.favoritesOnly, onClick = { query.favoritesOnly = !query.favoritesOnly },
                        label = { Text("Favorites $favCount") },
                        leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    val counts = library.items.groupingBy { it.kind }.eachCount()
                    kindLabels.filterKeys { counts.containsKey(it) }.forEach { (k, label) ->
                        FilterChip(selected = k in query.kinds, onClick = { query.kinds = query.kinds.toggle(k) }, label = { Text("$label ${counts[k]}") })
                    }
                }
            }
            val extra = query.statuses.map { "status" to it } + query.trusts.map { "trust" to it } +
                listOfNotNull(if (query.withDeadline) "deadline" to "deadline" else null)
            if (extra.isNotEmpty()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    extra.forEach { (type, v) ->
                        InputChip(
                            selected = true,
                            onClick = {
                                when (type) {
                                    "status" -> query.statuses -= v
                                    "trust" -> query.trusts -= v
                                    else -> query.withDeadline = false
                                }
                            },
                            label = { Text(statusLabels[v] ?: trustLabels[v] ?: "Has a deadline") },
                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove filter", modifier = Modifier.size(16.dp)) },
                        )
                    }
                    TextButton(onClick = { query.clear() }) { Text("Clear all") }
                }
            }
            if (!library.paired) item { MessageCard("Not connected yet", "Pair this phone from the laptop with `mindcore pair`.") }
            if (shown.isEmpty() && library.items.isNotEmpty()) item {
                Column(Modifier.padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LotusBlob(BlobState.Idle, size = 110.dp)
                    }
                    Text("Nothing matches", style = MaterialTheme.typography.titleMedium)
                    Text("Try fewer filters or a different search.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(shown, key = { it.id }) { item ->
                ItemCard(item, onFavorite = { scope.launch { library.toggleFavorite(it) } }) { onOpen(item.id) }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { query.clear() }) { Text("Reset") }
                }
                ChipGroup("Type", kindLabels, query.kinds) { query.kinds = query.kinds.toggle(it) }
                ChipGroup("Status", statusLabels, query.statuses) { query.statuses = query.statuses.toggle(it) }
                ChipGroup("Trust", trustLabels, query.trusts) { query.trusts = query.trusts.toggle(it) }
                Text("More", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(query.favoritesOnly, { query.favoritesOnly = !query.favoritesOnly }, { Text("Favorites only") })
                    FilterChip(query.withDeadline, { query.withDeadline = !query.withDeadline }, { Text("Has a deadline") })
                }
                Text("${query.apply(library.items).size} items match", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, options: Map<String, String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (k, label) -> FilterChip(k in selected, { onToggle(k) }, { Text(label) }) }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, badge: Int = 0, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box {
        Icon(icon, contentDescription = label, modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (badge > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(14.dp).size(24.dp))
        if (badge > 0) Text(
            badge.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

private fun Set<String>.toggle(v: String) = if (v in this) this - v else this + v
