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
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import tn.careerpilot.ui.common.EmptyState
import tn.careerpilot.ui.common.ScreenHeader
import tn.careerpilot.ui.common.Tag
import tn.careerpilot.ui.common.UiMessageEffect
import java.util.Locale

private val CV_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

/** Mes CV : import (PDF/DOCX, 5 Mo), versions, activation et suppression. */
@Composable
fun CvScreen(viewModel: CvViewModel) {
    val cvs by viewModel.cvs.observeAsState(emptyList())
    val state by viewModel.uiState.observeAsState()
    var toDelete by remember { mutableStateOf<Cv?>(null) }
    UiMessageEffect(state, viewModel::messageShown)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.upload(uri)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Mes CV", "Un seul CV actif · 10 versions conservées au maximum")
            if (state?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (cvs.isEmpty() && state?.loading != true) {
                EmptyState("Aucun CV", "Importez votre CV (PDF ou DOCX, 5 Mo maximum) : l'agent en déduira votre profil.")
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cvs, key = { it.id }) { cv -> CvCard(cv, onActivate = { viewModel.activate(cv) }, onDelete = { toDelete = cv }) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { picker.launch(CV_MIME_TYPES) },
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

private fun statusLabel(status: String) = when (status) {
    "UPLOADED" -> "importé"
    "PROCESSING" -> "analyse en cours"
    "PROCESSED" -> "analysé"
    "ERROR" -> "erreur d'analyse"
    else -> status.lowercase()
}
