package app.mindcore.ui

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
    // Material You: colors come from the wallpaper on Android 12+.
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else darkColorScheme()

    MaterialTheme(colorScheme = scheme) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val backdrop = rememberLayerBackdrop()

        Box(Modifier.fillMaxSize().background(scheme.surface)) {
            // Everything in this layer is what the glass bends and blurs.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                Glow(scheme)
                when (tab) {
                    0 -> ForYou()
                    else -> Placeholder(tabs[tab].first)
                }
            }
            BottomBar(tab, { tab = it }, backdrop, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Soft wallpaper-colored light behind the content, so the glass has color to refract. */
@Composable
private fun Glow(scheme: ColorScheme) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.size(320.dp).offset((-80).dp, (-60).dp).blur(120.dp).background(scheme.primary.copy(0.35f), CircleShape))
        Box(Modifier.size(280.dp).align(Alignment.CenterEnd).offset(90.dp, 40.dp).blur(120.dp)
            .background(scheme.tertiary.copy(0.30f), CircleShape))
        Box(Modifier.size(260.dp).align(Alignment.BottomStart).offset((-40).dp, 20.dp).blur(110.dp)
            .background(scheme.secondary.copy(0.30f), CircleShape))
    }
}

@Composable
private fun ForYou() {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Column(Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)) {
                Text("For You", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "${sampleItems.size} finds from your saved reels",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit, backdrop: Backdrop, modifier: Modifier) {
    val contentColor = Color.White
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidBottomTabs(
            selectedTabIndex = { selected },
            onTabSelected = onSelect,
            backdrop = backdrop,
            tabsCount = tabs.size,
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
        CaptureButton(backdrop)
    }
}

/** Round glass "+" beside the tabs, like Convx's search button. Opens the capture sheet (M2). */
@Composable
private fun CaptureButton(backdrop: Backdrop) {
    Box(
        Modifier
            .size(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                onDrawSurface = { drawRect(Color(0xFF121212).copy(alpha = 0.4f)) },
            )
            .clickable { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(30.dp))
    }
}
