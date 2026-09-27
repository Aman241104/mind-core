package app.mindcore.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Where the accent color comes from. Lotus is the app's own pink, from the icon. */
enum class Accent(val label: String) { Lotus("Lotus"), Wallpaper("Wallpaper"), Mono("Mono") }

enum class ThemeMode(val label: String) { Dark("Dark"), Light("Light"), System("System") }

/** How much on-device AI runs on the phone (plan §8a-1). */
enum class PhoneAi(val label: String) { Off("Off"), Smart("Smart"), Max("Max") }

/** Where research goes (plan §8a-2). */
enum class Research(val label: String) { Auto("Auto"), Claude("Claude"), Free("Free"), HandOff("Hand-off") }

data class AppSettings(
    val accent: Accent = Accent.Lotus,
    val theme: ThemeMode = ThemeMode.Dark,
    val glass: Float = 1f, // 0 = plain frosted panel, 1 = full refraction
    val haptics: Boolean = true,
    val phoneAi: PhoneAi = PhoneAi.Smart,
    val research: Research = Research.Auto,
    val watchScreenshots: Boolean = false,
    val clipboardCheck: Boolean = true,
    val obsidian: Boolean = false,
    val notebookLm: Boolean = false,
    val perplexity: Boolean = false,
    val serverUrl: String = DEFAULT_SERVER,
    val apiToken: String = "",
)

const val DEFAULT_SERVER = "https://mindcore.mind-core.workers.dev"

private val Context.store by preferencesDataStore("settings")

private object Keys {
    val accent = stringPreferencesKey("accent")
    val theme = stringPreferencesKey("theme")
    val glass = floatPreferencesKey("glass")
    val haptics = booleanPreferencesKey("haptics")
    val phoneAi = stringPreferencesKey("phone_ai")
    val research = stringPreferencesKey("research")
    val watchScreenshots = booleanPreferencesKey("watch_screenshots")
    val clipboardCheck = booleanPreferencesKey("clipboard_check")
    val obsidian = booleanPreferencesKey("obsidian")
    val notebookLm = booleanPreferencesKey("notebooklm")
    val perplexity = booleanPreferencesKey("perplexity")
    val serverUrl = stringPreferencesKey("server_url")
    val apiToken = stringPreferencesKey("api_token")
}

private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
    this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

private fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        accent = enum(Keys.accent, d.accent),
        theme = enum(Keys.theme, d.theme),
        glass = this[Keys.glass] ?: d.glass,
        haptics = this[Keys.haptics] ?: d.haptics,
        phoneAi = enum(Keys.phoneAi, d.phoneAi),
        research = enum(Keys.research, d.research),
        watchScreenshots = this[Keys.watchScreenshots] ?: d.watchScreenshots,
        clipboardCheck = this[Keys.clipboardCheck] ?: d.clipboardCheck,
        obsidian = this[Keys.obsidian] ?: d.obsidian,
        notebookLm = this[Keys.notebookLm] ?: d.notebookLm,
        perplexity = this[Keys.perplexity] ?: d.perplexity,
        serverUrl = this[Keys.serverUrl]?.takeIf { it.isNotBlank() } ?: d.serverUrl,
        apiToken = this[Keys.apiToken] ?: d.apiToken,
    )
}

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.store.data.map { it.toSettings() }

    suspend fun update(change: (AppSettings) -> AppSettings) {
        context.store.edit { p ->
            val new = change(p.toSettings())
            p[Keys.accent] = new.accent.name
            p[Keys.theme] = new.theme.name
            p[Keys.glass] = new.glass
            p[Keys.haptics] = new.haptics
            p[Keys.phoneAi] = new.phoneAi.name
            p[Keys.research] = new.research.name
            p[Keys.watchScreenshots] = new.watchScreenshots
            p[Keys.clipboardCheck] = new.clipboardCheck
            p[Keys.obsidian] = new.obsidian
            p[Keys.notebookLm] = new.notebookLm
            p[Keys.perplexity] = new.perplexity
            p[Keys.serverUrl] = new.serverUrl
            p[Keys.apiToken] = new.apiToken
        }
    }
}
