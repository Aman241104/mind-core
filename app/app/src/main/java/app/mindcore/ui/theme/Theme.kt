package app.mindcore.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.mindcore.settings.Accent
import app.mindcore.settings.AppSettings
import app.mindcore.settings.ThemeMode

// Lotus palette, sampled from the app icon: pink petals, warm peach core, lavender glints.
internal val LotusDark = darkColorScheme(
    primary = Color(0xFFF5A9BE), onPrimary = Color(0xFF4A1426),
    primaryContainer = Color(0xFF6B2A3D), onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFF2BFA0), onSecondary = Color(0xFF45240F),
    tertiary = Color(0xFFBDB6F7), onTertiary = Color(0xFF26215C),
    background = Color(0xFF0B090A), onBackground = Color(0xFFEDE3E5),
    surface = Color(0xFF0B090A), onSurface = Color(0xFFEDE3E5),
    surfaceVariant = Color(0xFF3A3033), onSurfaceVariant = Color(0xFFCDBFC3),
    surfaceContainerLow = Color(0xFF161214), surfaceContainer = Color(0xFF1B1719),
    surfaceContainerHigh = Color(0xFF241F21), surfaceContainerHighest = Color(0xFF2F292B),
    outline = Color(0xFF978A8E), outlineVariant = Color(0xFF4D4346),
)

// Warm paper, ink text, one lotus accent; neutrals tinted a touch toward the pink.
internal val LotusLight = lightColorScheme(
    primary = Color(0xFFA23A5A), onPrimary = Color(0xFFFFFBFA),
    primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF3E0619),
    secondary = Color(0xFF8C4F2A), onSecondary = Color(0xFFFFFBFA),
    secondaryContainer = Color(0xFFFFDCC7), onSecondaryContainer = Color(0xFF331200),
    tertiary = Color(0xFF5B55A8), onTertiary = Color(0xFFFFFBFA),
    tertiaryContainer = Color(0xFFE3DFFF), onTertiaryContainer = Color(0xFF161060),
    background = Color(0xFFFBF7F4), onBackground = Color(0xFF1F1A1C),
    surface = Color(0xFFFBF7F4), onSurface = Color(0xFF1F1A1C),
    surfaceVariant = Color(0xFFF1E6E7), onSurfaceVariant = Color(0xFF524346),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F1EE),
    surfaceContainer = Color(0xFFF3ECE9), surfaceContainerHigh = Color(0xFFEDE5E2),
    surfaceContainerHighest = Color(0xFFE6DEDB),
    outline = Color(0xFF857376), outlineVariant = Color(0xFFD8C2C5),
    inverseSurface = Color(0xFF1F1A1C), inverseOnSurface = Color(0xFFF7EEEF), inversePrimary = Color(0xFFF5A9BE),
    error = Color(0xFFBA1A1A),
)

/** Pastel card colors (from the note-app reference). [light] on paper, [dark] on the dark theme; text uses [ink]. */
data class Pastel(val light: Color, val dark: Color, val inkLight: Color, val inkDark: Color)

val Pastels = listOf(
    Pastel(Color(0xFFDCEBCB), Color(0xFF2F3B27), Color(0xFF1F2A16), Color(0xFFDCEBCB)), // sage
    Pastel(Color(0xFFFBDCE6), Color(0xFF45252F), Color(0xFF3E0619), Color(0xFFFBDCE6)), // blush
    Pastel(Color(0xFFFCE3CF), Color(0xFF45301F), Color(0xFF331200), Color(0xFFFCE3CF)), // peach
    Pastel(Color(0xFFE4E0FB), Color(0xFF2E2A4A), Color(0xFF161060), Color(0xFFE4E0FB)), // lavender
    Pastel(Color(0xFFD7ECF6), Color(0xFF223540), Color(0xFF0B2530), Color(0xFFD7ECF6)), // sky
)

private val MonoDark = darkColorScheme(
    primary = Color(0xFFE6E6E6), onPrimary = Color.Black, secondary = Color(0xFFBDBDBD),
    tertiary = Color(0xFF9E9E9E), surface = Color(0xFF0A0A0A), background = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF1F1F1F),
)

private val MonoLight = lightColorScheme(
    primary = Color(0xFF1A1A1A), secondary = Color(0xFF424242), tertiary = Color(0xFF616161),
    surfaceContainerHigh = Color(0xFFEDEDED),
)

@Composable
fun isDark(settings: AppSettings): Boolean = when (settings.theme) {
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
    ThemeMode.System -> isSystemInDarkTheme()
}

@Composable
fun colorSchemeFor(settings: AppSettings): ColorScheme {
    val dark = isDark(settings)
    val context = LocalContext.current
    return when (settings.accent) {
        Accent.Lotus -> if (dark) LotusDark else LotusLight
        Accent.Mono -> if (dark) MonoDark else MonoLight
        Accent.Wallpaper ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (dark) LotusDark else LotusLight
    }
}
