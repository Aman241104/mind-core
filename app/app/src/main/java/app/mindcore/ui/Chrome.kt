package app.mindcore.ui

import android.graphics.Bitmap
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import app.mindcore.settings.GlassStyle
import app.mindcore.ui.glass.GlassLook
import app.mindcore.ui.glass.LiquidBottomTab
import app.mindcore.ui.glass.LiquidBottomTabs
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal val tabs = listOf(
    "For You" to Icons.Rounded.Home,
    "Library" to Icons.AutoMirrored.Rounded.List,
    "Ask" to Icons.Rounded.Search,
)

/** Soft wallpaper-colored light behind the content, so the glass has color to refract. */
@Composable
internal fun Glow(scheme: ColorScheme) {
    Canvas(Modifier.fillMaxSize()) {
        fun glow(color: Color, center: Offset, radius: Float) =
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
        glow(scheme.primary.copy(alpha = 0.40f), Offset(size.width * 0.1f, size.height * 0.08f), size.width * 0.9f)
        glow(scheme.tertiary.copy(alpha = 0.32f), Offset(size.width * 1.0f, size.height * 0.5f), size.width * 0.8f)
        glow(scheme.secondary.copy(alpha = 0.30f), Offset(size.width * 0.0f, size.height * 0.95f), size.width * 0.8f)
    }
}

/** Resolve the saved style against the theme (null colors = theme defaults, same as Kyant's catalog). */
@Composable
internal fun GlassStyle.look(dark: Boolean): GlassLook {
    val base = if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)
    return GlassLook(
        vibrancy = vibrancy,
        blurDp = blur,
        lensHeightDp = lensHeight,
        lensAmountDp = lensAmount,
        chromaticAberration = chromaticAberration,
        depthEffect = depthEffect,
        surface = (tint?.let { Color(it) } ?: base).copy(alpha = tintOpacity),
        pill = (pillColor?.let { Color(it) } ?: if (dark) Color.White else Color.Black).copy(alpha = pillOpacity),
        highlight = GlassLook.highlightOf(highlightColor?.let { Color(it) } ?: Color.White, highlightOpacity),
    )
}

/**
 * Adaptive contrast: every ~0.4 s, shrink what the glass is showing to 5×5 pixels, average its brightness
 * (blended with the tint), and fade icons/text to black or white. Same idea as Kyant's AdaptiveLuminance demo.
 */
@Composable
private fun rememberAdaptiveContent(enabled: Boolean, fallback: Color, look: GlassLook): Pair<GraphicsLayer, () -> Color> {
    val layer = rememberGraphicsLayer()
    val color = remember { Animatable(fallback) }
    LaunchedEffect(enabled, fallback, look.surface) {
        if (!enabled) {
            color.animateTo(fallback, tween(300))
            return@LaunchedEffect
        }
        val buffer = IntArray(25)
        while (isActive) {
            delay(400)
            val lum = runCatching {
                val bmp = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                Bitmap.createScaledBitmap(bmp, 5, 5, true).getPixels(buffer, 0, 5, 0, 0, 5, 5)
                buffer.sumOf { argb ->
                    0.2126 * (argb shr 16 and 0xFF) / 255 + 0.7152 * (argb shr 8 and 0xFF) / 255 + 0.0722 * (argb and 0xFF) / 255
                } / buffer.size
            }.getOrNull() ?: continue
            val a = look.surface.alpha
            val seen = lum * (1 - a) + look.surface.luminance() * a
            color.animateTo(if (seen > 0.55) Color.Black else Color.White, tween(600))
        }
    }
    return layer to { color.value }
}

private fun recordInto(layer: GraphicsLayer): DrawScope.(DrawScope.() -> Unit) -> Unit = { drawBackdrop ->
    drawBackdrop()
    layer.record { drawBackdrop() }
}

@Composable
internal fun BottomBar(
    selected: () -> Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    style: GlassStyle,
    dark: Boolean,
    modifier: Modifier,
    onCapture: () -> Unit = {},
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val glassLook = style.look(dark)
    val barLook = if (style.glassTabBar) glassLook else glassLook.solid(container)
    val buttonLook = if (style.glassCaptureButton) glassLook else glassLook.solid(container)
    val (layer, contentColor) = rememberAdaptiveContent(
        enabled = style.adaptiveContrast && style.glassTabBar,
        fallback = if (dark) Color.White else Color.Black,
        look = barLook,
    )
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
            look = barLook,
            onDrawBackdrop = recordInto(layer),
            modifier = Modifier.weight(1f),
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                LiquidBottomTab({ onSelect(index) }) {
                    val c = contentColor()
                    Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(24.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = c)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        CaptureButton(backdrop, buttonLook, contentColor, onCapture)
    }
}

/** Round glass "+" beside the tabs, like Convx's search button. Opens the capture sheet. */
@Composable
private fun CaptureButton(backdrop: Backdrop, look: GlassLook, contentColor: () -> Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    if (look.vibrancy) vibrancy()
                    blur(look.blurDp.dp.toPx())
                    lens(look.lensHeightDp.dp.toPx() * 0.66f, look.lensAmountDp.dp.toPx() * 1.33f,
                        depthEffect = look.depthEffect, chromaticAberration = look.chromaticAberration)
                },
                highlight = { look.highlight },
                onDrawSurface = { drawRect(look.surface) },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Capture", tint = contentColor(), modifier = Modifier.size(30.dp))
    }
}
