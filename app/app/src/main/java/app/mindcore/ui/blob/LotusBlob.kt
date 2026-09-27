package app.mindcore.ui.blob

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Swan, the Lotus blob: mind-core's own mascot (the idea of bloub's state-morphing avatar, not a copy of it or of
 * x.ai's). A soft jelly body in the lotus gradient with two eyes. Each state means something in the app.
 */
enum class BlobState(val label: String) {
    Idle("Idle"), // calm: floats, breathes, blinks, looks around
    Listening("Listening"), // recording a voice note: perks up, sound rings follow your voice
    Thinking("Thinking"), // Ask / brainstorm working: rolls into three bouncing dots
    Orbit("Working"), // the laptop is processing saves: little moons circle it
    Searching("Researching"), // research online: eyes scan, a comet circles
    Burst("Saved"), // saved or done: squish, pop, sparks
    Alert("Alert"), // deadline soon / something failed: becomes a "!" and shakes
    Notify("New"), // something new: a badge pops on
    Sleep("Asleep"), // laptop offline / night: eyes closed, slow breath, bubbles
    Wink("Wink"), // tap it
}

private val Pink = Color(0xFFF5A9BE)
private val Peach = Color(0xFFF2BFA0)
private val Lavender = Color(0xFFBDB6F7)
private val Rose = Color(0xFFC96A87) // deeper pink for the jelly's shaded edge
private val Ink = Color(0xFF2A1520)
private val Alarm = Color(0xFFFF6B6B)

/** Everything the drawing reads. States only set targets; springs move them. */
private class Motion {
    val radius = Animatable(1f)
    val wobble = Animatable(0.035f)
    val stretch = Animatable(1f) // >1 taller, <1 wider
    val eyeOpen = Animatable(1f)
    val eyeSize = Animatable(1f) // wide eyes when listening
    val gazeX = Animatable(0f) // -1..1
    val gazeY = Animatable(0f)
    val dots = Animatable(0f) // 1 = three thinking dots
    val moons = Animatable(0f)
    val comet = Animatable(0f)
    val bang = Animatable(0f) // 1 = "!" shape
    val shake = Animatable(0f)
    val badge = Animatable(0f)
    val sparks = Animatable(0f)
    val zzz = Animatable(0f)
    val wink = Animatable(0f)
    val listen = Animatable(0f) // sound rings
}

/**
 * @param level 0..1 loudness for [BlobState.Listening], read every frame (smoothed here).
 * @param onTap default: wink. Pass null to ignore taps.
 */
@Composable
fun LotusBlob(
    state: BlobState,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    level: () -> Float = { 0f },
    onTap: (() -> Unit)? = {},
) {
    val m = remember { Motion() }
    val scope = rememberCoroutineScope()
    // "Remove animations" in Android settings → still poses only.
    val motionOn = remember { ValueAnimator.areAnimatorsEnabled() }
    var now by remember { mutableLongStateOf(0L) }
    var voice by remember { mutableFloatStateOf(0f) } // smoothed loudness
    var winking by remember { mutableStateOf(false) }

    // One continuous clock; also smooths the voice level (fast attack, slow release, like a VU meter).
    if (motionOn) LaunchedEffect(Unit) {
        while (true) withFrameMillis {
            now = it
            val target = if (state == BlobState.Listening) level().coerceIn(0f, 1f) else 0f
            voice += (target - voice) * if (target > voice) 0.35f else 0.08f
        }
    }

    // Blinks every 2.5-5.5 s; sometimes a double blink.
    LaunchedEffect(state, winking) {
        if (!motionOn || state == BlobState.Sleep || winking) return@LaunchedEffect
        while (true) {
            delay(2500L + Random.nextLong(3000))
            repeat(if (Random.nextFloat() < 0.2f) 2 else 1) {
                m.eyeOpen.animateTo(0.06f, tween(70))
                m.eyeOpen.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 900f))
            }
        }
    }
    // Looks around now and then when idle.
    LaunchedEffect(state) {
        if (!motionOn || state != BlobState.Idle) { m.gazeX.animateTo(0f); m.gazeY.animateTo(0f); return@LaunchedEffect }
        while (true) {
            delay(1800L + Random.nextLong(2600))
            val gx = listOf(-0.9f, -0.5f, 0f, 0f, 0.5f, 0.9f).random()
            launch { m.gazeX.animateTo(gx, spring(dampingRatio = 0.75f, stiffness = 180f)) }
            m.gazeY.animateTo(listOf(-0.4f, 0f, 0f, 0.3f).random(), spring(dampingRatio = 0.75f, stiffness = 180f))
        }
    }

    val effective = if (winking) BlobState.Wink else state
    LaunchedEffect(effective) { m.goTo(effective, motionOn) }

    Canvas(
        modifier.size(size)
            .semantics { contentDescription = "Swan: ${effective.label}" }
            .then(
                if (onTap != null) Modifier.clickable(MutableInteractionSource(), indication = null) {
                    onTap()
                    if (!winking) scope.launch { winking = true; delay(900); winking = false }
                } else Modifier,
            ),
    ) { drawBlob(m, if (motionOn) now / 1000f else 0f, voice) }
}

