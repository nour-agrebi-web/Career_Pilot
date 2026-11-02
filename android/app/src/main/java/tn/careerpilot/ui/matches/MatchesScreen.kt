package tn.careerpilot.ui.matches

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tn.careerpilot.domain.JobTypes
import tn.careerpilot.domain.Match
import tn.careerpilot.domain.MatchFilter
import tn.careerpilot.domain.MatchSort
import tn.careerpilot.domain.MatchStatus
import tn.careerpilot.domain.WorkModes
import tn.careerpilot.ui.common.EmptyState
import tn.careerpilot.ui.common.ScoreBadge
import tn.careerpilot.ui.common.ScreenHeader
import tn.careerpilot.ui.common.Tag
import tn.careerpilot.ui.common.UiMessageEffect

/** Fil des offres recommandées (maquette « Mes offres »). */
@Composable
fun FeedScreen(viewModel: MatchesViewModel, onOpen: (Match) -> Unit, onOpenStats: () -> Unit, onEditThreshold: () -> Unit) {
    val matches by viewModel.matches.observeAsState(emptyList())
    val filter by viewModel.filter.observeAsState(MatchFilter())
    val threshold by viewModel.threshold.observeAsState(70)
    val state by viewModel.uiState.observeAsState()
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Mes offres", "Classées par ${if (filter.sort == MatchSort.SCORE) "score" else "date"} · seuil $threshold") {
            IconButton(onClick = onOpenStats) { Icon(Icons.Filled.Info, contentDescription = "Statistiques") }
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Actualiser") }
        }
        if (state?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth())

        OutlinedTextField(
            value = filter.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Titre, entreprise, ville ou compétence") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(selected = filter.type == null, onClick = { viewModel.setType(null) }, label = { Text("Tous") }) }
            items(JobTypes.all) { (code, label) ->
                FilterChip(selected = filter.type == code, onClick = { viewModel.setType(code) }, label = { Text(label) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${matches.size} offre(s)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.setSort(if (filter.sort == MatchSort.SCORE) MatchSort.DATE else MatchSort.SCORE) }) {
                Text(if (filter.sort == MatchSort.SCORE) "Trier par date" else "Trier par score")
            }
            TextButton(onClick = onEditThreshold) { Text("Modifier le seuil") }
        }

        if (matches.isEmpty() && state?.loading != true) {
            EmptyState(
                "Aucune offre au-dessus de $threshold",
                "Validez votre profil, élargissez vos filtres ou baissez votre seuil de pertinence pour voir plus d'offres.",
            )
        }
        MatchList(matches, onOpen, viewModel::toggleSaved)
    }
}

/** Offres sauvegardées. */
@Composable
fun SavedScreen(viewModel: MatchesViewModel, onOpen: (Match) -> Unit) {
    val saved by viewModel.saved.observeAsState(emptyList())
    val state by viewModel.uiState.observeAsState()
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Sauvegardées", "${saved.size} offre(s) gardée(s) pour plus tard")
        if (saved.isEmpty()) {
            EmptyState("Rien pour l'instant", "Touchez le cœur d'une offre pour la retrouver ici.")
        }
        MatchList(saved, onOpen, viewModel::toggleSaved)
    }
}

@Composable
private fun MatchList(matches: List<Match>, onOpen: (Match) -> Unit, onToggleSaved: (Match) -> Unit) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(matches, key = { it.id }) { match -> MatchCard(match, { onOpen(match) }, { onToggleSaved(match) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchCard(match: Match, onClick: () -> Unit, onToggleSaved: () -> Unit) {
    val offer = match.offer
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (match.status == MatchStatus.PENDING) {
                            Tag("Nouveau")
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(offer.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        listOfNotNull(offer.company, offer.city, WorkModes.label(offer.workMode).ifEmpty { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScoreBadge(match.score)
            }
            match.reasons.firstOrNull()?.let {
                Text("Correspond à : $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tag(JobTypes.label(offer.type))
                match.missingSkills.take(2).forEach { Tag("Manque : $it", MaterialTheme.colorScheme.errorContainer) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("via ${offer.source}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleSaved) {
                    Icon(
                        if (match.isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (match.isSaved) "Retirer des sauvegardes" else "Sauvegarder",
                    )
                }
            }
        }
    }
}
