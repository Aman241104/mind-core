package app.mindcore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mindcore.data.ApiItem
import app.mindcore.data.Verification

val kindLabels = linkedMapOf(
    "repo" to "Repo", "tool" to "Tool", "course" to "Course", "cert" to "Cert",
    "job" to "Job", "tip" to "Tip", "video" to "Video", "playlist" to "Playlist", "book" to "Book", "other" to "Other",
)

@Composable
fun kindColor(kind: String): Color {
    val s = MaterialTheme.colorScheme
    return when (kind) {
        "repo" -> s.primary
        "tool" -> s.tertiary
        "course", "cert" -> s.secondary
        "job" -> Color(0xFF8FD3FF)
        "video", "playlist" -> Color(0xFFFF8A80)
        "book" -> Color(0xFFA5D6A7)
        else -> s.outline
    }
}

fun trustLabel(trust: String) = when (trust) {
    "verified" -> "Verified"
    "check" -> "Check claims"
    "dead" -> "Archived"
    "hype" -> "Hype"
    else -> "Unconfirmed"
}

@Composable
fun trustColor(trust: String): Color = when (trust) {
    "verified" -> Color(0xFF7BD88F)
    "check" -> Color(0xFFFFC66D)
    "dead", "hype" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> "%.1fk".format(n / 1000f)
    else -> n.toString()
}

fun Verification.summary(): String = listOfNotNull(
    repo,
    stars?.let { "★${compact(it)}" },
    license?.takeIf { it != "NOASSERTION" },
    if (archived) "archived" else null,
).joinToString(" · ")

@Composable
fun ItemCard(item: ApiItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = scheme.onSurface,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(kindLabels[item.kind] ?: item.kind, kindColor(item.kind))
                Spacer(Modifier.width(8.dp))
                Pill(trustLabel(item.trust), trustColor(item.trust))
                if (item.status != "new") {
                    Spacer(Modifier.width(8.dp))
                    Pill(item.status.replaceFirstChar { it.uppercase() }, scheme.primary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.oneLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            val detail = listOfNotNull(
                item.verification?.summary()?.takeIf { it.isNotBlank() },
                if (item.sourceCount > 1) "saved ${item.sourceCount}×" else null,
            ).joinToString(" · ")
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
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
fun MessageCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
