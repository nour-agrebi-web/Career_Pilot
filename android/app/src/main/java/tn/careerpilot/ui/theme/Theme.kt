package tn.careerpilot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import tn.careerpilot.R

// Couleurs de la maquette (variables CSS --ink, --muted, --line, --aqua).
val Ink = Color(0xFF12213F)
val Muted = Color(0xFF6D7789)
val Line = Color(0xFFE5EAF1)
val Aqua = Color(0xFF007F7D)

val DmSans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

private val LightColors = lightColorScheme(primary = Ink, secondary = Aqua, onSurface = Ink)
private val DarkColors = darkColorScheme(primary = Color(0xFFEDF3FB), onPrimary = Ink, secondary = Color(0xFF6FDBD3))

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
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
