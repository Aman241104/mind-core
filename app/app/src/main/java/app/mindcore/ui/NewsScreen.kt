package app.mindcore.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.UniNews
import kotlinx.coroutines.launch

@Composable
fun NewsScreen(api: Api, backdrop: com.kyant.backdrop.Backdrop, glass: app.mindcore.settings.GlassStyle, dark: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val look = glass.look(dark)
    var newsList by remember { mutableStateOf<List<UniNews>>(emptyList()) }
    var newsError by remember { mutableStateOf<String?>(null) }
    var showUnseenOnly by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        scope.launch {
            runCatching { api.news(showUnseenOnly) }
                .onSuccess { newsList = it; newsError = null }
                .onFailure { newsError = it.message }
            loading = false
        }
    }

    LaunchedEffect(api, showUnseenOnly) { load() }

    Column(modifier = Modifier.fillMaxSize().background(scheme.surface)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Row(modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("University News", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = scheme.onSurface)
            GlassIconButton(Icons.Rounded.Notifications, "Toggle unseen", { showUnseenOnly = !showUnseenOnly }, backdrop, look,
                tint = if (showUnseenOnly) scheme.primary else scheme.onSurface)
        }

        Row(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Show: ", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            FilterChip(
                selected = showUnseenOnly,
                onClick = { showUnseenOnly = !showUnseenOnly },
                label = { Text("Unseen only", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.padding(start = 8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = scheme.surfaceContainerHighest,
                    labelColor = scheme.onSurfaceVariant,
                    selectedContainerColor = scheme.primaryContainer,
                    selectedLabelColor = scheme.onPrimaryContainer,
                ),
            )
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            newsError != null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text(newsError!!, color = scheme.error)
            }
            newsList.isEmpty() -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("No university updates yet — checks run periodically.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            }
            else -> PullToRefreshBox(isRefreshing = loading, onRefresh = { load() }, modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(newsList, key = { it.id }) { news ->
                        NewsCard(
                            news = news, scheme = scheme, showUnseenOnly = showUnseenOnly,
                            onOpen = { openLink(context, news.sourceUrl) },
                            onMarkSeen = { scope.launch { runCatching { api.markNewsSeen(news.id) }; load() } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewsCard(news: UniNews, scheme: ColorScheme, showUnseenOnly: Boolean, onOpen: () -> Unit, onMarkSeen: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(if (news.seen) scheme.surface.copy(alpha = 0.5f) else scheme.surfaceContainerHigh)
        .clickable(onClick = onOpen)
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(news.university, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = scheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text(news.country, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            KindBadge(news.kind, scheme)
            if (!news.seen) {
                Spacer(Modifier.width(8.dp))
                Text("NEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = scheme.primary)
            }
        }

        Text(news.headline, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)

        news.summary?.let { summary ->
            Text(summary, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(news.detectedAt, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (!news.seen) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onMarkSeen)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Mark seen", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun KindBadge(kind: String, scheme: ColorScheme) {
    val color = when (kind.lowercase()) {
        "deadline" -> scheme.errorContainer
        "scholarship" -> scheme.tertiaryContainer
        "fee_change" -> scheme.secondaryContainer
        "intake" -> scheme.primaryContainer
        else -> scheme.surfaceVariant
    }
    val textColor = when (kind.lowercase()) {
        "deadline" -> scheme.onErrorContainer
        "scholarship" -> scheme.onTertiaryContainer
        "fee_change" -> scheme.onSecondaryContainer
        "intake" -> scheme.onPrimaryContainer
        else -> scheme.onSurfaceVariant
    }
    Text(kind.replace('_', ' ').replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
        color = textColor, modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp))
}

fun openLink(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
