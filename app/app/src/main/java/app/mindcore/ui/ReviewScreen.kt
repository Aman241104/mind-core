package app.mindcore.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.Flashcard
import app.mindcore.ui.blob.BlobState
import app.mindcore.ui.blob.LotusBlob
import kotlinx.coroutines.launch

// Flashcard review: question on the front, tap to flip, then say how well you knew it. Spaced repetition decides
// when each card comes back (again: tomorrow; easy: much later).

@Composable
fun ReviewScreen(api: Api, onBack: () -> Unit, onOpenSource: (type: String, id: String) -> Unit) {
    val scope = rememberCoroutineScope()
    val motionOn = remember { ValueAnimator.areAnimatorsEnabled() }
    var cards by remember { mutableStateOf<List<Flashcard>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var again by remember { mutableIntStateOf(0) }
    val flip = remember { Animatable(0f) } // 0 = question, 180 = answer
    val enter = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        runCatching { api.dueCards() }.onSuccess { cards = it; total = it.size }.onFailure { error = it.message }
    }

    fun grade(g: String) {
        val list = cards ?: return
        val card = list.getOrNull(index) ?: return
        scope.launch {
            runCatching { api.reviewCard(card.id, g) }.onFailure { error = it.message; return@launch }
            if (g == "again") { cards = list + card; again++ } // see it once more before the end
            index++
            flip.snapTo(0f)
            if (motionOn) { enter.snapTo(0.92f); enter.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, 500f)) }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    Column(Modifier.fillMaxSize().background(scheme.surface).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 44.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Review", style = MaterialTheme.typography.headlineSmall)
                cards?.let { Text(if (index < it.size) "${(index + 1).coerceAtMost(it.size)} of ${it.size}" else "Done",
                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) }
            }
        }
        cards?.let { if (it.isNotEmpty()) LinearProgressIndicator(
            progress = { index.toFloat() / it.size }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(50)),
        ) }
        val list = cards
        val card = list?.getOrNull(index)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                error != null && list == null -> Text(error!!, color = scheme.error)
                list == null -> LotusBlob(BlobState.Thinking, size = 72.dp, onTap = null)
                card == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LotusBlob(if (total == 0) BlobState.Sleep else BlobState.Burst, size = 96.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(if (total == 0) "Nothing to review today" else "All done for today", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (total == 0) "Make flashcards from any note or saved thing, and they'll show up here when it's time."
                        else "$total cards reviewed${if (again > 0) ", $again seen twice" else ""}. They'll come back when you're about to forget them.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
                    )
                }
                else -> {
                    val showingAnswer = flip.value > 90f
                    Column(
                        Modifier.fillMaxWidth().aspectRatio(0.78f)
                            .graphicsLayer { rotationY = flip.value; cameraDistance = 14 * density; scaleX = enter.value; scaleY = enter.value }
                            .clip(RoundedCornerShape(32.dp))
                            .background(if (showingAnswer) scheme.inverseSurface else pastelFor(card.id.hashCode()).bg())
                            .pressScale {
                                scope.launch { flip.animateTo(if (flip.value < 90f) 180f else 0f, if (motionOn) tween(380) else tween(0)) }
                            }
                            .padding(28.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // The back is drawn mirrored so it reads correctly once flipped.
                        val ink = if (showingAnswer) scheme.inverseOnSurface else pastelFor(card.id.hashCode()).ink()
                        Column(Modifier.graphicsLayer { rotationY = if (showingAnswer) 180f else 0f }.fillMaxWidth()) {
                            Text(if (showingAnswer) "Answer" else "Question", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
                            Spacer(Modifier.height(14.dp))
                            Text(if (showingAnswer) card.a else card.q, style = if (showingAnswer) MaterialTheme.typography.headlineSmall
                                else MaterialTheme.typography.headlineMedium, color = ink)
                        }
                        Text(
                            if (showingAnswer) "From: ${card.sourceTitle}" else "Tap to see the answer",
                            style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f),
                            modifier = Modifier.graphicsLayer { rotationY = if (showingAnswer) 180f else 0f }.then(
                                if (showingAnswer) Modifier.pressScale { onOpenSource(card.sourceType, card.sourceId) } else Modifier),
                        )
                    }
                }
            }
        }
        if (card != null) {
            val answered = flip.value > 90f
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("again" to "Again", "hard" to "Hard", "good" to "Good", "easy" to "Easy").forEach { (g, label) ->
                    val bg = when (g) { "again" -> scheme.errorContainer; "good" -> scheme.inverseSurface; else -> scheme.surfaceContainerHigh }
                    val fg = when (g) { "again" -> scheme.onErrorContainer; "good" -> scheme.inverseOnSurface; else -> scheme.onSurface }
                    Box(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (answered) bg else scheme.surfaceContainer)
                            .then(if (answered) Modifier.pressScale { grade(g) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (answered) fg else scheme.onSurfaceVariant.copy(alpha = 0.5f)) }
                }
            }
        }
    }
}