private suspend fun Motion.goTo(s: BlobState, animate: Boolean) {
    val soft: AnimationSpec<Float> = spring(dampingRatio = 0.62f, stiffness = 240f)
    val snappy: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 600f)
    suspend fun Animatable<Float, *>.to(v: Float, spec: AnimationSpec<Float> = soft) = if (animate) animateTo(v, spec) else snapTo(v)
    coroutineScope {
        launch { radius.to(when (s) { BlobState.Sleep -> 0.94f; BlobState.Listening -> 1.04f; else -> 1f }) }
        launch { wobble.to(when (s) { BlobState.Listening -> 0.03f; BlobState.Searching, BlobState.Orbit -> 0.045f; BlobState.Sleep -> 0.015f; else -> 0.032f }) }
        launch { stretch.to(when (s) { BlobState.Sleep -> 0.88f; BlobState.Listening -> 1.06f; else -> 1f }) }
        launch { eyeSize.to(when (s) { BlobState.Listening -> 1.25f; BlobState.Alert -> 1.15f; else -> 1f }) }
        launch { dots.to(if (s == BlobState.Thinking) 1f else 0f) }
        launch { moons.to(if (s == BlobState.Orbit) 1f else 0f) }
        launch { comet.to(if (s == BlobState.Searching) 1f else 0f) }
        launch { bang.to(if (s == BlobState.Alert) 1f else 0f, snappy) }
        launch { badge.to(if (s == BlobState.Notify) 1f else 0f, snappy) }
        launch { zzz.to(if (s == BlobState.Sleep) 1f else 0f) }
        launch { wink.to(if (s == BlobState.Wink) 1f else 0f, snappy) }
        launch { listen.to(if (s == BlobState.Listening) 1f else 0f) }
        launch { eyeOpen.to(if (s == BlobState.Sleep) 0f else 1f) }
        if (s == BlobState.Listening && animate) launch { gazeY.animateTo(-0.5f, soft) } // looks up, attentive
        if (s == BlobState.Alert && animate) launch {
            // A quick shake when the alert starts, then still.
            shake.snapTo(1f)
            shake.animateTo(0f, spring(dampingRatio = 0.12f, stiffness = 900f))
        }
        if (s == BlobState.Burst) launch {
            sparks.snapTo(0f)
            if (!animate) return@launch
            stretch.animateTo(0.8f, spring(stiffness = 1500f)) // squish down...
            launch { sparks.animateTo(1f, tween(650)) }
            stretch.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 520f)) // ...and pop
        } else launch { sparks.snapTo(0f) }
    }
}

