package tn.careerpilot.ui.cv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import tn.careerpilot.domain.Cv
import tn.careerpilot.domain.CvAnalysis
import tn.careerpilot.domain.CvStatus
import tn.careerpilot.ui.common.AccentOutlineButton
import tn.careerpilot.ui.common.BodyText
import tn.careerpilot.ui.common.CpCard
import tn.careerpilot.ui.common.CpDialog
import tn.careerpilot.ui.common.DashedBox
import tn.careerpilot.ui.common.LoadingBar
import tn.careerpilot.ui.common.MutedText
import tn.careerpilot.ui.common.PrimaryButton
import tn.careerpilot.ui.common.SecondaryButton
import tn.careerpilot.ui.common.SoftButton
import tn.careerpilot.ui.common.Tag
import tn.careerpilot.ui.common.TagStyle
import tn.careerpilot.ui.common.UiMessageEffect
import tn.careerpilot.ui.theme.CareerPilotTheme
import tn.careerpilot.ui.theme.DmSans
import java.util.Locale

private val CV_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

/**
 * Carte « Mes CV » de l'onglet Profil et CV (.cv-section de la maquette) :
 * import (PDF/DOCX, 5 Mo), suivi de l'analyse, profil déduit, versions, activation et suppression.
 */
@Composable
fun CvSection(viewModel: CvViewModel, onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    val cvs by viewModel.cvs.observeAsState(emptyList())
    val state by viewModel.uiState.observeAsState()
    val analysis by viewModel.analysis.observeAsState()
    val openProfile by viewModel.openProfile.observeAsState(false)
    var toDelete by remember { mutableStateOf<Cv?>(null) }
    val c = CareerPilotTheme.colors
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

    CpCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Mes CV",
                color = c.title,
                fontFamily = DmSans,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.weight(1f),
            )
            // .cv-icon
            Box(Modifier.size(36.dp).background(c.accentSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Create, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
        MutedText(
            "Importez vos CV au format PDF ou DOCX (5 Mo maximum). Un seul CV actif, 10 versions conservées.",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        AccentOutlineButton("Importer un CV", pickFile, icon = Icons.Filled.Add)
        if (state?.loading == true && analysis?.let { CvStatus.isRunning(it.status) } != true) {
            LoadingBar(Modifier.padding(top = 10.dp))
        }

        analysis?.let { current ->
            AnalysisCard(
                analysis = current,
                onApply = { viewModel.applyProfile(current) },
                onReimport = pickFile,
                onRetry = { viewModel.reanalyze(current) },
                onManualEntry = viewModel::enterProfileManually,
                onOpenProfile = onOpenProfile,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Text(
            "Mes CV",
            color = c.title,
            fontFamily = DmSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        if (cvs.isEmpty()) {
            DashedBox {
                MutedText("Aucun CV importé", Modifier.padding(bottom = 10.dp), size = 13)
                SoftButton("Importer un CV", pickFile)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cvs.forEach { cv -> CvItem(cv, onActivate = { viewModel.activate(cv) }, onDelete = { toDelete = cv }) }
            }
        }
    }

    toDelete?.let { cv ->
        CpDialog(
            title = "Supprimer ce CV ?",
            onDismiss = { toDelete = null },
            confirmText = "Supprimer",
            destructive = true,
            onConfirm = {
                viewModel.delete(cv)
                toDelete = null
            },
        ) {
            BodyText("La version ${cv.version} (${cv.filename}) sera supprimée définitivement.", size = 13)
        }
    }
}

/** Une version de CV (.cv-item) : nom du fichier, date, statut et actions. */
@Composable
private fun CvItem(cv: Cv, onActivate: () -> Unit, onDelete: () -> Unit) {
    val c = CareerPilotTheme.colors
    CpCard(padding = PaddingValues(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cv.filename,
                color = c.title,
                fontFamily = DmSans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (cv.active) Tag("Actif", TagStyle.ACCENT) else Tag("Archivé")
        }
        MutedText(
            "Version ${cv.version} · ${formatSize(cv.size)} · importé le ${cv.createdAt.take(10)} · ${statusLabel(cv.status)}",
            Modifier.padding(top = 4.dp, bottom = 9.dp),
            size = 11,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!cv.active) SecondaryButton("Activer", onActivate)
            SecondaryButton("Supprimer", onDelete, color = c.error)
        }
    }
}

@Composable
private fun AnalysisTitle(color: androidx.compose.ui.graphics.Color, text: String) {
    Text(text, color = color, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) String.format(Locale.FRANCE, "%.1f Mo", bytes / 1024.0 / 1024.0)
    else "${(bytes / 1024).coerceAtLeast(1)} Ko"

private fun statusLabel(status: String) = CvStatus.label(status)

/** Carte du CV actif : analyse en cours (US-2.1.1), profil déduit (US-2.2.1), erreur (US-2.2.2, US-2.2.3). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisCard(
    analysis: CvAnalysis,
    onApply: () -> Unit,
    onReimport: () -> Unit,
    onRetry: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CareerPilotTheme.colors
    val isError = analysis.status == CvStatus.ERROR
    CpCard(
        modifier,
        padding = PaddingValues(13.dp),
        background = if (isError) c.warningBackground else c.accentSoft,
        borderColor = if (isError) c.warningText.copy(alpha = 0.35f) else c.accent.copy(alpha = 0.35f),
    ) {
        val titleColor = if (isError) c.warningText else c.title
        when {
            CvStatus.isRunning(analysis.status) -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), color = c.accent, trackColor = c.track, strokeWidth = 3.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    AnalysisTitle(titleColor, "Analyse de votre CV en cours…")
                    MutedText("L'agent lit votre CV et en déduit votre profil (moins de 30 secondes).")
                }
            }

            isError -> {
                AnalysisTitle(titleColor, "Impossible d'analyser ce CV")
                BodyText(CvStatus.errorMessage(analysis.errorCode, analysis.error), Modifier.padding(top = 6.dp))
                MutedText("Vous pouvez importer un autre fichier ou saisir votre profil vous-même.", Modifier.padding(top = 4.dp))
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PrimaryButton("Réimporter", onReimport)
                    SecondaryButton("Saisir mon profil", onManualEntry)
                    if (analysis.errorCode != CvStatus.UNREADABLE && analysis.errorCode != CvStatus.NO_TEXT) {
                        SecondaryButton("Relancer l'analyse", onRetry)
                    }
                }
            }

            analysis.applied -> {
                AnalysisTitle(titleColor, "Profil importé depuis ce CV")
                MutedText("Relisez-le et validez-le pour démarrer la veille des offres.", Modifier.padding(top = 4.dp, bottom = 10.dp))
                SoftButton("Voir mon profil", onOpenProfile)
            }

            else -> {
                AnalysisTitle(titleColor, "Profil déduit de votre CV")
                analysis.domain?.let { BodyText("Domaine : $it", Modifier.padding(top = 6.dp)) }
                BodyText(
                    "${analysis.skills.size} compétences · ${analysis.experienceCount} expériences · " +
                        "${analysis.educationCount} formations · ${analysis.languageCount} langues",
                    Modifier.padding(top = 4.dp),
                )
                if (analysis.skills.isNotEmpty()) {
                    MutedText(analysis.skills.take(8).joinToString(" · "), Modifier.padding(top = 4.dp))
                }
                CvStatus.missingLabel(analysis.missingSections)?.let {
                    BodyText("Sections non trouvées : $it. Vous pourrez les compléter.", Modifier.padding(top = 4.dp), color = c.warningText)
                }
                if (analysis.incompleteCount > 0) {
                    MutedText("${analysis.incompleteCount} élément(s) incomplet(s) à compléter à la main.", Modifier.padding(top = 4.dp))
                }
                PrimaryButton("Utiliser ce profil", onApply, Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        }
    }
}
