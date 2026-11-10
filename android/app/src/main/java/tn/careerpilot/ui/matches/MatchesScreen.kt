package tn.careerpilot.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tn.careerpilot.domain.JobTypes
import tn.careerpilot.domain.Match
import tn.careerpilot.domain.MatchFilter
import tn.careerpilot.domain.MatchSort
import tn.careerpilot.domain.MatchStatus
import tn.careerpilot.domain.WorkModes
import tn.careerpilot.ui.common.CpCard
import tn.careerpilot.ui.common.EmptyState
import tn.careerpilot.ui.common.FilterPill
import tn.careerpilot.ui.common.IconBox
import tn.careerpilot.ui.common.LinkButton
import tn.careerpilot.ui.common.LoadingBar
import tn.careerpilot.ui.common.ScoreBadge
import tn.careerpilot.ui.common.ScreenHeader
import tn.careerpilot.ui.common.SearchField
import tn.careerpilot.ui.common.Tag
import tn.careerpilot.ui.common.TagStyle
import tn.careerpilot.ui.common.UiMessageEffect
import tn.careerpilot.ui.theme.CareerPilotTheme
import tn.careerpilot.ui.theme.DmSans

/** Fil des offres recommandées (maquette « Mes offres »). */
@Composable
fun FeedScreen(viewModel: MatchesViewModel, onOpen: (Match) -> Unit, onOpenStats: () -> Unit, onEditThreshold: () -> Unit) {
    val matches by viewModel.matches.observeAsState(emptyList())
    val filter by viewModel.filter.observeAsState(MatchFilter())
    val threshold by viewModel.threshold.observeAsState(70)
    val state by viewModel.uiState.observeAsState()
    val c = CareerPilotTheme.colors
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Mes offres", eyebrow = "Vos opportunités sélectionnées") {
            IconBox(Icons.Filled.Info, "Statistiques", onOpenStats)
            IconBox(Icons.Filled.Refresh, "Actualiser", viewModel::refresh)
        }
        if (state?.loading == true) LoadingBar(Modifier.padding(top = 8.dp))

        SearchField(
            value = filter.query,
            onValueChange = viewModel::setQuery,
            placeholder = "Rechercher une offre ou une compétence",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item { FilterPill("Tous", filter.type == null) { viewModel.setType(null) } }
            items(JobTypes.all) { (code, label) -> FilterPill(label, filter.type == code) { viewModel.setType(code) } }
        }
        // .sort-line : « Classées par score · seuil 70 » à gauche, liens à droite.
        Row(Modifier.fillMaxWidth().padding(start = 17.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${matches.size} offre(s) · classées par ${if (filter.sort == MatchSort.SCORE) "score" else "date"} · seuil $threshold",
                color = c.muted,
                fontFamily = DmSans,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            LinkButton(if (filter.sort == MatchSort.SCORE) "Par date" else "Par score", {
                viewModel.setSort(if (filter.sort == MatchSort.SCORE) MatchSort.DATE else MatchSort.SCORE)
            }, color = c.muted)
            LinkButton("Modifier le seuil", onEditThreshold)
        }

        if (matches.isEmpty() && state?.loading != true) {
            EmptyState(
                "Aucune offre au-dessus de $threshold",
                "Validez votre profil, élargissez vos filtres ou baissez votre seuil de pertinence pour voir plus d'offres.",
                icon = Icons.Filled.Search,
            )
        }
        MatchList(matches, onOpen, viewModel::toggleSaved)
    }
}

/** Offres sauvegardées (maquette « Sauvegardées »). */
@Composable
fun SavedScreen(viewModel: MatchesViewModel, onOpen: (Match) -> Unit) {
    val saved by viewModel.saved.observeAsState(emptyList())
    val state by viewModel.uiState.observeAsState()
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Sauvegardées", eyebrow = "À revoir plus tard")
        if (saved.isEmpty()) {
            EmptyState(
                "Aucune offre sauvegardée",
                "Enregistrez les offres qui vous intéressent pour les retrouver facilement ici.",
                icon = Icons.Filled.FavoriteBorder,
            )
        } else {
            Text(
                "${saved.size} offre(s) gardée(s) pour plus tard",
                color = CareerPilotTheme.colors.muted,
                fontFamily = DmSans,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 17.dp, top = 12.dp),
            )
        }
        MatchList(saved, onOpen, viewModel::toggleSaved)
    }
}

@Composable
private fun MatchList(matches: List<Match>, onOpen: (Match) -> Unit, onToggleSaved: (Match) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(matches, key = { it.id }) { match -> MatchCard(match, { onOpen(match) }, { onToggleSaved(match) }) }
    }
}

/** Carte d'offre (.offer-card) : titre, entreprise, score cerclé, correspondances, étiquettes et pied de carte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchCard(match: Match, onClick: () -> Unit, onToggleSaved: () -> Unit) {
    val offer = match.offer
    val c = CareerPilotTheme.colors
    CpCard(onClick = onClick, padding = PaddingValues(start = 13.dp, end = 13.dp, top = 13.dp, bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(end = 52.dp)) {
                Text(
                    offer.title,
                    color = c.title,
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(offer.company, offer.city, WorkModes.label(offer.workMode).ifEmpty { null }).joinToString(" · "),
                    color = c.muted,
                    fontFamily = DmSans,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) { ScoreBadge(match.score) }
        }
        match.reasons.firstOrNull()?.let {
            Text(
                "Correspond à : $it",
                color = c.body,
                fontFamily = DmSans,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 11.dp),
            )
        }
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (match.status == MatchStatus.PENDING) Tag("Nouveau", TagStyle.ACCENT)
            match.missingSkills.take(2).forEach { Tag("Manque : $it", TagStyle.WARNING) }
            Tag(JobTypes.label(offer.type))
        }
        // .card-foot : source à gauche, action à droite, séparés du haut par un filet.
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(c.divider))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("via ${offer.source}", color = c.muted, fontFamily = DmSans, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onToggleSaved),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (match.isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (match.isSaved) "Retirer des sauvegardes" else "Sauvegarder",
                    tint = if (match.isSaved) c.accent else c.body,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
