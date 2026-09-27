package app.mindcore.ui.glass

import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

/** A GlassStyle resolved against the current theme: concrete colors, ready for drawBackdrop. */
data class GlassLook(
    val vibrancy: Boolean,
    val blurDp: Float,
    val lensHeightDp: Float,
    val lensAmountDp: Float,
    val chromaticAberration: Boolean,
    val depthEffect: Boolean,
    val surface: Color, // tint, alpha included
    val pill: Color, // selected-tab wash, alpha included
    val highlight: Highlight,
) {
    /** Same look with the glass turned off: a plain solid bar in the theme's container color. */
    fun solid(container: Color) = copy(
        vibrancy = false, blurDp = 0f, lensHeightDp = 0f, lensAmountDp = 0f,
        chromaticAberration = false, depthEffect = false, surface = container,
    )

    companion object {
        fun highlightOf(color: Color, opacity: Float) =
            Highlight(style = HighlightStyle.Default(color = color.copy(alpha = opacity)))
    }
}
