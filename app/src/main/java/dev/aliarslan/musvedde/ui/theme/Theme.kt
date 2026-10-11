package dev.aliarslan.musvedde.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.aliarslan.musvedde.domain.Settings
import dev.aliarslan.musvedde.domain.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF6E5530),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFADDB0),
    onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6B5D4B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4E0C7),
    onSecondaryContainer = Color(0xFF241A0C),
    tertiary = Color(0xFF4D6544),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBF6),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBF6),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEFE0CF),
    onSurfaceVariant = Color(0xFF4F4539),
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD3C4B4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF3EA),
    surfaceContainer = Color(0xFFF5EDE4),
    surfaceContainerHigh = Color(0xFFEFE7DE),
    surfaceContainerHighest = Color(0xFFE9E1D8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFDDBF8F),
    onPrimary = Color(0xFF3E2D07),
    primaryContainer = Color(0xFF55401B),
    onPrimaryContainer = Color(0xFFFADDB0),
    secondary = Color(0xFFD7C4AC),
    onSecondary = Color(0xFF3B2F20),
    secondaryContainer = Color(0xFF524535),
    onSecondaryContainer = Color(0xFFF4E0C7),
    tertiary = Color(0xFFB3CEA6),
    onTertiary = Color(0xFF203619),
    background = Color(0xFF17130E),
    onBackground = Color(0xFFEBE1D7),
    surface = Color(0xFF17130E),
    onSurface = Color(0xFFEBE1D7),
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD3C4B4),
    outline = Color(0xFF9C8F80),
    outlineVariant = Color(0xFF4F4539),
    surfaceContainerLowest = Color(0xFF110E09),
    surfaceContainerLow = Color(0xFF1F1B16),
    surfaceContainer = Color(0xFF231F1A),
    surfaceContainerHigh = Color(0xFF2E2924),
    surfaceContainerHighest = Color(0xFF39342E),
)

fun isDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

fun colorSchemeFor(mode: ThemeMode, systemDark: Boolean, dynamic: ColorScheme? = null): ColorScheme {
    val dark = isDarkTheme(mode, systemDark)
    val base = dynamic ?: if (dark) DarkColors else LightColors
    return if (mode == ThemeMode.AMOLED) base.amoled() else base
}

private fun ColorScheme.amoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFF1C1C1C),
)

@Composable
fun MusveddeTheme(settings: Settings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = isDarkTheme(settings.themeMode, systemDark)
    val dynamic = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    val scheme = remember(settings.themeMode, systemDark, dynamic) {
        colorSchemeFor(settings.themeMode, systemDark, dynamic)
    }

    SystemBarsEffect(dark, scheme.background)

    val density = LocalDensity.current
    val scaledDensity = remember(density, settings.uiTextScale) {
        Density(density.density, density.fontScale * settings.uiTextScale)
    }
    val appearance = remember(dark, settings) {
        Appearance(
            dark = dark,
            amoled = settings.themeMode == ThemeMode.AMOLED,
            highContrast = settings.highContrast,
            animationScale = appearanceScale(settings.animationSpeed, settings.reduceMotion),
        )
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalAppearance provides appearance,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MusveddeTypography, content = content)
    }
}

@Composable
private fun SystemBarsEffect(dark: Boolean, background: Color) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(dark, background) {
        (activity as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { dark },
        )
        activity.window.decorView.setBackgroundColor(background.toArgb())
        onDispose { }
    }
}
