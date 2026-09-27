package app.mindcore.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Lotus Studio components, from the two reference designs: pill tabs with counts, round icon buttons,
// and a hero card whose round arrow button sits in a curved notch cut out of the card's corner.

/** Card shape with a round notch at the bottom-right that hugs a button of [buttonSize] + [gap]. */
class NotchedCardShape(private val radius: Dp, private val buttonSize: Dp, private val gap: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val b = with(density) { buttonSize.toPx() }
        val g = with(density) { gap.toPx() }
        val card = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r)) }
        val notchR = b / 2 + g
        val cx = size.width - b / 2
        val cy = size.height - b / 2
        val notch = Path().apply { addOval(Rect(cx - notchR, cy - notchR, cx + notchR, cy + notchR)) }
        return Outline.Generic(Path().apply { op(card, notch, PathOperation.Difference) })
    }
}

/**
 * The big card from the references: colored, a notch in the corner, and the round arrow button in it.
 * The button sits outside the card's shape, so the notch reads as a cut-out, not an overlay.
 */
@Composable
fun HeroCard(
    color: Color,
    contentColor: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    buttonColor: Color = MaterialTheme.colorScheme.inverseSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val button = 56.dp
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().pressScale(onOpen).clip(NotchedCardShape(30.dp, button, 7.dp)).background(color)
                .padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
                content()
            }
        }
        CircleIconButton(
            Icons.AutoMirrored.Rounded.ArrowForward, "Open", onOpen,
            Modifier.align(Alignment.BottomEnd), size = button,
            container = buttonColor, content = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    outlined: Boolean = false,
) {
    Box(
        modifier.size(size).pressScale(onClick).clip(CircleShape).background(container)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(size * 0.46f)) }
}

/** "All (20)  Bookmarked  Class notes" pills; the selected one is filled. */
@Composable
fun <T> PillTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    counts: Map<T, Int> = emptyMap(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.inverseSurface else Color.Transparent, label = "pill")
            val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface, label = "pillText")
            Row(
                Modifier.pressScale { onSelect(value) }.clip(RoundedCornerShape(50)).background(bg)
                    .then(if (on) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                counts[value]?.let {
                    Text(" ($it)", style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.7f), fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}

/** Small uppercase-free meta chip: "250 words", "2 images". */
@Composable
fun MetaChip(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Press feedback used by all Lotus Studio components: a quick spring to 96%. */
@Composable
fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f), label = "press")
    return graphicsLayer { scaleX = s; scaleY = s }.clickable(source, indication = null, onClick = onClick)
}
