package tn.careerpilot.ui.matches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tn.careerpilot.domain.JobTypes
import tn.careerpilot.domain.Levels
import tn.careerpilot.domain.MatchStatus
import tn.careerpilot.domain.WorkModes
import tn.careerpilot.ui.common.EmptyState
import tn.careerpilot.ui.common.ScoreBadge
import tn.careerpilot.ui.common.ScoreBar
import tn.careerpilot.ui.common.SectionTitle
import tn.careerpilot.ui.common.Tag
import tn.careerpilot.ui.common.UiMessageEffect

/** Détail d'une offre : score expliqué, raisons, compétences manquantes et actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchDetailScreen(viewModel: MatchDetailViewModel, onBack: () -> Unit) {
    val match by viewModel.match.observeAsState()
    val state by viewModel.uiState.observeAsState()
    val uriHandler = LocalUriHandler.current
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            Text("Détail de l'offre", style = MaterialTheme.typography.titleMedium)
        }
        val current = match
        if (current == null) {
            EmptyState("Offre introuvable", "Elle a peut-être expiré ou été masquée.")
            return@Column
        }
        if (current.status == MatchStatus.REJECTED) {
            LaunchedBack(onBack)
        }
        val offer = current.offer
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(offer.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(offer.company, offer.city, offer.country).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(JobTypes.label(offer.type))
                offer.workMode?.let { Tag(WorkModes.label(it)) }
                offer.level?.let { Tag("Niveau : ${Levels.label(it)}") }
                offer.publicationDate?.let { Tag("Publiée le ${it.take(10)}") }
            }

            Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreBadge(current.score, size = 64)
                        Spacer(Modifier.width(12.dp))
                        Text("/ 100 · compatibilité", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    ScoreBar("Similarité sémantique", current.breakdown.semantic, 40)
                    ScoreBar("Compétences clés", current.breakdown.skills, 30)
                    ScoreBar("Niveau et expérience", current.breakdown.level, 15)
                    ScoreBar("Préférences", current.breakdown.preferences, 15)
                }
            }

            if (current.reasons.isNotEmpty()) {
                SectionTitle("Pourquoi cette offre")
                current.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            if (current.missingSkills.isNotEmpty()) {
                SectionTitle("Compétences manquantes")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    current.missingSkills.forEach { Tag(it, MaterialTheme.colorScheme.errorContainer) }
                }
                Text("Signalées à titre indicatif, jamais ajoutées à votre profil.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (offer.description.isNotBlank()) {
                SectionTitle("Description")
                Text(offer.description, style = MaterialTheme.typography.bodyMedium)
            }

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::toggleSaved, modifier = Modifier.weight(1f)) {
                    Text(if (current.isSaved) "Retirer" else "Sauvegarder")
                }
                OutlinedButton(onClick = viewModel::hide, modifier = Modifier.weight(1f)) { Text("Pas pour moi") }
            }
            offer.url?.let { url ->
                Button(onClick = { uriHandler.openUri(url) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Postuler sur ${offer.source}")
                }
            }
        }
    }
}

/** Une offre masquée quitte l'écran de détail. */
@Composable
private fun LaunchedBack(onBack: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
}
