package app.mindcore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindcore.settings.Accent
import app.mindcore.settings.AppSettings
import app.mindcore.settings.PhoneAi
import app.mindcore.settings.Research
import app.mindcore.settings.ThemeMode
import app.mindcore.BuildConfig
import app.mindcore.update.UpdateBanner
import app.mindcore.update.UpdateState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    updates: UpdateState,
    onOpenGlass: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back",
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(10.dp).size(26.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
        }

        item { Section("Look") }
        item {
            Group {
                Choice("Accent color", Accent.entries, settings.accent, { it.label },
                    hint = "Lotus is the pink from the app icon. Wallpaper follows your phone's Material You colors.") { v ->
                    onChange { it.copy(accent = v) }
                }
                Divider()
                Choice("Theme", ThemeMode.entries, settings.theme, { it.label }) { v -> onChange { it.copy(theme = v) } }
                Divider()
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenGlass).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Liquid Glass", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${settings.glass.preset.label} · blur, lens, tint, highlight, per component",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
                Divider()
                Toggle("Haptics", "Small taps when you switch tabs and finish actions", settings.haptics) { v ->
                    onChange { it.copy(haptics = v) }
                }
            }
        }

        item { Section("AI") }
        item {
            Group {
                Choice("Phone AI", PhoneAi.entries, settings.phoneAi, { it.label },
                    hint = when (settings.phoneAi) {
                        PhoneAi.Off -> "Nothing runs on the phone; everything goes to the cloud or laptop."
                        PhoneAi.Smart -> "Speech-to-text and screenshot text on the phone. Gemma only when offline."
                        PhoneAi.Max -> "Everything the phone can do, plus Ask offline (~2.3 GB of models)."
                    }) { v -> onChange { it.copy(phoneAi = v) } }
                Divider()
                Choice("Research", Research.entries, settings.research, { it.label },
                    hint = when (settings.research) {
                        Research.Auto -> "Laptop online → Claude. Laptop off → free Gemini with Google Search."
                        Research.Claude -> "Always your laptop's Claude. Waits if the laptop is off."
                        Research.Free -> "Gemini + Google Search from the cloud. No laptop needed."
                        Research.HandOff -> "Opens Claude / ChatGPT / Gemini / Perplexity with a ready prompt."
                    }) { v -> onChange { it.copy(research = v) } }
                Divider()
                Info("Laptop brain", "Not connected yet. Comes with the backend (M1).")
            }
        }

        item { Section("Capture") }
        item {
            Group {
                Toggle("Watch screenshots", "Ask \"Save to mind-core?\" when you take one (M2)", settings.watchScreenshots) { v ->
                    onChange { it.copy(watchScreenshots = v) }
                }
                Divider()
                Toggle("Offer copied links", "When you open the app with a link copied (M2)", settings.clipboardCheck) { v ->
                    onChange { it.copy(clipboardCheck = v) }
                }
            }
        }

        item { Section("Connect") }
        item {
            Group {
                Toggle("Obsidian", "Two-way notes in 40 - mind-core/ in your vault (M5)", settings.obsidian) { v ->
                    onChange { it.copy(obsidian = v) }
                }
                Divider()
                Toggle("NotebookLM", "One Google Doc per collection, for podcasts and quizzes (M5)", settings.notebookLm) { v ->
                    onChange { it.copy(notebookLm = v) }
                }
                Divider()
                Toggle("Perplexity", "\"Ask Perplexity\" button and thread-link import (M5)", settings.perplexity) { v ->
                    onChange { it.copy(perplexity = v) }
                }
            }
        }

        item { Section("Server") }
        item {
            Group {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    OutlinedTextField(
                        value = settings.serverUrl,
                        onValueChange = { v -> onChange { it.copy(serverUrl = v.trim()) } },
                        label = { Text("Cloudflare API address") },
                        placeholder = { Text("filled in automatically in M1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { Section("About") }
        item {
            Group {
                Info("mind-core", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Divider()
                val scope = rememberCoroutineScope()
                Row(
                    Modifier.fillMaxWidth().clickable { scope.launch { updates.check(manual = true) } }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Check for updates", style = MaterialTheme.typography.titleMedium)
                        Text(
                            updates.message ?: updates.release?.let { "Version ${it.versionName} is ready. Update from For You." }
                                ?: "New versions come from your own server.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (updates.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                updates.release?.let { UpdateBanner(updates, Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) }
                Divider()
                Info("Glass", "Kyant0/backdrop and its catalog components, Apache License 2.0")
                Divider()
                Info("Icon", "Your lotus")
            }
        }
        item { Spacer(Modifier.height(24.dp).windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun Divider() = HorizontalDivider(
    Modifier.padding(horizontal = 20.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
)

@Composable
private fun <T> Choice(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    hint: String? = null,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    label = { Text(label(option), maxLines = 1) },
                )
            }
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Info(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
