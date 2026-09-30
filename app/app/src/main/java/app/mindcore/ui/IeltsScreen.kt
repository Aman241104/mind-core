package app.mindcore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.AttemptResult
import app.mindcore.data.IeltsAttempt
import app.mindcore.data.IeltsFeedback
import app.mindcore.data.IeltsProgress
import app.mindcore.data.IeltsResource
import app.mindcore.data.IeltsTask
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun IeltsScreen(api: Api) {
    val scheme = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }

    var progress by remember { mutableStateOf<IeltsProgress?>(null) }
    var progressError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api, tab) {
        if (tab != 0) return@LaunchedEffect
        runCatching { api.ieltsProgress() }.onSuccess { progress = it; progressError = null }
            .onFailure { progressError = it.message }
    }

    var resources by remember { mutableStateOf<List<IeltsResource>>(emptyList()) }
    var resourcesError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api, tab) {
        if (tab != 1) return@LaunchedEffect
        runCatching { api.ieltsResources() }.onSuccess { resources = it; resourcesError = null }
            .onFailure { resourcesError = it.message }
    }

    var tasks by remember { mutableStateOf<List<IeltsTask>>(emptyList()) }
    var tasksError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api, tab) {
        if (tab != 2) return@LaunchedEffect
        runCatching { api.ieltsTasks() }.onSuccess { tasks = it; tasksError = null }
            .onFailure { tasksError = it.message }
    }

    var attempts by remember { mutableStateOf<List<IeltsAttempt>>(emptyList()) }
    var attemptsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api, tab) {
        if (tab != 3) return@LaunchedEffect
        runCatching { api.ieltsAttempts() }.onSuccess { attempts = it; attemptsError = null }
            .onFailure { attemptsError = it.message }
    }

    Column(modifier = Modifier.fillMaxSize().background(scheme.surface)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(64.dp))

        Text("IELTS Prep", modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)

        PillTabs(
            options = listOf(0 to "Progress", 1 to "Study Plan", 2 to "Practice", 3 to "History"),
            selected = tab, onSelect = { tab = it },
            modifier = Modifier.padding(bottom = 8.dp),
        )

        when (tab) {
            0 -> ProgressTab(progress, progressError, scheme)
            1 -> StudyPlanTab(resources, resourcesError, scheme)
            2 -> PracticeTab(tasks, tasksError, api, scheme)
            3 -> HistoryTab(attempts, attemptsError, scheme)
            else -> {}
        }
    }
}

@Composable
fun ProgressTab(progress: IeltsProgress?, error: String?, scheme: ColorScheme) {
    if (progress == null && error == null) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (error != null) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text(error, color = scheme.error)
        }
        return
    }
    if (progress?.bySkill?.isEmpty() == true) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text("Take your first practice task to see your estimated band",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text("Overall Estimate", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                val overall = progress?.overallEstimate
                if (overall == null) {
                    Text("Not calculated", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                } else {
                    Text(String.format("%.1f", overall), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = scheme.primary)
                    Text("Band", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        items(progress?.bySkill.orEmpty(), key = { it.skill }) { skillProgress ->
            SkillProgressRow(skillProgress.skill, skillProgress.band, scheme)
        }
    }
}

@Composable
fun SkillProgressRow(skill: String, band: Double, scheme: ColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(skill.replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
        if (band > 0) {
            Text(String.format("Band %.1f", band), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = bandColor(band, scheme))
        } else {
            Text("Not set", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StudyPlanTab(resources: List<IeltsResource>, error: String?, scheme: ColorScheme) {
    if (error != null) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text(error, color = scheme.error)
        }
        return
    }
    if (resources.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text("No resources available", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        return
    }

    val grouped = resources.groupBy { it.skill }.mapValues { it.value.sortedBy { r -> r.orderHint } }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (skill, skillResources) ->
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(skill.replaceFirstChar { it.uppercase() } + " resources",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.primary)
                    skillResources.forEach { resource -> ResourceCard(resource, scheme) }
                }
            }
        }
    }
}

@Composable
fun ResourceCard(resource: IeltsResource, scheme: ColorScheme) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(scheme.surfaceContainerHigh)
        .clickable(enabled = resource.url != null) { resource.url?.let { openLink(context, it) } }
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(resource.title, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            resource.bandFocus?.let { bandFocus ->
                Text(bandFocus, style = MaterialTheme.typography.labelSmall, color = scheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(scheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        resource.note?.let { note -> Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        if (resource.url != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Open link", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp).padding(start = 4.dp), tint = scheme.primary)
            }
        }
    }
}

@Composable
fun PracticeTab(tasks: List<IeltsTask>, error: String?, api: Api, scheme: ColorScheme) {
    if (error != null) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text(error, color = scheme.error)
        }
        return
    }
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text("No tasks available yet", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        return
    }

    val grouped = tasks.groupBy { it.skill }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (skill, skillTasks) ->
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(skill.replaceFirstChar { it.uppercase() } + " tasks",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.primary)
                    skillTasks.forEach { task -> TaskCard(task, api, scheme) }
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: IeltsTask, api: Api, scheme: ColorScheme) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(scheme.surfaceContainerHigh)
        .clickable { expanded = !expanded }
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.title, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Text(task.taskType, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(scheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 4.dp))
        }

        if (expanded) {
            Text("Prompt:", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Text(task.prompt, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            if (task.skill == "writing" || task.skill == "speaking") {
                WritingSpeakingSubmission(task, api, scheme)
            } else if (task.skill == "reading" || task.skill == "listening") {
                ReadingListeningSubmission(task, api, scheme)
            }
        }
    }
}

@Composable
fun WritingSpeakingSubmission(task: IeltsTask, api: Api, scheme: ColorScheme) {
    val scope = rememberCoroutineScope()
    var response by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<IeltsFeedback?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = response, onValueChange = { response = it },
            label = { Text("Your response") },
            modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 10,
        )
        if (submitting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(onClick = {
                scope.launch {
                    submitting = true
                    error = null
                    runCatching { api.submitIeltsAttempt(task.id, "practice", task.skill, response) }
                        .onSuccess { result = it.feedback }
                        .onFailure { error = it.message }
                    submitting = false
                }
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Submit for grading")
            }
        }
        error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
        result?.let { SubmissionResultCard(it, scheme) }
    }
}