private fun DrawScope.drawBlob(m: Motion, t: Float, voice: Float) {
    val base = size.minDimension * 0.30f
    val bob = sin(t * 1.3f) * base * 0.05f * (1f - m.zzz.value * 0.6f) // gentle float, calmer asleep
    val c = Offset(center.x + m.shake.value * sin(t * 60f) * base * 0.12f, center.y - bob - base * 0.08f)
    val breath = 1f + 0.022f * sin(t * (if (m.zzz.value > 0.5f) 0.9f else 1.6f))
    val r = base * m.radius.value * breath * (1f + voice * 0.12f)
    val stretch = m.stretch.value * (1f + voice * 0.05f)
    val dots = m.dots.value

    // Ground shadow: shrinks and fades as the blob floats up, so it has weight.
    val sh = 1f - bob / (base * 0.2f) * 0.15f
    drawOval(
        Brush.radialGradient(listOf(Ink.copy(alpha = 0.28f), Color.Transparent), Offset(center.x, center.y + base * 1.12f), base * 0.9f * sh),
        Offset(center.x - base * 0.9f * sh, center.y + base * 1.02f), Size(base * 1.8f * sh, base * 0.22f),
    )

    // Sound rings: ripple out from the body; how many and how strong follows your voice.
    if (m.listen.value > 0.01f) {
        for (i in 0 until 3) {
            val p = (t * (0.55f + voice * 0.9f) + i / 3f) % 1f
            val ringR = r * (1.05f + p * 0.75f)
            drawCircle(Pink.copy(alpha = (1f - p) * (0.25f + voice * 0.65f) * m.listen.value), ringR, c,
                style = Stroke(width = base * (0.05f + voice * 0.06f) * (1f - p * 0.5f)))
        }
    }

    // Thinking: the body shrinks into the middle dot while two dots roll out of it.
    val bodyScale = 1f - dots * 0.72f
    if (dots > 0.01f) {
        for (i in listOf(-1, 1)) {
            val bounce = kotlin.math.max(0f, sin(t * 7f - i * 1.1f)) * base * 0.35f * dots
            val dc = Offset(c.x + i * base * 0.82f * dots, c.y + base * 0.1f * dots - bounce)
            drawJelly(dc, base * 0.26f * dots, 1f, 0f, t, withEyes = false, m = m)
        }
    }
    val bodyBounce = if (dots > 0.01f) kotlin.math.max(0f, sin(t * 7f)) * base * 0.35f * dots else 0f

    if (m.bang.value > 0.01f) drawBang(c, base, m.bang.value, t, m)
    if (m.bang.value < 0.99f) {
        val a = 1f - m.bang.value
        val bc = Offset(c.x, c.y + base * 0.1f * dots - bodyBounce)
        drawJelly(bc, r * bodyScale, stretch, m.wobble.value, t, withEyes = dots < 0.5f, m = m, alpha = a)
    }

    if (m.moons.value > 0.01f) {
        for (i in 0 until 3) {
            val ang = t * 2.1f + i * (2 * PI / 3).toFloat()
            val p = Offset(c.x + cos(ang) * base * 1.45f, c.y + sin(ang) * base * 0.5f)
            val front = sin(ang) > 0
            drawCircle(Lavender.copy(alpha = if (front) 1f else 0.35f), base * (if (front) 0.13f else 0.1f) * m.moons.value, p)
        }
    }
    if (m.comet.value > 0.01f) {
        rotate(t * 170f % 360f, c) {
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, Peach.copy(alpha = 0.95f * m.comet.value)), c),
                startAngle = 0f, sweepAngle = 120f, useCenter = false,
                topLeft = Offset(c.x - base * 1.45f, c.y - base * 1.45f), size = Size(base * 2.9f, base * 2.9f),
                style = Stroke(width = base * 0.1f, cap = StrokeCap.Round),
            )
            drawCircle(Peach.copy(alpha = m.comet.value), base * 0.09f, Offset(c.x + base * 1.45f * cos(2.09f), c.y + base * 1.45f * sin(2.09f)))
        }
    }
    if (m.badge.value > 0.01f) {
        val p = Offset(c.x + r * 0.78f, c.y - r * 0.78f)
        drawCircle(Color.White, base * 0.22f * m.badge.value, p)
        drawCircle(Alarm, base * 0.16f * m.badge.value, p)
    }
    val s = m.sparks.value
    if (s > 0.01f && s < 0.99f) {
        val ease = 1f - (1f - s) * (1f - s)
        for (i in 0 until 10) {
            val ang = i * (2 * PI / 10).toFloat() + 0.2f
            val from = r * (1.1f + ease * 0.55f)
            val to = from + base * 0.3f * (1f - ease)
            drawLine(if (i % 2 == 0) Pink else Peach, Offset(c.x + cos(ang) * from, c.y + sin(ang) * from),
                Offset(c.x + cos(ang) * to, c.y + sin(ang) * to), strokeWidth = base * 0.085f, cap = StrokeCap.Round, alpha = 1f - s)
        }
    }
    if (m.zzz.value > 0.01f) {
        for (i in 0 until 3) {
            val p = (t * 0.4f + i / 3f) % 1f
            drawCircle(Lavender.copy(alpha = (1f - p) * 0.85f * m.zzz.value), base * (0.06f + p * 0.08f),
                Offset(c.x + base * (0.75f + p * 0.5f) + sin(p * 6f) * base * 0.08f, c.y - base * (0.6f + p * 1.0f)))
        }
    }
}

