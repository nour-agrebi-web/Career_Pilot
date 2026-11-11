package tn.careerpilot.ui.matches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tn.careerpilot.domain.JobTypes
import tn.careerpilot.domain.Levels
import tn.careerpilot.domain.MatchStatus
import tn.careerpilot.domain.WorkModes
import tn.careerpilot.ui.common.BackHeader
import tn.careerpilot.ui.common.BodyText
import tn.careerpilot.ui.common.CpCard
import tn.careerpilot.ui.common.EmptyState
import tn.careerpilot.ui.common.MutedText
import tn.careerpilot.ui.common.PrimaryButton
import tn.careerpilot.ui.common.ScoreBar
import tn.careerpilot.ui.common.SecondaryButton
import tn.careerpilot.ui.common.SectionTitle
import tn.careerpilot.ui.common.SkillTag
import tn.careerpilot.ui.common.Tag
import tn.careerpilot.ui.common.UiMessageEffect
import tn.careerpilot.ui.theme.CareerPilotTheme
import tn.careerpilot.ui.theme.DmSans

/** Détail d'une offre (maquette « Détail de l'offre ») : score expliqué, raisons, compétences manquantes et actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchDetailScreen(viewModel: MatchDetailViewModel, onBack: () -> Unit) {
    val match by viewModel.match.observeAsState()
    val state by viewModel.uiState.observeAsState()
    val uriHandler = LocalUriHandler.current
    val c = CareerPilotTheme.colors
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        BackHeader("Détail de l'offre", onBack, Icons.AutoMirrored.Filled.ArrowBack)
        val current = match
        if (current == null) {
            EmptyState("Offre introuvable", "Elle a peut-être expiré ou été masquée.", icon = Icons.Filled.Search)
            return@Column
        }
        if (current.status == MatchStatus.REJECTED) {
            LaunchedBack(onBack)
        }
        val offer = current.offer
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 22.dp)) {
            Text(
                offer.title,
                color = c.title,
                fontFamily = DmSans,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                lineHeight = 25.sp,
                letterSpacing = (-0.35).sp,
            )
            MutedText(
                listOfNotNull(offer.company, offer.city, offer.country).joinToString(" · "),
                Modifier.padding(top = 6.dp, bottom = 9.dp),
                size = 12,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Tag(JobTypes.label(offer.type))
                offer.workMode?.let { Tag(WorkModes.label(it)) }
                offer.level?.let { Tag("Niveau : ${Levels.label(it)}") }
                offer.publicationDate?.let { Tag("Publiée le ${it.take(10)}") }
            }
            MutedText("Source : ${offer.source}", Modifier.padding(top = 9.dp), size = 10)

            // .detail-card : grand score turquoise puis le détail en barres.
            CpCard(Modifier.padding(top = 13.dp), padding = PaddingValues(horizontal = 14.dp, vertical = 15.dp)) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        "${current.score}",
                        color = c.accent,
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        letterSpacing = (-1).sp,
                    )
                    MutedText("/ 100 · compatibilité", Modifier.padding(start = 6.dp, bottom = 6.dp), size = 12)
                }
                ScoreBar("Similarité sémantique", current.breakdown.semantic, 40)
                ScoreBar("Compétences clés", current.breakdown.skills, 30)
                ScoreBar("Niveau et expérience", current.breakdown.level, 15)
                ScoreBar("Préférences", current.breakdown.preferences, 15)
            }

            if (current.reasons.isNotEmpty()) {
                SectionTitle("Pourquoi cette offre")
                current.reasons.forEach { BodyText("•  $it", Modifier.padding(start = 4.dp, bottom = 2.dp)) }
            }
            if (current.missingSkills.isNotEmpty()) {
                SectionTitle("Compétences manquantes")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    current.missingSkills.forEach { SkillTag(it, warning = true) }
                }
                MutedText("Signalées à titre indicatif, jamais ajoutées à votre profil.", Modifier.padding(top = 10.dp), size = 10)
            }
            if (offer.description.isNotBlank()) {
                SectionTitle("Description")
                BodyText(offer.description)
            }

            // .action-grid : deux boutons bordés côte à côte, puis « Postuler » en pleine largeur.
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SecondaryButton(if (current.isSaved) "Retirer" else "Sauvegarder", viewModel::toggleSaved, Modifier.weight(1f))
                SecondaryButton("Pas pour moi", viewModel::hide, Modifier.weight(1f))
            }
            offer.url?.let { url ->
                PrimaryButton("Postuler sur ${offer.source}  ↗", { uriHandler.openUri(url) }, Modifier.fillMaxWidth().padding(top = 7.dp))
            }
        }
    }
}

/** Une offre masquée quitte l'écran de détail. */
@Composable
private fun LaunchedBack(onBack: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
}
