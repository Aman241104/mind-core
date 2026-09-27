package app.mindcore.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.mindcore.R
import app.mindcore.settings.GlassPreset
import app.mindcore.settings.GlassStyle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * In-depth glass customization, like Convx's Liquid Glass page. A live preview stays pinned at the top
 * (moving content under the real tab bar and capture button), so every change is visible immediately.
 */
@Composable
fun GlassSettingsScreen(style: GlassStyle, dark: Boolean, onChange: (GlassStyle) -> Unit, onBack: () -> Unit) {
    // Any manual tweak turns the preset into "Custom".
    val edit: (GlassStyle.() -> GlassStyle) -> Unit = { f -> onChange(style.f().copy(preset = GlassPreset.Custom)) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back",
                modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(10.dp).size(26.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Liquid Glass", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Preview(style, dark)

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionLabel("Style") }
            item {
                Card {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            GlassPreset.entries.forEachIndexed { i, p ->
                                SegmentedButton(
                                    selected = style.preset == p,
                                    onClick = { onChange(style.withPreset(p)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, GlassPreset.entries.size),
                                    label = { Text(p.label, maxLines = 1) },
                                )
                            }
                        }
                        Hint(
                            when (style.preset) {
                                GlassPreset.Liquid -> "Balanced: soft blur, clear bending at the edges."
                                GlassPreset.Frosted -> "Heavy blur, little bending. Calm and very readable."
                                GlassPreset.Clear -> "Almost no blur, strong lens and depth. Most like iOS."
                                GlassPreset.Custom -> "Your own mix. Pick a preset to start over."
                            },
                        )
                    }
                }
            }

            item { SectionLabel("Effects") }
            item {
                Card {
                    ToggleRow("Vibrancy", "Boost the saturation of what's behind the glass", style.vibrancy) { v -> edit { copy(vibrancy = v) } }
                    Line()
                    SliderRow("Blur radius", "How soft the glass makes what's behind it", style.blur, 0f..24f, "dp") { v -> edit { copy(blur = v) } }
                    Line()
                    SliderRow("Lens refraction height", "How far in from the edge the bending reaches", style.lensHeight, 0f..48f, "dp") { v ->
                        edit { copy(lensHeight = v) }
                    }
                    Line()
                    SliderRow("Lens refraction amount", "How strongly the edge bends the content", style.lensAmount, 0f..64f, "dp") { v ->
                        edit { copy(lensAmount = v) }
                    }
                    Line()
                    ToggleRow("Chromatic aberration", "Rainbow fringe on the tab bubble while you drag it", style.chromaticAberration) { v ->
                        edit { copy(chromaticAberration = v) }
                    }
                    Line()
                    ToggleRow("Depth effect", "Makes the lens look thicker, like a glass pebble", style.depthEffect) { v ->
                        edit { copy(depthEffect = v) }
                    }
                }
            }

            item { SectionLabel("Appearance") }
            item {
                Card {
                    ColorRow("Surface tint", "Color laid over the glass", style.tint, if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)) { c ->
                        edit { copy(tint = c) }
                    }
                    Line()
                    SliderRow("Surface opacity", "How strong the tint is. Higher = easier to read", style.tintOpacity, 0f..1f, "%") { v ->
                        edit { copy(tintOpacity = v) }
                    }
                    Line()
                    ColorRow("Selection pill color", "Wash over the selected tab. Default follows the theme", style.pillColor,
                        if (dark) Color.White else Color.Black) { c -> edit { copy(pillColor = c) } }
                    Line()
                    SliderRow("Selection pill opacity", "How strong that wash is at rest. It clears while pressed", style.pillOpacity, 0f..0.4f, "%") { v ->
                        edit { copy(pillOpacity = v) }
                    }
                    Line()
                    ColorRow("Highlight color", "The bright rim along the edge of the glass", style.highlightColor, Color.White) { c ->
                        edit { copy(highlightColor = c) }
                    }
                    Line()
                    SliderRow("Highlight opacity", "How bright that rim is", style.highlightOpacity, 0f..1f, "%") { v ->
                        edit { copy(highlightOpacity = v) }
                    }
                    Line()
                    ToggleRow("Adaptive contrast", "Icons and labels turn black or white based on what's behind the glass, so they stay readable",
                        style.adaptiveContrast) { v -> edit { copy(adaptiveContrast = v) } }
                }
            }

            item { SectionLabel("Per component") }
            item {
                Card {
                    ToggleRow("Glass tab bar", "Off = a plain solid bar", style.glassTabBar) { v -> edit { copy(glassTabBar = v) } }
                    Line()
                    ToggleRow("Glass capture button", "The round ＋ next to the tabs", style.glassCaptureButton) { v ->
                        edit { copy(glassCaptureButton = v) }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { onChange(GlassStyle()) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Reset glass to defaults")
                }
            }
            item { Spacer(Modifier.height(16.dp).windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

/** Moving lotus + text under the real tab bar, so blur, bending and fringes are easy to see. */
@Composable
private fun Preview(style: GlassStyle, dark: Boolean) {
    val backdrop = rememberLayerBackdrop()
    var tab by remember { mutableIntStateOf(0) }
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = -60f, targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "x",
    )
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(210.dp).clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Image(
                painterResource(R.drawable.glass_preview), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationX = drift.dp.toPx(); scaleX = 1.3f; scaleY = 1.3f },
            )
            Column(Modifier.padding(20.dp).graphicsLayer { translationX = -drift.dp.toPx() / 2 }) {
                Text("For You", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Archify · 72k stars · MIT", color = Color.White.copy(0.85f), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Color(0xFFF5A9BE), Color(0xFFBDB6F7), Color(0xFF8FD3FF), Color(0xFFF2BFA0)).forEach {
                        Box(Modifier.size(width = 58.dp, height = 22.dp).background(it, RoundedCornerShape(50)))
                    }
                }
            }
        }
        BottomBar(
            selected = { tab }, onSelect = { tab = it }, backdrop = backdrop, style = style, dark = dark,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp),
        )
    }
}

