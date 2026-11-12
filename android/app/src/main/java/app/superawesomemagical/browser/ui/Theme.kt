package app.superawesomemagical.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superawesomemagical.browser.R

/*
 * The family's look (SuperAwesomeMagicalLinux's tools/schemes.py, Music, Fitness): Lexend, the
 * aurora's purple and green over cool slate, rounded cards, light and dark with the phone.
 */

private val Dark = darkColorScheme(
    primary = Color(0xFFCFBDFF), onPrimary = Color(0xFF341A86),
    primaryContainer = Color(0xFF4B33A8), onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF6EF2B0), onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF005233), onSecondaryContainer = Color(0xFFC0FFD8),
    tertiary = Color(0xFF8DB4FF), onTertiary = Color(0xFF0A1E4A),
    tertiaryContainer = Color(0xFF1D3B85), onTertiaryContainer = Color(0xFFDCE6FF),
    background = Color(0xFF0B1220), onBackground = Color(0xFFE5E9F0),
    surface = Color(0xFF0B1220), onSurface = Color(0xFFE5E9F0),
    surfaceVariant = Color(0xFF1E293B), onSurfaceVariant = Color(0xFFA9B4C6),
    surfaceContainerLowest = Color(0xFF070C16), surfaceContainerLow = Color(0xFF0F1828),
    surfaceContainer = Color(0xFF131D30), surfaceContainerHigh = Color(0xFF1A2539),
    surfaceContainerHighest = Color(0xFF223047),
    outline = Color(0xFF3B4A63), outlineVariant = Color(0xFF263349),
    error = Color(0xFFFF8A80), onError = Color(0xFF5C0000),
    errorContainer = Color(0xFF6B1515), onErrorContainer = Color(0xFFFFDAD6),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B3FD1), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF), onPrimaryContainer = Color(0xFF1D0060),
    secondary = Color(0xFF0B7A4F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDECE3), onSecondaryContainer = Color(0xFF002112),
    tertiary = Color(0xFF2563EB), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE6FF), onTertiaryContainer = Color(0xFF0B2A6B),
    background = Color(0xFFF5F7FB), onBackground = Color(0xFF111827),
    surface = Color(0xFFF5F7FB), onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE4E9F2), onSurfaceVariant = Color(0xFF4B5565),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEEF2F8),
    surfaceContainerHighest = Color(0xFFE6EBF3),
    outline = Color(0xFFB8C1CF), outlineVariant = Color(0xFFDDE3EC),
    error = Color(0xFFC62828), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

/** The soft glows of the aurora behind the window, as in the apps. */
data class Glow(val a: Color, val b: Color)

val LocalGlow = staticCompositionLocalOf { Glow(Color(0x55B9A6FF), Color(0x448FE6C8)) }
val LocalIsDark = staticCompositionLocalOf { false }

/** Lexend (SIL Open Font Licence), the variable font, at the weights the apps use. */
private fun lexend(weight: Int) =
    Font(R.font.lexend, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Lexend = FontFamily(lexend(300), lexend(400), lexend(500), lexend(600), lexend(700))

private val AppTypography = Typography().run {
    fun TextStyle.lexend() = copy(fontFamily = Lexend)
    Typography(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp).lexend(),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Light).lexend(),
        displaySmall = displaySmall.lexend(),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold).lexend(),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold).lexend(),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold).lexend(),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold).lexend(),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium).lexend(),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.Medium).lexend(),
        bodyLarge = bodyLarge.lexend(),
        bodyMedium = bodyMedium.lexend(),
        bodySmall = bodySmall.lexend(),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium).lexend(),
        labelMedium = labelMedium.lexend(),
        labelSmall = labelSmall.lexend(),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BrowserTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val glow = if (dark) Glow(Color(0x4D5B3FD1), Color(0x3312A37C)) else Glow(Color(0x55B9A6FF), Color(0x448FE6C8))
    CompositionLocalProvider(LocalGlow provides glow, LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
