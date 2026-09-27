package app.mindcore.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.mindcore.MainActivity
import app.mindcore.Nav
import app.mindcore.ShareActivity
import app.mindcore.data.Api
import app.mindcore.settings.SettingsStore
import app.mindcore.ui.theme.LotusDark
import app.mindcore.ui.theme.LotusLight
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// The app's lotus colors, light and dark, so widgets match the app.
private val colors = ColorProviders(light = LotusLight, dark = LotusDark)

@Composable
private fun Panel(content: @Composable () -> Unit) {
    GlanceTheme(colors = colors) {
        Box(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).cornerRadius(28.dp).padding(14.dp),
        ) { content() }
    }
}

@Composable
private fun Title(text: String, trailing: String? = null) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight())
        trailing?.let { Text(it, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp)) }
    }
}

private fun openApp(context: Context, itemId: String? = null): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply { itemId?.let { putExtra(Nav.EXTRA_OPEN_ITEM, it) } }

private fun capture(context: Context, mode: String? = null): Intent =
    Intent(context, ShareActivity::class.java).setAction(ShareActivity.ACTION_CAPTURE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { mode?.let { putExtra(ShareActivity.EXTRA_MODE, it) } }

private val kindColors = mapOf(
    "repo" to Color(0xFFF5A9BE), "tool" to Color(0xFFBDB6F7), "course" to Color(0xFFF2BFA0),
    "cert" to Color(0xFFF2BFA0), "job" to Color(0xFF8FD3FF), "video" to Color(0xFFFF8A80),
    "playlist" to Color(0xFFFF8A80), "book" to Color(0xFFA5D6A7),
)

@Composable
private fun KindDot(kind: String) {
    Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(ColorProvider(kindColors[kind] ?: Color(0xFF978A8E)))) {}
}

// ---------- 1. Quick capture: ＋ Save · 🎙 Voice · 📋 Paste ----------

class CaptureWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 60.dp), DpSize(280.dp, 60.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        Panel {
            val wide = LocalSize.current.width >= 280.dp
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (wide) {
                    Text("mind-core", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context))))
                }
                Chip("＋ Save", primary = true, action = capture(context))
                Spacer(GlanceModifier.width(6.dp))
                Chip("🎙", primary = false, action = capture(context, ShareActivity.MODE_VOICE))
                Spacer(GlanceModifier.width(6.dp))
                Chip("📋", primary = false, action = capture(context, ShareActivity.MODE_PASTE))
            }
        }
    }
}

@Composable
private fun Chip(label: String, primary: Boolean, action: Intent) {
    Box(
        GlanceModifier.height(44.dp).cornerRadius(22.dp)
            .background(if (primary) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant)
            .padding(horizontal = 16.dp).clickable(actionStartActivity(action)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(
            color = if (primary) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface,
            fontSize = 15.sp, fontWeight = FontWeight.Bold,
        ))
    }
}

// ---------- 2. Coming up: next deadlines ----------

class UpcomingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetData.read(context)
        provideContent {
            Panel {
                Column(GlanceModifier.fillMaxSize()) {
                    Title("Coming up")
                    if (snap.upcoming.isEmpty()) {
                        Text("No deadlines. Add one with a voice note or on an item.",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                            modifier = GlanceModifier.clickable(actionStartActivity(openApp(context))))
                    } else LazyColumn {
                        items(snap.upcoming, itemId = { it.id.hashCode().toLong() }) { d ->
                            val date = LocalDate.parse(d.date)
                            val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(actionStartActivity(openApp(context, d.id))),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(GlanceModifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(date.dayOfMonth.toString(), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                                    Text(date.format(DateTimeFormatter.ofPattern("MMM")), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
                                }
                                Column(GlanceModifier.defaultWeight().padding(start = 8.dp)) {
                                    Text(d.name, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
                                    Text(
                                        when { days == 0L -> "today"; days == 1L -> "tomorrow"; days > 1 -> "in $days days"; else -> "${-days} days ago" },
                                        style = TextStyle(color = if (days < 0) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 3. Fresh finds: newest items + status line ----------

class FreshWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetData.read(context)
        provideContent {
            Panel {
                Column(GlanceModifier.fillMaxSize()) {
                    val status = buildList {
                        add(if (snap.laptopOnline) "● laptop online" else "○ laptop offline")
                        if (snap.processing > 0) add("${snap.processing} processing")
                    }.joinToString(" · ")
                    Title("Fresh finds", status)
                    if (snap.fresh.isEmpty()) {
                        Text("Open mind-core once to fill this in.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                            modifier = GlanceModifier.clickable(actionStartActivity(openApp(context))))
                    } else LazyColumn {
                        items(snap.fresh, itemId = { it.id.hashCode().toLong() }) { r ->
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(actionStartActivity(openApp(context, r.id))),
                                verticalAlignment = Alignment.CenterVertically) {
                                KindDot(r.kind)
                                Spacer(GlanceModifier.width(10.dp))
                                Text(r.name, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                                    modifier = GlanceModifier.defaultWeight())
                                if (r.detail.startsWith("★")) Text(r.detail, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 4. Daily pick: one thing to actually try today ----------

class PickWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetData.read(context)
        provideContent {
            Panel {
                val pick = snap.pick
                Column(GlanceModifier.fillMaxSize()) {
                    Title("Today's pick")
                    if (pick == null) {
                        Text("Nothing waiting. Mark things as Want to see them here.",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                        return@Column
                    }
                    Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context, pick.id)))) {
                        Text(pick.name, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                        Text(pick.detail, maxLines = 3, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                    }
                    Row(GlanceModifier.fillMaxWidth()) {
                        PickButton("Open", actionStartActivity(openApp(context, pick.id)), primary = true)
                        Spacer(GlanceModifier.width(6.dp))
                        PickButton("Done", actionRunCallback<SetStatusAction>(actionParametersOf(ItemKey to pick.id, StatusKey to "done")), false)
                        Spacer(GlanceModifier.width(6.dp))
                        PickButton("Skip", actionRunCallback<SetStatusAction>(actionParametersOf(ItemKey to pick.id, StatusKey to "skip")), false)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickButton(label: String, action: androidx.glance.action.Action, primary: Boolean) {
    Box(
        GlanceModifier.height(38.dp).cornerRadius(19.dp)
            .background(if (primary) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant)
            .padding(horizontal = 14.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = if (primary) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface,
            fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

private val ItemKey = ActionParameters.Key<String>("item")
private val StatusKey = ActionParameters.Key<String>("status")

/** Done / Skip straight from the home screen: update the item, then pick again. */
class SetStatusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val s = SettingsStore(context).settings.first()
        if (s.apiToken.isBlank()) return
        runCatching { Api(s.serverUrl, s.apiToken).setStatus(parameters[ItemKey]!!, parameters[StatusKey]!!) }
        WidgetData.sync(context)
    }
}

// Receivers (one per widget, as Android requires).
class CaptureWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = CaptureWidget() }
class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = UpcomingWidget() }
class FreshWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = FreshWidget() }
class PickWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = PickWidget() }
