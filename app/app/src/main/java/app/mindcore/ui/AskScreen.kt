package app.mindcore.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.AskSource
import app.mindcore.settings.Research
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One chat message. Assistant messages carry their sources; research replies carry web links. */
data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val sources: List<AskSource> = emptyList(),
    val webSources: List<Pair<String, String>> = emptyList(),
    val notInSaves: Boolean = false,
    val question: String? = null, // for "Research online" on an answer
    val pending: Boolean = false,
    val research: Boolean = false,
)

/** Chat state lives above the tabs, so switching tabs doesn't wipe the conversation. */
class AskState {
    val messages = mutableStateListOf<ChatMessage>()
    var busy by mutableStateOf(false)
}

private val suggestions = listOf(
    "What did I save this week?",
    "What jobs did I save?",
    "Which repos can I run on my laptop?",
    "Make a learning plan from my courses",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(state: AskState, api: Api?, research: Research, onOpenItem: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.size - 1) }

    fun send(q: String) {
        val question = q.trim()
        if (question.isEmpty() || api == null || state.busy) return
        val history = state.messages.filter { !it.pending && !it.research }
            .map { (if (it.fromUser) "user" else "assistant") to it.text }
        state.messages += ChatMessage(true, question)
        state.messages += ChatMessage(false, "", pending = true)
        state.busy = true
        scope.launch {
            val reply = try {
                val a = api.ask(question, history)
                ChatMessage(false, a.answer, a.sources, notInSaves = !a.found, question = question)
            } catch (e: Exception) {
                ChatMessage(false, e.message ?: "Couldn't reach the server")
            }
            state.messages[state.messages.lastIndex] = reply
            state.busy = false
        }
    }

    fun researchOnline(question: String) {
        if (api == null) return
        if (research == Research.HandOff) {
            // Hand the question to an AI app on the phone; you read the answer there.
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Research this for me, with sources: $question"), "Ask with"))
            return
        }
        state.messages += ChatMessage(false, "", pending = true, research = true)
        val index = state.messages.lastIndex
        scope.launch {
            try {
                val (id, online) = api.startResearch(question)
                if (!online) state.messages[index] = ChatMessage(false, "Waiting for your laptop to come online…", pending = true, research = true)
                while (true) {
                    delay(4000)
                    val r = api.research(id)
                    when (r.status) {
                        "done" -> { state.messages[index] = ChatMessage(false, r.answer ?: "", webSources = r.sources, research = true); break }
                        "failed" -> { state.messages[index] = ChatMessage(false, "Research failed: ${r.error}", research = true); break }
                        "leased" -> state.messages[index] = ChatMessage(false, "Researching on your laptop…", pending = true, research = true)
                    }
                }
            } catch (e: Exception) {
                state.messages[index] = ChatMessage(false, e.message ?: "Research failed", research = true)
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item {
                Column(Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)) {
                    Text("Ask", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("Answers come only from your saves, with sources.", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (api == null) item { MessageCard("Not connected yet", "Pair this phone from the laptop with `mindcore pair`.") }
            if (state.messages.isEmpty()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s ->
                        Text(
                            s, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { send(s) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            itemsIndexed(state.messages) { _, m ->
                if (m.fromUser) UserBubble(m.text) else Reply(m, onOpenItem, onResearch = { researchOnline(it) }, onOpenUrl = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                })
            }
        }
        // Input sits above the floating glass tab bar.
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 92.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Ask about your saves") },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(28.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input); input = "" }),
                maxLines = 4,
            )
            Box(
                Modifier.padding(start = 8.dp).size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !state.busy) { send(input); input = "" },
                contentAlignment = Alignment.Center,
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                .background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reply(m: ChatMessage, onOpenItem: (String) -> Unit, onResearch: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 24.dp)) {
        if (m.research) Text("Researched online", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        if (m.pending) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(m.text.ifBlank { "Reading your saves…" }, modifier = Modifier.padding(start = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        Text(markdown(m.text, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.bodyLarge)
        if (m.sources.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            m.sources.forEach { s ->
                Text(
                    "${s.n}  ${s.title}", style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { s.itemId?.let(onOpenItem) ?: s.url?.let(onOpenUrl) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        if (m.webSources.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            m.webSources.forEachIndexed { i, (title, url) ->
                Text("${i + 1}. $title", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenUrl(url) })
            }
        }
        if (m.question != null) {
            if (m.notInSaves) FilledTonalButton(onClick = { onResearch(m.question) }) { Text("Research online") }
            else OutlinedButton(onClick = { onResearch(m.question) }, modifier = Modifier.height(36.dp)) {
                Text("Research online", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Just enough markdown for chat answers: **bold**, # headings, bullets, and [n] citations in the accent color. */
private fun markdown(src: String, accent: Color): AnnotatedString = buildAnnotatedString {
    src.lines().forEachIndexed { li, raw ->
        if (li > 0) append("\n")
        var line = raw
        val heading = line.startsWith("#")
        if (heading) line = line.trimStart('#').trim()
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) line = "•  " + line.trimStart().drop(2)
        val tokens = Regex("""\*\*(.+?)\*\*|\[(\d+)\]""")
        var last = 0
        if (heading) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        tokens.findAll(line).forEach { m ->
            append(line.substring(last, m.range.first))
            when {
                m.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
                else -> withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("[${m.groupValues[2]}]") }
            }
            last = m.range.last + 1
        }
        append(line.substring(last))
        if (heading) pop()
    }
}
