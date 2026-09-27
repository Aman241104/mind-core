package app.mindcore.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    val glass: GlassStyle = GlassStyle(),
    val haptics: Boolean = true,
    val phoneAi: PhoneAi = PhoneAi.Smart,
    val research: Research = Research.Auto,
    val watchScreenshots: Boolean = false,
    val clipboardCheck: Boolean = true,
    val obsidian: Boolean = false,
    val notebookLm: Boolean = false,
    val perplexity: Boolean = false,
    val edgeRight: Boolean = true, // edge handle on the right side (false = left)
    val edgePosition: Float = 0.35f, // handle height on screen, 0 = top, 1 = bottom
    val copyPopup: Boolean = true, // "Save to mind-core?" pill after you copy something
    val serverUrl: String = DEFAULT_SERVER,
    val apiToken: String = "",
)

const val DEFAULT_SERVER = "https://mindcore.mind-core.workers.dev"

private val Context.store by preferencesDataStore("settings")

private object Keys {
    val accent = stringPreferencesKey("accent")
    val theme = stringPreferencesKey("theme")
    // Liquid Glass page
    val gPreset = stringPreferencesKey("g_preset")
    val gVibrancy = booleanPreferencesKey("g_vibrancy")
    val gBlur = floatPreferencesKey("g_blur")
    val gLensHeight = floatPreferencesKey("g_lens_height")
    val gLensAmount = floatPreferencesKey("g_lens_amount")
    val gChromatic = booleanPreferencesKey("g_chromatic")
    val gDepth = booleanPreferencesKey("g_depth")
    val gTint = longPreferencesKey("g_tint")
    val gTintOpacity = floatPreferencesKey("g_tint_opacity")
    val gPill = longPreferencesKey("g_pill")
    val gPillOpacity = floatPreferencesKey("g_pill_opacity")
    val gHighlight = longPreferencesKey("g_highlight")
    val gHighlightOpacity = floatPreferencesKey("g_highlight_opacity")
    val gAdaptive = booleanPreferencesKey("g_adaptive")
    val gTabBar = booleanPreferencesKey("g_tab_bar")
    val gCapture = booleanPreferencesKey("g_capture")
    val haptics = booleanPreferencesKey("haptics")
    val phoneAi = stringPreferencesKey("phone_ai")
    val research = stringPreferencesKey("research")
    val watchScreenshots = booleanPreferencesKey("watch_screenshots")
    val clipboardCheck = booleanPreferencesKey("clipboard_check")
    val obsidian = booleanPreferencesKey("obsidian")
    val notebookLm = booleanPreferencesKey("notebooklm")
    val perplexity = booleanPreferencesKey("perplexity")
    val edgeRight = booleanPreferencesKey("edge_right")
    val edgePosition = floatPreferencesKey("edge_position")
    val copyPopup = booleanPreferencesKey("copy_popup")
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
        glass = glassStyle(),
        haptics = this[Keys.haptics] ?: d.haptics,
        phoneAi = enum(Keys.phoneAi, d.phoneAi),
        research = enum(Keys.research, d.research),
        watchScreenshots = this[Keys.watchScreenshots] ?: d.watchScreenshots,
        clipboardCheck = this[Keys.clipboardCheck] ?: d.clipboardCheck,
        obsidian = this[Keys.obsidian] ?: d.obsidian,
        notebookLm = this[Keys.notebookLm] ?: d.notebookLm,
        perplexity = this[Keys.perplexity] ?: d.perplexity,
        edgeRight = this[Keys.edgeRight] ?: d.edgeRight,
        edgePosition = this[Keys.edgePosition] ?: d.edgePosition,
        copyPopup = this[Keys.copyPopup] ?: d.copyPopup,
        serverUrl = this[Keys.serverUrl]?.takeIf { it.isNotBlank() } ?: d.serverUrl,
        apiToken = this[Keys.apiToken] ?: d.apiToken,
    )
}

private fun Preferences.glassStyle(): GlassStyle {
    val d = GlassStyle()
    return GlassStyle(
        preset = enum(Keys.gPreset, d.preset),
        vibrancy = this[Keys.gVibrancy] ?: d.vibrancy,
        blur = this[Keys.gBlur] ?: d.blur,
        lensHeight = this[Keys.gLensHeight] ?: d.lensHeight,
        lensAmount = this[Keys.gLensAmount] ?: d.lensAmount,
        chromaticAberration = this[Keys.gChromatic] ?: d.chromaticAberration,
        depthEffect = this[Keys.gDepth] ?: d.depthEffect,
        tint = this[Keys.gTint],
        tintOpacity = this[Keys.gTintOpacity] ?: d.tintOpacity,
        pillColor = this[Keys.gPill],
        pillOpacity = this[Keys.gPillOpacity] ?: d.pillOpacity,
        highlightColor = this[Keys.gHighlight],
        highlightOpacity = this[Keys.gHighlightOpacity] ?: d.highlightOpacity,
        adaptiveContrast = this[Keys.gAdaptive] ?: d.adaptiveContrast,
        glassTabBar = this[Keys.gTabBar] ?: d.glassTabBar,
        glassCaptureButton = this[Keys.gCapture] ?: d.glassCaptureButton,
    )
}

private fun MutablePreferences.putGlass(g: GlassStyle) {
    this[Keys.gPreset] = g.preset.name
    this[Keys.gVibrancy] = g.vibrancy
    this[Keys.gBlur] = g.blur
    this[Keys.gLensHeight] = g.lensHeight
    this[Keys.gLensAmount] = g.lensAmount
    this[Keys.gChromatic] = g.chromaticAberration
    this[Keys.gDepth] = g.depthEffect
    // null = follow the theme, so remove the key instead of storing a color
    g.tint?.let { this[Keys.gTint] = it } ?: remove(Keys.gTint)
    this[Keys.gTintOpacity] = g.tintOpacity
    g.pillColor?.let { this[Keys.gPill] = it } ?: remove(Keys.gPill)
    this[Keys.gPillOpacity] = g.pillOpacity
    g.highlightColor?.let { this[Keys.gHighlight] = it } ?: remove(Keys.gHighlight)
    this[Keys.gHighlightOpacity] = g.highlightOpacity
    this[Keys.gAdaptive] = g.adaptiveContrast
    this[Keys.gTabBar] = g.glassTabBar
    this[Keys.gCapture] = g.glassCaptureButton
}

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.store.data.map { it.toSettings() }

    suspend fun update(change: (AppSettings) -> AppSettings) {
        context.store.edit { p ->
            val new = change(p.toSettings())
            p[Keys.accent] = new.accent.name
            p[Keys.theme] = new.theme.name
            p.putGlass(new.glass)
            p[Keys.haptics] = new.haptics
            p[Keys.phoneAi] = new.phoneAi.name
            p[Keys.research] = new.research.name
            p[Keys.watchScreenshots] = new.watchScreenshots
            p[Keys.clipboardCheck] = new.clipboardCheck
            p[Keys.obsidian] = new.obsidian
            p[Keys.notebookLm] = new.notebookLm
            p[Keys.perplexity] = new.perplexity
            p[Keys.edgeRight] = new.edgeRight
            p[Keys.edgePosition] = new.edgePosition
            p[Keys.copyPopup] = new.copyPopup
            p[Keys.serverUrl] = new.serverUrl
            p[Keys.apiToken] = new.apiToken
        }
    }
}