/** A jelly body: smooth liquid outline, lotus gradient, shaded edge, rim light, highlight, optional eyes. */
private fun DrawScope.drawJelly(
    c: Offset, r: Float, stretch: Float, wobble: Float, t: Float, withEyes: Boolean, m: Motion, alpha: Float = 1f,
) {
    if (r <= 0.5f) return
    val path = liquidPath(c, r, stretch, wobble, t)
    val turn = t * 0.3f
    val d = Offset(cos(turn) * r, sin(turn) * r)
    drawPath(path, Brush.linearGradient(listOf(Pink, Peach, Lavender), start = c - d, end = c + d), alpha = alpha)
    clipPath(path) {
        // Depth: the lower edge falls into shadow, like light passing through jelly.
        drawCircle(Brush.radialGradient(listOf(Color.Transparent, Rose.copy(alpha = 0.55f)), Offset(c.x, c.y - r * 0.35f), r * 1.35f),
            r * 1.6f, c, alpha = alpha)
        // Soft inner glow near the top.
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), Offset(c.x, c.y - r * 0.5f), r * 0.9f),
            r * 0.9f, Offset(c.x, c.y - r * 0.5f), alpha = alpha)
    }
    // Rim light: a thin bright edge, stronger at the top.
    drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f)), c.y - r, c.y + r),
        alpha = alpha, style = Stroke(width = r * 0.035f))
    // Glossy highlight.
    val h = Offset(c.x - r * 0.4f, c.y - r * 0.5f * stretch)
    drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), h, r * 0.28f),
        Offset(h.x - r * 0.26f, h.y - r * 0.15f), Size(r * 0.52f, r * 0.3f), alpha = alpha)
    if (withEyes) drawEyes(c, r, stretch, m, t, alpha)
}

/** 120 points joined with smooth quadratic curves (no facets), made liquid by three slow waves. */
private fun liquidPath(c: Offset, r: Float, stretch: Float, wobble: Float, t: Float): Path {
    val n = 120
    val pts = List(n) { i ->
        val a = i.toFloat() / n * 2f * PI.toFloat()
        val w = 1f + wobble * (sin(3 * a + t * 1.2f) + 0.55f * sin(5 * a - t * 1.6f) + 0.35f * sin(2 * a + t * 0.8f))
        // Slightly flatter bottom, like it's sitting on something.
        val flatten = if (sin(a) > 0) 1f - 0.06f * sin(a) else 1f
        Offset(c.x + cos(a) * r * w / stretch.coerceAtLeast(0.5f).let { kotlin.math.sqrt(it) },
            c.y + sin(a) * r * w * flatten * kotlin.math.sqrt(stretch))
    }
    val path = Path()
    val mid = { a: Offset, b: Offset -> Offset((a.x + b.x) / 2, (a.y + b.y) / 2) }
    path.moveTo(mid(pts.last(), pts[0]).x, mid(pts.last(), pts[0]).y)
    for (i in pts.indices) {
        val p = pts[i]
        val q = mid(p, pts[(i + 1) % n])
        path.quadraticTo(p.x, p.y, q.x, q.y)
    }
    path.close()
    return path
}

