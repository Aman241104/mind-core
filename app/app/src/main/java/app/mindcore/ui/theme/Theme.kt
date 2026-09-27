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

internal val LotusLight = lightColorScheme(
    primary = Color(0xFFA23A5A), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF3E0619),
    secondary = Color(0xFF8C4F2A), tertiary = Color(0xFF5B55A8),
    background = Color(0xFFFFF8F8), surface = Color(0xFFFFF8F8), onSurface = Color(0xFF221A1C),
    surfaceContainerHigh = Color(0xFFF6E9EB), surfaceContainerHighest = Color(0xFFF0E3E5),
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