// ---------- rows ----------

@Composable
private fun SectionLabel(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
)

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun Line() = HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Hint(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                if (unit == "%") "${(value * 100).toInt()}%" else "${"%.0f".format(value)} $unit",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
        }
        Hint(subtitle)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ColorRow(title: String, subtitle: String, value: Long?, themeDefault: Color, onChange: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = value?.let { Color(it) } ?: themeDefault
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Hint(if (value == null) "$subtitle · theme default" else subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(32.dp).background(shown, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
    }
    if (open) ColorPicker(title, shown, onDismiss = { open = false }, onPick = { onChange(it); open = false })
}

private val swatches = listOf(
    0xFF000000, 0xFF121212, 0xFF3A3033, 0xFFFFFFFF, 0xFFF5A9BE, 0xFFF2BFA0,
    0xFFBDB6F7, 0xFF8FD3FF, 0xFF7BD88F, 0xFFFFC66D, 0xFFFF6B6B, 0xFF0088FF,
)

/** Swatches plus hue / saturation / brightness sliders; "Theme default" clears the custom color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(title: String, initial: Color, onDismiss: () -> Unit, onPick: (Long?) -> Unit) {
    val hsl = remember { FloatArray(3).also { ColorUtils.colorToHSL(initial.toArgb(), it) } }
    var h by remember { mutableStateOf(hsl[0]) }
    var s by remember { mutableStateOf(hsl[1]) }
    var l by remember { mutableStateOf(hsl[2]) }
    val current = Color(ColorUtils.HSLToColor(floatArrayOf(h, s, l)))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(44.dp).background(current, RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    swatches.forEach { argb ->
                        Box(
                            Modifier.size(32.dp).background(Color(argb), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    val out = FloatArray(3)
                                    ColorUtils.colorToHSL(argb.toInt(), out)
                                    h = out[0]; s = out[1]; l = out[2]
                                },
                        )
                    }
                }
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Slider(value = h, onValueChange = { h = it }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(value = s, onValueChange = { s = it })
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(value = l, onValueChange = { l = it })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current.toArgb().toLong() and 0xFFFFFFFFL) }) { Text("Use color") } },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("Theme default") } },
    )
}