/** Alert: a rounded "!" bar with eyes and the dot below, in jelly style. */
private fun DrawScope.drawBang(c: Offset, base: Float, k: Float, t: Float, m: Motion) {
    val w = base * 0.7f
    val h = base * 1.55f * (0.6f + 0.4f * k)
    val top = Offset(c.x - w / 2, c.y - h * 0.62f)
    val barPath = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(top.x, top.y, top.x + w, top.y + h, CornerRadius(w / 2)))
    }
    val g = Brush.verticalGradient(listOf(Pink, Peach), top.y, top.y + h)
    drawPath(barPath, g, alpha = k)
    clipPath(barPath) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Rose.copy(alpha = 0.5f)), top.y + h * 0.5f, top.y + h), alpha = k)
    }
    drawPath(barPath, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), top.y, top.y + h),
        alpha = k, style = Stroke(width = w * 0.05f))
    // Eyes on the bar.
    val ey = top.y + h * 0.28f
    for (dx in listOf(-w * 0.18f, w * 0.18f)) {
        drawOval(Ink.copy(alpha = k), Offset(c.x + dx - w * 0.07f, ey - w * 0.12f * m.eyeOpen.value), Size(w * 0.14f, w * 0.24f * m.eyeOpen.value))
    }
    val dotY = top.y + h + base * 0.32f
    drawCircle(Brush.radialGradient(listOf(Alarm, Rose), Offset(c.x, dotY), w * 0.42f), w * 0.38f * k, Offset(c.x, dotY))
}

private fun DrawScope.drawEyes(c: Offset, r: Float, stretch: Float, m: Motion, t: Float, alpha: Float) {
    val open = m.eyeOpen.value
    val scan = if (m.comet.value > 0.01f) sin(t * 3f) * m.comet.value else 0f
    val gx = (m.gazeX.value + scan) * r * 0.12f
    val gy = m.gazeY.value * r * 0.09f
    val y = c.y - r * 0.06f * stretch + gy
    val gap = r * 0.3f
    val size = m.eyeSize.value
    val w = r * 0.12f * size
    val h = r * 0.19f * size * kotlin.math.sqrt(stretch)
    listOf(-gap, gap).forEachIndexed { i, dx ->
        val o = if (i == 1) open * (1f - m.wink.value) else open
        val e = Offset(c.x + dx + gx, y)
        if (o < 0.15f) {
            drawArc(Ink.copy(alpha = alpha), 15f, 150f, false, Offset(e.x - w, e.y - h * 0.35f), Size(w * 2, h * 0.7f),
                style = Stroke(width = w * 0.45f, cap = StrokeCap.Round))
        } else {
            drawOval(Ink.copy(alpha = alpha), Offset(e.x - w, e.y - h * o), Size(w * 2, h * 2 * o))
            // Catchlight follows the gaze a little, which is what makes eyes feel alive.
            drawCircle(Color.White.copy(alpha = 0.9f * alpha), w * 0.34f, Offset(e.x + w * 0.35f + gx * 0.15f, e.y - h * o * 0.42f))
        }
    }
    // Cheeks, very faint, only when happy (burst / wink).
    val blush = maxOf(m.sparks.value.let { if (it in 0.01f..0.99f) 1f - it else 0f }, m.wink.value)
    if (blush > 0.01f) for (dx in listOf(-gap * 1.55f, gap * 1.55f)) {
        drawOval(Rose.copy(alpha = 0.45f * blush * alpha), Offset(c.x + dx - w * 1.1f, y + h * 0.9f), Size(w * 2.2f, w * 1.1f))
    }
}

