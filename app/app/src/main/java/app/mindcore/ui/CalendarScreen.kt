package app.mindcore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.CalendarMonth
import app.mindcore.data.DayItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Month grid: dots = what you saved that day (colored by kind), a ring = a deadline. Tap a day for its list. */
@Composable
fun CalendarScreen(api: Api, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var data by remember { mutableStateOf<CalendarMonth?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(month) {
        data = null
        runCatching { api.calendar(month) }.onSuccess { data = it; error = null }.onFailure { error = it.message }
    }
    val ym = YearMonth.parse(month)

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back",
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(10.dp).size(26.dp))
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(ym.format(DateTimeFormatter.ofPattern("MMMM")), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(ym.year.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month",
                    modifier = Modifier.clip(CircleShape).clickable { month = ym.minusMonths(1).toString() }.padding(10.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month",
                    modifier = Modifier.clip(CircleShape).clickable { month = ym.plusMonths(1).toString() }.padding(10.dp))
            }
        }
        item { MonthGrid(ym, data, selected) { selected = it } }
        if (data == null) item {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator()
            }
        }
        data?.let { d ->
            val day = LocalDate.parse(selected)
            item {
                Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }
            val due = d.deadlines[selected].orEmpty()
            val saved = d.saved[selected].orEmpty()
            if (due.isEmpty() && saved.isEmpty()) item {
                Text("Nothing saved or due this day.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
            if (due.isNotEmpty()) {
                item { Label("Due") }
                items(due, key = { "d" + it.id }) { DayRow(it, onOpen, dueLabel(day, LocalDate.parse(d.today))) }
            }
            if (saved.isNotEmpty()) {
                item { Label("Saved") }
                items(saved, key = { "s" + it.id }) { DayRow(it, onOpen, null) }
            }
        }
    }
}

@Composable
private fun MonthGrid(ym: YearMonth, data: CalendarMonth?, selected: String, onSelect: (String) -> Unit) {
    val first = ym.atDay(1)
    val lead = first.dayOfWeek.value % 7 // weeks start on Sunday
    val cells = lead + ym.lengthOfMonth()
    val today = LocalDate.now().toString()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (week in 0 until (cells + 6) / 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (dow in 0 until 7) {
                    val n = week * 7 + dow - lead + 1
                    if (n < 1 || n > ym.lengthOfMonth()) { Spacer(Modifier.weight(1f)); continue }
                    val date = ym.atDay(n).toString()
                    val saved = data?.saved?.get(date).orEmpty()
                    val due = data?.deadlines?.get(date).orEmpty()
                    val isSel = date == selected
                    val scheme = MaterialTheme.colorScheme
                    Column(
                        Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) scheme.primaryContainer else if (saved.isNotEmpty()) scheme.surfaceContainerHigh else scheme.surface.copy(alpha = 0f))
                            .then(if (due.isNotEmpty()) Modifier.border(2.dp, scheme.error, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable { onSelect(date) }.padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            n.toString(), style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (date == today) FontWeight.ExtraBold else FontWeight.Normal,
                            color = when { isSel -> scheme.onPrimaryContainer; date == today -> scheme.primary; else -> scheme.onSurface },
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            saved.take(3).forEach { Box(Modifier.size(5.dp).background(kindColor(it.kind), CircleShape)) }
                        }
                        if (saved.size > 3) Text("+${saved.size - 3}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 4.dp, top = 4.dp))

@Composable
private fun DayRow(item: DayItem, onOpen: (String) -> Unit, due: String?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onOpen(if (item.kind == "task") "note:${item.id}" else item.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill(kindLabels[item.kind] ?: if (item.kind == "task") "To-do" else item.kind, kindColor(item.kind))
        Spacer(Modifier.width(10.dp))
        Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, modifier = Modifier.weight(1f))
        due?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
    }
}

/** "today", "in 5 days", "3 days ago". */
fun dueLabel(date: LocalDate, today: LocalDate): String {
    val d = ChronoUnit.DAYS.between(today, date)
    return when {
        d == 0L -> "today"
        d == 1L -> "tomorrow"
        d > 1 -> "in $d days"
        d == -1L -> "yesterday"
        else -> "${-d} days ago"
    }
}
