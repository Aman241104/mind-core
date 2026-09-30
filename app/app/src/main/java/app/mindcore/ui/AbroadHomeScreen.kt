package app.mindcore.ui

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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.IeltsProgress
import app.mindcore.data.UniNews

private const val PLAN_URL = "https://claude.ai/artifact/8ukn5f94BgwociEUGWscEM"

@Composable
fun AbroadHomeScreen(
    api: Api,
    space: AppSpace,
    onSpace: (AppSpace) -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
    glass: app.mindcore.settings.GlassStyle,
    dark: Boolean,
    onOpenPractice: () -> Unit,
    onOpenNews: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val look = glass.look(dark)

    var progress by remember { mutableStateOf<IeltsProgress?>(null) }
    var news by remember { mutableStateOf<List<UniNews>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(api) {
        loading = true
        runCatching { api.ieltsProgress() }.onSuccess { progress = it }
        runCatching { api.news(unseenOnly = true) }.onSuccess { news = it }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { SpaceSwitcher(space, onSpace, modifier = Modifier.padding(top = 8.dp)) }

        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sept 2027 intake", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    Text("Study Abroad", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                }
                GlassIconButton(Icons.AutoMirrored.Rounded.OpenInNew, "Your MS Abroad Plan", { openLink(context, PLAN_URL) }, backdrop, look)
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                HeroCard(color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer, onOpen = onOpenPractice) {
                    Text("IELTS estimate", style = MaterialTheme.typography.labelLarge)
                    if (loading) {
                        Spacer(Modifier.height(4.dp))
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    } else {
                        val overall = progress?.overallEstimate
                        Text(
                            if (overall != null) String.format("Band %.1f", overall) else "Not started",
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (progress?.bySkill.isNullOrEmpty()) "Tap to take your first practice task"
                            else "${progress?.bySkill?.size ?: 0} skills tracked — tap to practice",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Rounded.EditNote, "Practice", Modifier.weight(1f), onOpenPractice)
                QuickAction(Icons.Rounded.School, "Study plan", Modifier.weight(1f), onOpenPractice)
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Latest news", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("See all", style = MaterialTheme.typography.labelLarge, color = scheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenNews).padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }

        if (!loading && news.isEmpty()) {
            item {
                Text("No new university updates right now.", style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }

        items(news.take(3), key = { it.id }) { item ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                NewsCard(news = item, scheme = scheme, showUnseenOnly = true,
                    onOpen = { openLink(context, item.sourceUrl) },
                    onMarkSeen = { onOpenNews() })
            }
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.pressScale(onClick).clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
    }
}
