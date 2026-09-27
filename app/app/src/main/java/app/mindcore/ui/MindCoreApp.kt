package app.mindcore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.shape.CircleShape
import app.mindcore.settings.AppSettings
import app.mindcore.settings.SettingsStore
import app.mindcore.ui.theme.colorSchemeFor
import app.mindcore.ui.theme.isDark
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindcore.ui.glass.LiquidBottomTab
import app.mindcore.ui.glass.LiquidBottomTabs
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

private val tabs = listOf(
    "For You" to Icons.Rounded.Home,
    "Library" to Icons.AutoMirrored.Rounded.List,
    "Ask" to Icons.Rounded.Search,
)

@Composable
fun MindCoreApp() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val settings by store.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val update: ((AppSettings) -> AppSettings) -> Unit = { change -> scope.launch { store.update(change) } }
    val scheme = colorSchemeFor(settings)
    val dark = isDark(settings)

    MaterialTheme(colorScheme = scheme) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        val backdrop = rememberLayerBackdrop()
        val haptics = LocalHapticFeedback.current
        BackHandler(enabled = showSettings) { showSettings = false }

        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            Box(Modifier.fillMaxSize().background(scheme.surface)) {
                // Everything in this layer is what the glass bends and blurs.
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    Glow(scheme)
                    when {
                        showSettings -> SettingsScreen(settings, update) { showSettings = false }
                        tab == 0 -> ForYou(onSettings = { showSettings = true })
                        else -> Placeholder(tabs[tab].first)
                    }
                }
                if (!showSettings) {
                    // Pass a lambda that reads the state (not the Int), so the glass puck sees every change.
                    BottomBar(
                        selected = { tab },
                        onSelect = {
                            if (settings.haptics && it != tab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            tab = it
                        },
                        backdrop = backdrop,
                        glass = settings.glass,
                        dark = dark,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/** Soft wallpaper-colored light behind the content, so the glass has color to refract. */
@Composable
private fun Glow(scheme: ColorScheme) {
    Canvas(Modifier.fillMaxSize()) {
        fun glow(color: Color, center: Offset, radius: Float) =
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
        glow(scheme.primary.copy(alpha = 0.40f), Offset(size.width * 0.1f, size.height * 0.08f), size.width * 0.9f)
        glow(scheme.tertiary.copy(alpha = 0.32f), Offset(size.width * 1.0f, size.height * 0.5f), size.width * 0.8f)
        glow(scheme.secondary.copy(alpha = 0.30f), Offset(size.width * 0.0f, size.height * 0.95f), size.width * 0.8f)
    }
}

@Composable
private fun ForYou(onSettings: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Row(Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("For You", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${sampleItems.size} finds from your saved reels",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Rounded.Settings, contentDescription = "Settings",
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onSettings)
                        .padding(12.dp)
                        .size(24.dp),
                )
            }
        }
        items(sampleItems) { ItemCard(it) }
    }
}

@Composable
private fun ItemCard(item: Item) {
    val scheme = MaterialTheme.colorScheme
    val kindColor = when (item.kind) {
        Kind.Repo -> scheme.primary
        Kind.Tool -> scheme.tertiary
        Kind.Course -> scheme.secondary
        Kind.Job -> scheme.error
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(item.kind.label, kindColor)
                Spacer(Modifier.width(8.dp))
                val trustColor = when (item.trust) {
                    Trust.Verified -> Color(0xFF7BD88F)
                    Trust.Check -> Color(0xFFFFC66D)
                    Trust.Unconfirmed -> scheme.outline
                }
                Pill(item.trust.label, trustColor)
            }
            Spacer(Modifier.height(2.dp))
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(item.oneLine, style = MaterialTheme.typography.bodyMedium)
            Text(item.detail, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(item.source, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Placeholder(title: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Coming in M2 — this build only tests the glass.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BottomBar(
    selected: () -> Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    glass: Float,
    dark: Boolean,
    modifier: Modifier,
) {
    val contentColor = if (dark) Color.White else Color.Black
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidBottomTabs(
            selectedTabIndex = selected,
            onTabSelected = onSelect,
            backdrop = backdrop,
            tabsCount = tabs.size,
            accentColor = MaterialTheme.colorScheme.primary,
            isLight = !dark,
            glass = glass,
            modifier = Modifier.weight(1f),
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                LiquidBottomTab({ onSelect(index) }) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        CaptureButton(backdrop, glass, dark, contentColor)
    }
}

/** Round glass "+" beside the tabs, like Convx's search button. Opens the capture sheet (M2). */
@Composable
private fun CaptureButton(backdrop: Backdrop, glass: Float, dark: Boolean, contentColor: Color) {
    Box(
        Modifier
            .size(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx() * (0.5f + glass / 2f))
                    if (glass > 0f) lens(16f.dp.toPx() * glass, 32f.dp.toPx() * glass)
                },
                onDrawSurface = { drawRect((if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)).copy(alpha = 0.4f)) },
            )
            .clickable { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Capture", tint = contentColor, modifier = Modifier.size(30.dp))
    }
}
