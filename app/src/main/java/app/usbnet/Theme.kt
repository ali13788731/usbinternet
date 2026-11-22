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
val BrandBlue = Color(0xFF4F7CFF)
val BrandViolet = Color(0xFF8B5CF6)
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
    bg = Color(0xFF0B1020),
    surface = Color(0xFF121A30),
    surface2 = Color(0xFF1A2440),
    border = Color(0xFF26325A),
    text = Color(0xFFEEF2FF),
    muted = Color(0xFF93A0C4),
)

val LightPalette = AppColors(
    dark = false,
    bg = Color(0xFFF4F6FC),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEEF1FA),
    border = Color(0xFFE1E6F3),
    text = Color(0xFF0F172A),
    muted = Color(0xFF5B6785),
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
            displayLarge = t.displayLarge.copy(fontFamily = VazirFamily), displayMedium = t.displayMedium.copy(fontFamily = VazirFamily),
            displaySmall = t.displaySmall.copy(fontFamily = VazirFamily), headlineLarge = t.headlineLarge.copy(fontFamily = VazirFamily),
            headlineMedium = t.headlineMedium.copy(fontFamily = VazirFamily), headlineSmall = t.headlineSmall.copy(fontFamily = VazirFamily),
            titleLarge = t.titleLarge.copy(fontFamily = VazirFamily), titleMedium = t.titleMedium.copy(fontFamily = VazirFamily),
            titleSmall = t.titleSmall.copy(fontFamily = VazirFamily), bodyLarge = t.bodyLarge.copy(fontFamily = VazirFamily),
            bodyMedium = t.bodyMedium.copy(fontFamily = VazirFamily), bodySmall = t.bodySmall.copy(fontFamily = VazirFamily),
            labelLarge = t.labelLarge.copy(fontFamily = VazirFamily), labelMedium = t.labelMedium.copy(fontFamily = VazirFamily),
            labelSmall = t.labelSmall.copy(fontFamily = VazirFamily),
        )
        MaterialTheme(colorScheme = scheme, typography = vt, content = content)
    }
}
