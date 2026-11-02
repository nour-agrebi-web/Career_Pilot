package tn.careerpilot.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tn.careerpilot.domain.JobTypes
import tn.careerpilot.ui.common.EmptyState
import tn.careerpilot.ui.common.ScoreBar
import tn.careerpilot.ui.common.SectionTitle
import tn.careerpilot.ui.common.scoreColor

/** Tableau de bord : répartition des scores, offres par type et compétences à renforcer. */
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val stats by viewModel.statistics.observeAsState()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            Text("Statistiques", style = MaterialTheme.typography.titleMedium)
        }
        val s = stats
        if (s == null || s.total == 0) {
            EmptyState("Pas encore de données", "Les statistiques apparaîtront avec vos premières offres recommandées.")
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard("Offres", "${s.total}", Modifier.weight(1f))
                KpiCard("Score moyen", "${s.averageScore}", Modifier.weight(1f))
                KpiCard("Sauvegardées", "${s.saved}", Modifier.weight(1f))
            }

            SectionTitle("Répartition des scores")
            ScoreHistogram(s.histogram)

            SectionTitle("Par type de contrat")
            val max = s.byType.maxOfOrNull { it.count } ?: 1
            s.byType.forEach { ScoreBar("${JobTypes.label(it.type)} · score moyen ${it.averageScore}", it.count, max) }

            if (s.topMissingSkills.isNotEmpty()) {
                SectionTitle("Compétences à renforcer")
                Text(
                    "Les compétences les plus demandées par vos offres et absentes de votre profil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val top = s.topMissingSkills.first().second
                s.topMissingSkills.forEach { (skill, count) -> ScoreBar(skill, count, top, MaterialTheme.colorScheme.error) }
            }
            if (s.hidden > 0) {
                Text("${s.hidden} offre(s) masquée(s) ne sont pas comptées.", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

/** Histogramme dessiné avec Canvas : une barre par tranche de score, colorée comme les badges. */
@Composable
private fun ScoreHistogram(histogram: List<Pair<String, Int>>) {
    val max = (histogram.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / histogram.size
            val barWidth = slot * 0.6f
            histogram.forEachIndexed { index, (range, count) ->
                val left = index * slot + (slot - barWidth) / 2
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), CornerRadius(8f, 8f))
                val barHeight = size.height * count / max
                val lowerBound = range.substringBefore('-').toInt()
                drawRoundRect(
                    scoreColor(lowerBound),
                    Offset(left, size.height - barHeight),
                    Size(barWidth, barHeight),
                    CornerRadius(8f, 8f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            histogram.forEach { (range, count) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(range, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

