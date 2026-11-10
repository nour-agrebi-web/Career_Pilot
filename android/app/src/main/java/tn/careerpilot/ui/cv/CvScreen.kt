package tn.careerpilot.ui.cv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tn.careerpilot.domain.Cv
import tn.careerpilot.domain.CvAnalysis
import tn.careerpilot.domain.CvStatus
import tn.careerpilot.ui.common.EmptyState
import tn.careerpilot.ui.common.ScreenHeader
import tn.careerpilot.ui.common.Tag
import tn.careerpilot.ui.common.UiMessageEffect
import java.util.Locale

private val CV_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

/** Mes CV : import (PDF/DOCX, 5 Mo), suivi de l'analyse, profil déduit, versions, activation et suppression. */
@Composable
fun CvScreen(viewModel: CvViewModel, onOpenProfile: () -> Unit) {
    val cvs by viewModel.cvs.observeAsState(emptyList())
    val state by viewModel.uiState.observeAsState()
    val analysis by viewModel.analysis.observeAsState()
    val openProfile by viewModel.openProfile.observeAsState(false)
    var toDelete by remember { mutableStateOf<Cv?>(null) }
    UiMessageEffect(state, viewModel::messageShown)

    LaunchedEffect(openProfile) {
        if (openProfile) {
            viewModel.profileOpened()
            onOpenProfile()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.upload(uri)
    }
    val pickFile = { picker.launch(CV_MIME_TYPES) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Mes CV", "Un seul CV actif · 10 versions conservées au maximum")
            if (state?.loading == true && analysis?.let { CvStatus.isRunning(it.status) } != true) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (cvs.isEmpty() && state?.loading != true) {
                EmptyState("Aucun CV", "Importez votre CV (PDF ou DOCX, 5 Mo maximum) : l'agent en déduira votre profil.")
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                analysis?.let { current ->
                    item(key = "analysis") {
                        AnalysisCard(
                            analysis = current,
                            onApply = { viewModel.applyProfile(current) },
                            onReimport = pickFile,
                            onRetry = { viewModel.reanalyze(current) },
                            onManualEntry = viewModel::enterProfileManually,
                            onOpenProfile = onOpenProfile,
                        )
                    }
                }
                items(cvs, key = { it.id }) { cv -> CvCard(cv, onActivate = { viewModel.activate(cv) }, onDelete = { toDelete = cv }) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = pickFile,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Importer un CV") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    toDelete?.let { cv ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer ce CV ?") },
            text = { Text("La version ${cv.version} (${cv.filename}) sera supprimée définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(cv)
                    toDelete = null
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun CvCard(cv: Cv, onActivate: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Version ${cv.version}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (cv.active) Tag("Actif") else Tag("Archivé", MaterialTheme.colorScheme.surfaceVariant)
                }
                Text(cv.filename, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${formatSize(cv.size)} · importé le ${cv.createdAt.take(10)} · ${statusLabel(cv.status)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!cv.active) TextButton(onClick = onActivate) { Text("Activer") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
        }
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) String.format(Locale.FRANCE, "%.1f Mo", bytes / 1024.0 / 1024.0)
    else "${(bytes / 1024).coerceAtLeast(1)} Ko"

private fun statusLabel(status: String) = CvStatus.label(status)

/** Carte du CV actif : analyse en cours (US-2.1.1), profil déduit (US-2.2.1), erreur (US-2.2.2, US-2.2.3). */
@Composable
private fun AnalysisCard(
    analysis: CvAnalysis,
    onApply: () -> Unit,
    onReimport: () -> Unit,
    onRetry: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val colors = when (analysis.status) {
        CvStatus.ERROR -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        else -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                CvStatus.isRunning(analysis.status) -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Column {
                        Text("Analyse de votre CV en cours…", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("L'agent lit votre CV et en déduit votre profil (moins de 30 secondes).", style = MaterialTheme.typography.bodySmall)
                    }
                }

                analysis.status == CvStatus.ERROR -> {
                    Text("Impossible d'analyser ce CV", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(CvStatus.errorMessage(analysis.errorCode, analysis.error), style = MaterialTheme.typography.bodyMedium)
                    Text("Vous pouvez importer un autre fichier ou saisir votre profil vous-même.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onReimport) { Text("Réimporter") }
                        OutlinedButton(onClick = onManualEntry) { Text("Saisir mon profil") }
                    }
                    if (analysis.errorCode != CvStatus.UNREADABLE && analysis.errorCode != CvStatus.NO_TEXT) {
                        TextButton(onClick = onRetry) { Text("Relancer l'analyse") }
                    }
                }

                analysis.applied -> {
                    Text("Profil importé depuis ce CV", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Relisez-le et validez-le pour démarrer la veille des offres.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onOpenProfile) { Text("Voir mon profil") }
                }

                else -> {
                    Text("Profil déduit de votre CV", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    analysis.domain?.let { Text("Domaine : $it", style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        "${analysis.skills.size} compétences · ${analysis.experienceCount} expériences · " +
                            "${analysis.educationCount} formations · ${analysis.languageCount} langues",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (analysis.skills.isNotEmpty()) {
                        Text(analysis.skills.take(8).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    CvStatus.missingLabel(analysis.missingSections)?.let {
                        Text("Sections non trouvées : $it. Vous pourrez les compléter.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (analysis.incompleteCount > 0) {
                        Text("${analysis.incompleteCount} élément(s) incomplet(s) à compléter à la main.", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onApply) { Text("Utiliser ce profil") }
                }
            }
        }
    }
}
