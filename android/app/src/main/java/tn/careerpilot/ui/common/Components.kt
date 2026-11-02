package tn.careerpilot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Snackbar partagée par tous les écrans de l'application connectée. */
val LocalSnackbarHost = staticCompositionLocalOf { SnackbarHostState() }

/** Affiche le message ponctuel du ViewModel puis le signale comme lu. */
@Composable
fun UiMessageEffect(state: UiState?, onShown: () -> Unit) {
    val host = LocalSnackbarHost.current
    val message = state?.message
    LaunchedEffect(message) {
        if (message != null) {
            onShown()
            host.showSnackbar(message.text)
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
    )
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Couleur du score : vert au-dessus de 80, turquoise de 70 à 79, orange en dessous. */
fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF1E8E5A)
    score >= 70 -> Color(0xFF007F7D)
    else -> Color(0xFFB7791F)
}

@Composable
fun ScoreBadge(score: Int, size: Int = 48) {
    Box(
        Modifier.size(size.dp).background(scoreColor(score), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$score", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.6).sp)
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Text(
        text,
        modifier = Modifier.background(color, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

/** Barre horizontale « libellé ... valeur / max ». */
@Composable
fun ScoreBar(label: String, value: Int, max: Int, color: Color = MaterialTheme.colorScheme.secondary) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$value / $max", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(4.dp))
        LinearProgressIndicator(
            progress = { if (max == 0) 0f else value.toFloat() / max },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            color = color,
        )
    }
}