@Composable
fun ReadingListeningSubmission(task: IeltsTask, api: Api, scheme: ColorScheme) {
    val scope = rememberCoroutineScope()
    var response by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AttemptResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Prompt:", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        Text(task.prompt, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
        Text("Enter your answers (one per line):", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        OutlinedTextField(
            value = response, onValueChange = { response = it },
            label = { Text("Answer (e.g. TRUE, FALSE, NOT GIVEN)") },
            modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 10,
        )
        if (submitting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(onClick = {
                scope.launch {
                    submitting = true
                    error = null
                    runCatching { api.submitIeltsAttempt(task.id, "practice", task.skill, encodeAnswers(response)) }
                        .onSuccess { result = it }
                        .onFailure { error = it.message }
                    submitting = false
                }
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Submit for grading")
            }
        }
        error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
        result?.let { ReadingListeningResultCard(it, scheme) }
    }
}

@Composable
fun SubmissionResultCard(feedback: IeltsFeedback, scheme: ColorScheme) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your band: ", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Text(String.format("%.1f", feedback.band ?: 0.0), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = bandColor(feedback.band ?: 0.0, scheme))
        }
        if (feedback.strengths.isNotEmpty()) {
            Text("Strengths:", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            feedback.strengths.forEach { strength ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(strength, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
                }
            }
        }
        if (feedback.fixes.isNotEmpty()) {
            Text("Fixes:", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            feedback.fixes.forEach { fix -> Text(fix, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface) }
        }
        feedback.note?.let { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
fun ReadingListeningResultCard(attemptResult: AttemptResult, scheme: ColorScheme) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (attemptResult.band != null) {
            Text(String.format("Your band: %.1f", attemptResult.band), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = bandColor(attemptResult.band, scheme))
        } else {
            Text("Note: ${attemptResult.note}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Text("Score: ${attemptResult.scoreRaw ?: 0}/${attemptResult.scoreTotal ?: 0}",
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
    }
}

@Composable
fun HistoryTab(attempts: List<IeltsAttempt>, error: String?, scheme: ColorScheme) {
    if (error != null) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text(error, color = scheme.error)
        }
        return
    }
    if (attempts.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text("No attempts yet", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(attempts, key = { it.id }) { attempt -> AttemptCard(attempt, scheme) }
    }
}

@Composable
fun AttemptCard(attempt: IeltsAttempt, scheme: ColorScheme) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(attempt.skill.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(attempt.createdAt, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            if (attempt.band != null) {
                Text(String.format("Band %.1f", attempt.band), style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = bandColor(attempt.band, scheme))
            } else {
                attempt.scoreRaw?.let { scoreRaw ->
                    Text("Score: $scoreRaw/${attempt.scoreTotal}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                }
            }
        }
        attempt.feedback?.note?.let { note -> Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        attempt.feedback?.fixes?.firstOrNull()?.let { fix ->
            Text("Key fix: $fix", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

fun bandColor(band: Double, scheme: ColorScheme): Color = when {
    band < 6.0 -> scheme.error
    band <= 6.5 -> scheme.secondary
    else -> scheme.tertiary
}

private fun encodeAnswers(text: String): String {
    val answers = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return JSONArray().apply { answers.forEach { put(it) } }.toString()
}
