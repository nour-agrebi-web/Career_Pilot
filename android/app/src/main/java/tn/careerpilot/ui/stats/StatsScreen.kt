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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.unit.sp
import tn.careerpilot.domain.JobTypes
import tn.careerpilot.ui.common.BackHeader
import tn.careerpilot.ui.common.CpCard
import tn.careerpilot.ui.common.EmptyState
import tn.careerpilot.ui.common.MutedText
import tn.careerpilot.ui.common.ScoreBar
import tn.careerpilot.ui.common.SectionTitle
import tn.careerpilot.ui.common.scoreColor
import tn.careerpilot.ui.theme.CareerPilotTheme
import tn.careerpilot.ui.theme.DmSans

/** Tableau de bord : répartition des scores, offres par type et compétences à renforcer. */
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val stats by viewModel.statistics.observeAsState()
    val c = CareerPilotTheme.colors

    Column(Modifier.fillMaxSize()) {
        BackHeader("Tableau de bord", onBack, Icons.AutoMirrored.Filled.ArrowBack)
        Text(
            "Statistiques",
            color = c.title,
            fontFamily = DmSans,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            letterSpacing = (-0.6).sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val s = stats
        if (s == null || s.total == 0) {
            EmptyState("Pas encore de données", "Les statistiques apparaîtront avec vos premières offres recommandées.", icon = Icons.Filled.Info)
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 22.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard("Offres", "${s.total}", Modifier.weight(1f))
                KpiCard("Score moyen", "${s.averageScore}", Modifier.weight(1f))
                KpiCard("Sauvegardées", "${s.saved}", Modifier.weight(1f))
            }

            CpCard(Modifier.padding(top = 13.dp)) {
                SectionTitle("Répartition des scores", Modifier.padding(top = 0.dp))
                ScoreHistogram(s.histogram)
            }

            CpCard(Modifier.padding(top = 13.dp)) {
                SectionTitle("Par type de contrat")
                val max = s.byType.maxOfOrNull { it.count } ?: 1
                s.byType.forEach { ScoreBar("${JobTypes.label(it.type)} · score moyen ${it.averageScore}", it.count, max) }
            }

            if (s.topMissingSkills.isNotEmpty()) {
                CpCard(Modifier.padding(top = 13.dp)) {
                    SectionTitle("Compétences à renforcer")
                    MutedText("Les compétences les plus demandées par vos offres et absentes de votre profil.")
                    val top = s.topMissingSkills.first().second
                    s.topMissingSkills.forEach { (skill, count) -> ScoreBar(skill, count, top, c.warningText) }
                }
            }
            if (s.hidden > 0) {
                MutedText("${s.hidden} offre(s) masquée(s) ne sont pas comptées.", Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    val c = CareerPilotTheme.colors
    CpCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(vertical = 13.dp, horizontal = 8.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = c.accent, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-1).sp)
            Text(label, color = c.muted, fontFamily = DmSans, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Histogramme dessiné avec Canvas : une barre par tranche de score, colorée comme les badges. */
@Composable
private fun ScoreHistogram(histogram: List<Pair<String, Int>>) {
    val max = (histogram.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val c = CareerPilotTheme.colors
    val track = c.track
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
                    Text("$count", color = c.title, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(range, color = c.muted, fontFamily = DmSans, fontSize = 10.sp)
                }
            }
        }
    }
}

