package app.usbnet

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Shared brand colours (same values on the Windows app)
val BrandBlue = Color(0xFF0D9488)   // teal (kept the old name so every screen picks it up)
val BrandViolet = Color(0xFF0EA5E9) // sky (secondary accent)
val AccentGreen = Color(0xFF22C55E)
val AccentAmber = Color(0xFFF59E0B)
val AccentRed = Color(0xFFEF4444)
val AccentCyan = Color(0xFF22D3EE)

val BrandGradient: Brush = Brush.linearGradient(listOf(BrandBlue, BrandViolet))
val GreenGradient: Brush = Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF14B8A6)))
val AmberGradient: Brush = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFF97316)))

@Immutable
class AppColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
)

val DarkPalette = AppColors(
    dark = true,
    bg = Color(0xFF0A1110),
    surface = Color(0xFF111B1A),
    surface2 = Color(0xFF182726),
    border = Color(0xFF233836),
    text = Color(0xFFEAF5F3),
    muted = Color(0xFF8DA8A3),
)

val LightPalette = AppColors(
    dark = false,
    bg = Color(0xFFF2F7F6),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE7F0EE),
    border = Color(0xFFD8E6E3),
    text = Color(0xFF0A1B19),
    muted = Color(0xFF55726D),
)

val LocalAppColors = staticCompositionLocalOf { DarkPalette }

/** Wraps the whole UI. [themeMode] is one of Prefs.THEME_SYSTEM / THEME_DARK / THEME_LIGHT. */
@Composable
fun UsbNetTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        Prefs.THEME_DARK -> true
        Prefs.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = BrandBlue,
            secondary = BrandViolet,
            background = p.bg,
            surface = p.surface,
            surfaceVariant = p.surface2,
            onBackground = p.text,
            onSurface = p.text,
            onSurfaceVariant = p.muted,
            error = AccentRed,
            outline = p.border,
        )
    } else {
        lightColorScheme(
            primary = BrandBlue,
            secondary = BrandViolet,
            background = p.bg,
            surface = p.surface,
            surfaceVariant = p.surface2,
            onBackground = p.text,
            onSurface = p.text,
            onSurfaceVariant = p.muted,
            error = AccentRed,
            outline = p.border,
        )
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalAppColors provides p) {
        val t = androidx.compose.material3.Typography()
        val vt = androidx.compose.material3.Typography(
            displayLarge = t.displayLarge.copy(fontFamily = AppFontFamily), displayMedium = t.displayMedium.copy(fontFamily = AppFontFamily),
            displaySmall = t.displaySmall.copy(fontFamily = AppFontFamily), headlineLarge = t.headlineLarge.copy(fontFamily = AppFontFamily),
            headlineMedium = t.headlineMedium.copy(fontFamily = AppFontFamily), headlineSmall = t.headlineSmall.copy(fontFamily = AppFontFamily),
            titleLarge = t.titleLarge.copy(fontFamily = AppFontFamily), titleMedium = t.titleMedium.copy(fontFamily = AppFontFamily),
            titleSmall = t.titleSmall.copy(fontFamily = AppFontFamily), bodyLarge = t.bodyLarge.copy(fontFamily = AppFontFamily),
            bodyMedium = t.bodyMedium.copy(fontFamily = AppFontFamily), bodySmall = t.bodySmall.copy(fontFamily = AppFontFamily),
            labelLarge = t.labelLarge.copy(fontFamily = AppFontFamily), labelMedium = t.labelMedium.copy(fontFamily = AppFontFamily),
            labelSmall = t.labelSmall.copy(fontFamily = AppFontFamily),
        )
        MaterialTheme(colorScheme = scheme, typography = vt, content = content)
    }
}
