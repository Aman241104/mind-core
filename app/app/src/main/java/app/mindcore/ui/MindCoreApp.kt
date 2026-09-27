package app.mindcore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.Library
import app.mindcore.settings.AppSettings
import app.mindcore.settings.SettingsStore
import app.mindcore.ui.theme.colorSchemeFor
import app.mindcore.ui.theme.isDark
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

@Composable
fun MindCoreApp() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val settings by store.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val update: ((AppSettings) -> AppSettings) -> Unit = { change -> scope.launch { store.update(change) } }
    val scheme = colorSchemeFor(settings)
    val dark = isDark(settings)

    val library = remember(settings.serverUrl, settings.apiToken) {
        Library(if (settings.apiToken.isBlank()) null else Api(settings.serverUrl, settings.apiToken))
    }
    LaunchedEffect(library) { library.refresh() }

    MaterialTheme(colorScheme = scheme) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showGlass by rememberSaveable { mutableStateOf(false) }
        var openItem by rememberSaveable { mutableStateOf<String?>(null) }
        val backdrop = rememberLayerBackdrop()
        val haptics = LocalHapticFeedback.current
        BackHandler(enabled = showSettings || showGlass || openItem != null) {
            when {
                openItem != null -> openItem = null
                showGlass -> showGlass = false
                else -> showSettings = false
            }
        }

        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            Box(Modifier.fillMaxSize().background(scheme.surface)) {
                // Everything in this layer is what the glass bends and blurs.
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    Glow(scheme)
                    val api = library.api
                    val itemId = openItem
                    when {
                        showGlass -> GlassSettingsScreen(
                            style = settings.glass, dark = dark,
                            onChange = { g -> update { it.copy(glass = g) } },
                            onBack = { showGlass = false },
                        )
                        showSettings -> SettingsScreen(settings, update, onOpenGlass = { showGlass = true }) { showSettings = false }
                        itemId != null && api != null -> ItemScreen(
                            id = itemId, api = api,
                            onBack = { openItem = null },
                            onOpenItem = { id -> openItem = id },
                            onChanged = library::replace,
                        )
                        tab == 0 -> ForYou(library, onSettings = { showSettings = true }, onOpen = { id -> openItem = id })
                        tab == 1 -> LibraryScreen(library, onOpen = { id -> openItem = id })
                        else -> Placeholder("Ask", "Chat with everything you saved comes in M3.")
                    }
                }
                if (!showSettings && !showGlass && openItem == null) {
                    // Pass a lambda that reads the state (not the Int), so the glass puck sees every change.
                    BottomBar(
                        selected = { tab },
                        onSelect = { index ->
                            if (settings.haptics && index != tab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            tab = index
                        },
                        backdrop = backdrop,
                        style = settings.glass,
                        dark = dark,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(library: Library, content: LazyListScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    PullToRefreshBox(isRefreshing = library.loading, onRefresh = { scope.launch { library.refresh() } }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            content()
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

/** Not paired, or the server failed: say so plainly instead of showing an empty list. */
private fun LazyListScope.problems(library: Library) {
    if (!library.paired) item {
        MessageCard("Not connected yet", "Pair this phone from the laptop with `mindcore pair`. It sends the server address and key over adb.")
    }
    library.error?.let { message -> item { MessageCard("Couldn't reach the server", message) } }
}

@Composable
private fun ForYou(library: Library, onSettings: () -> Unit, onOpen: (String) -> Unit) {
    val status = library.status
    Refreshable(library) {
        item {
            Header("For You", "${library.items.size} finds from your saves") {
                Icon(
                    Icons.Rounded.Settings, contentDescription = "Settings",
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onSettings).padding(12.dp).size(24.dp),
                )
            }
        }
        problems(library)
        if (status != null) item {
            val waiting = (status.jobs["pending"] ?: 0) + (status.jobs["leased"] ?: 0)
            val failed = status.jobs["failed"] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Box(
                    Modifier.size(10.dp).background(
                        if (status.laptopOnline) Color(0xFF7BD88F) else MaterialTheme.colorScheme.outline, CircleShape,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(
                        if (status.laptopOnline) "Laptop online" else "Laptop offline",
                        if (waiting > 0) "$waiting saves being processed" else "all caught up",
                        if (failed > 0) "$failed couldn't be opened" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(library.items.take(20), key = { it.id }) { item -> ItemCard(item) { onOpen(item.id) } }
    }
}

@Composable
private fun LibraryScreen(library: Library, onOpen: (String) -> Unit) {
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    val counts = library.items.groupingBy { it.kind }.eachCount()
    val shown = library.items.filter { kind == null || it.kind == kind }.sortedBy { it.name.lowercase() }
    Refreshable(library) {
        item { Header("Library", "${library.items.size} items, A–Z") }
        problems(library)
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("All") })
                kindLabels.filterKeys { counts.containsKey(it) }.forEach { (k, label) ->
                    FilterChip(
                        selected = kind == k, onClick = { kind = if (kind == k) null else k },
                        label = { Text("$label ${counts[k]}") },
                    )
                }
            }
        }
        items(shown, key = { it.id }) { item -> ItemCard(item) { onOpen(item.id) } }
    }
}

@Composable
private fun Placeholder(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
