package tn.careerpilot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import tn.careerpilot.R

// Couleurs de la maquette (variables CSS --ink, --muted, --line, --aqua, --pale, --orange).
val Ink = Color(0xFF12213F)
val Muted = Color(0xFF6D7789)
val Line = Color(0xFFE5EAF1)
val Aqua = Color(0xFF007F7D)
val Pale = Color(0xFFF6F8FB)
val Orange = Color(0xFFD7723C)

val DmSans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

/**
 * Palette complète des écrans connectés, reprise des classes CSS de la maquette
 * (.offer-card, .badge, .skill, .small-btn, .nav-item…) et de leur variante .dark-mode.
 */
@Immutable
data class CpColors(
    val background: Color,
    val card: Color,
    val border: Color,
    val divider: Color,
    val title: Color,
    val text: Color,
    val body: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val badgeBackground: Color,
    val badgeText: Color,
    val warningBackground: Color,
    val warningText: Color,
    val skillBackground: Color,
    val skillText: Color,
    val chipText: Color,
    val primaryButton: Color,
    val onPrimaryButton: Color,
    val track: Color,
    val scoreBackground: Color,
    val navBackground: Color,
    val navItem: Color,
    val navItemActive: Color,
    val error: Color,
    val dashed: Color,
)

val LightCpColors = CpColors(
    background = Color.White,
    card = Color.White,
    border = Line,
    divider = Color(0xFFEDF0F4),
    title = Ink,
    text = Ink,
    body = Color(0xFF445066),
    muted = Muted,
    accent = Aqua,
    accentSoft = Color(0xFFEDF7F7),
    inputBackground = Color(0xFFFBFCFD),
    inputBorder = Line,
    badgeBackground = Color(0xFFEEF2F6),
    badgeText = Color(0xFF536177),
    warningBackground = Color(0xFFFFF1E9),
    warningText = Color(0xFFB65F30),
    skillBackground = Color(0xFFEAF5F5),
    skillText = Color(0xFF08706F),
    chipText = Color(0xFF3E4A60),
    primaryButton = Ink,
    onPrimaryButton = Color.White,
    track = Color(0xFFE5ECEC),
    scoreBackground = Color(0xFFFBFFFF),
    navBackground = Color(0xF7FFFFFF),
    navItem = Color(0xFF707B8C),
    navItemActive = Ink,
    error = Color(0xFFB42318),
    dashed = Color(0xFFD8E1E9),
)

val DarkCpColors = CpColors(
    background = Color(0xFF101B2B),
    card = Color(0xFF172538),
    border = Color(0xFF34445A),
    divider = Color(0xFF34445A),
    title = Color(0xFFF3F6FB),
    text = Color(0xFFEDF3FB),
    body = Color(0xFFD5DEEB),
    muted = Color(0xFFAAB7CA),
    accent = Color(0xFF6FDBD3),
    accentSoft = Color(0xFF123B3E),
    inputBackground = Color(0xFF111E30),
    inputBorder = Color(0xFF3B4C62),
    badgeBackground = Color(0xFF26364A),
    badgeText = Color(0xFFD5DEEB),
    warningBackground = Color(0xFF493124),
    warningText = Color(0xFFFFC49F),
    skillBackground = Color(0xFF123B3E),
    skillText = Color(0xFF6FDBD3),
    chipText = Color(0xFFD5DEEB),
    primaryButton = Color(0xFFEDF3FB),
    onPrimaryButton = Ink,
    track = Color(0xFF34445A),
    scoreBackground = Color(0xFF132631),
    navBackground = Color(0xF7101B2B),
    navItem = Color(0xFFAAB7CA),
    navItemActive = Color.White,
    error = Color(0xFFFF8A80),
    dashed = Color(0xFF43546A),
)

val LocalCpColors = staticCompositionLocalOf { LightCpColors }

/** Accès court à la palette de la maquette : `CareerPilotTheme.colors.accent`. */
object CareerPilotTheme {
    val colors: CpColors
        @Composable @ReadOnlyComposable
        get() = LocalCpColors.current
}

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = Aqua,
    onSecondary = Color.White,
    secondaryContainer = LightCpColors.accentSoft,
    onSecondaryContainer = Aqua,
    tertiary = Orange,
    background = LightCpColors.background,
    onBackground = Ink,
    surface = LightCpColors.card,
    onSurface = Ink,
    surfaceVariant = LightCpColors.badgeBackground,
    onSurfaceVariant = Muted,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    surfaceContainerLow = Color.White,
    outline = Line,
    outlineVariant = LightCpColors.divider,
    error = LightCpColors.error,
    errorContainer = LightCpColors.warningBackground,
    onErrorContainer = LightCpColors.warningText,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEDF3FB),
    onPrimary = Ink,
    secondary = DarkCpColors.accent,
    onSecondary = Ink,
    secondaryContainer = DarkCpColors.accentSoft,
    onSecondaryContainer = DarkCpColors.accent,
    tertiary = Color(0xFFFFC49F),
    background = DarkCpColors.background,
    onBackground = DarkCpColors.text,
    surface = DarkCpColors.card,
    onSurface = DarkCpColors.text,
    surfaceVariant = DarkCpColors.badgeBackground,
    onSurfaceVariant = DarkCpColors.muted,
    surfaceContainer = DarkCpColors.card,
    surfaceContainerHigh = DarkCpColors.card,
    surfaceContainerHighest = DarkCpColors.card,
    surfaceContainerLow = DarkCpColors.card,
    outline = DarkCpColors.border,
    outlineVariant = DarkCpColors.divider,
    error = DarkCpColors.error,
    errorContainer = DarkCpColors.warningBackground,
    onErrorContainer = DarkCpColors.warningText,
)

private val AppTypography = Typography().run {
    fun TextStyle.dm() = copy(fontFamily = DmSans)
    Typography(
        displayLarge = displayLarge.dm(), displayMedium = displayMedium.dm(), displaySmall = displaySmall.dm(),
        headlineLarge = headlineLarge.dm(), headlineMedium = headlineMedium.dm(), headlineSmall = headlineSmall.dm(),
        titleLarge = titleLarge.dm(), titleMedium = titleMedium.dm(), titleSmall = titleSmall.dm(),
        bodyLarge = bodyLarge.dm(), bodyMedium = bodyMedium.dm(), bodySmall = bodySmall.dm(),
        labelLarge = labelLarge.dm(), labelMedium = labelMedium.dm(), labelSmall = labelSmall.dm(),
    )
}

@Composable
fun CareerPilotTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalCpColors provides if (dark) DarkCpColors else LightCpColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
