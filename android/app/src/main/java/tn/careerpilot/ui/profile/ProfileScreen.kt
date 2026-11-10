package tn.careerpilot.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import tn.careerpilot.data.repository.ProfileSection
import tn.careerpilot.domain.Completeness
import tn.careerpilot.domain.Education
import tn.careerpilot.domain.Experience
import tn.careerpilot.domain.Language
import tn.careerpilot.domain.LanguageLevels
import tn.careerpilot.domain.Levels
import tn.careerpilot.domain.Profile
import tn.careerpilot.domain.Skill
import tn.careerpilot.ui.common.ScreenHeader
import tn.careerpilot.ui.common.SectionTitle
import tn.careerpilot.ui.common.Tag
import tn.careerpilot.ui.common.UiMessageEffect

/** Élément en cours d'édition (null = ajout). */
private sealed interface Editing {
    data object Info : Editing
    data class SkillItem(val item: Skill?) : Editing
    data class ExperienceItem(val item: Experience?) : Editing
    data class EducationItem(val item: Education?) : Editing
    data class LanguageItem(val item: Language?) : Editing
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val profile by viewModel.profile.observeAsState(Profile())
    val completeness by viewModel.completeness.observeAsState(Completeness(0, emptyList()))
    val state by viewModel.uiState.observeAsState()
    var editing by remember { mutableStateOf<Editing?>(null) }
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Mon profil", if (profile.validated) "Profil validé · référence du matching" else "Profil à valider")
        if (state?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            CompletenessCard(completeness, profile, onValidate = viewModel::validate)

            SectionHeader("Domaine et niveau") { editing = Editing.Info }
            Text(profile.domain ?: "Domaine non renseigné", style = MaterialTheme.typography.bodyLarge)
            Text("Niveau : ${Levels.label(profile.level)}", style = MaterialTheme.typography.bodyMedium)

            SectionHeader("Compétences (${profile.skills.size})") { editing = Editing.SkillItem(null) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                profile.skills.forEach { skill ->
                    InputChip(
                        selected = false,
                        onClick = { editing = Editing.SkillItem(skill) },
                        label = { Text(skill.name) },
                        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = "Modifier") },
                    )
                }
            }
            if (profile.skills.isNotEmpty()) {
                Text("Touchez une compétence pour la modifier ou la supprimer.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionHeader("Expériences") { editing = Editing.ExperienceItem(null) }
            profile.experiences.forEach { exp ->
                ItemRow(exp.position, "${exp.company} · ${period(exp.startDate, exp.endDate)}",
                    onEdit = { editing = Editing.ExperienceItem(exp) },
                    onDelete = { viewModel.delete(ProfileSection.EXPERIENCES, exp.id) })
            }

            SectionHeader("Formations") { editing = Editing.EducationItem(null) }
            profile.educations.forEach { edu ->
                ItemRow(edu.degree, "${edu.institution} · ${period(edu.startDate, edu.endDate)}",
                    onEdit = { editing = Editing.EducationItem(edu) },
                    onDelete = { viewModel.delete(ProfileSection.EDUCATIONS, edu.id) })
            }

            SectionHeader("Langues") { editing = Editing.LanguageItem(null) }
            profile.languages.forEach { lang ->
                ItemRow(lang.name, "Niveau ${lang.level}",
                    onEdit = { editing = Editing.LanguageItem(lang) },
                    onDelete = { viewModel.delete(ProfileSection.LANGUAGES, lang.id) })
            }
            TextButton(onClick = viewModel::reset, modifier = Modifier.padding(vertical = 16.dp)) {
                Text("Réinitialiser mon profil", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    when (val current = editing) {
        null -> Unit
        Editing.Info -> InfoDialog(profile, onDismiss = { editing = null }) { domain, level ->
            viewModel.updateInfo(domain, level)
            editing = null
        }
        is Editing.SkillItem -> SkillDialog(current.item, onDismiss = { editing = null },
            onDelete = current.item?.let { skill ->
                {
                    viewModel.delete(ProfileSection.SKILLS, skill.id)
                    editing = null
                }
            },
        ) { name ->
            viewModel.saveSkill(current.item?.id, name)
            editing = null
        }
        is Editing.ExperienceItem -> PeriodDialog(
            title = if (current.item == null) "Ajouter une expérience" else "Modifier l'expérience",
            firstLabel = "Poste", secondLabel = "Entreprise",
            initial = current.item?.let { PeriodFields(it.position, it.company, it.startDate, it.endDate.orEmpty()) },
            onDismiss = { editing = null },
        ) { f ->
            viewModel.saveExperience(Experience(current.item?.id.orEmpty(), f.first, f.second, f.start, f.end.ifBlank { null }))
            editing = null
        }
        is Editing.EducationItem -> PeriodDialog(
            title = if (current.item == null) "Ajouter une formation" else "Modifier la formation",
            firstLabel = "Diplôme", secondLabel = "Établissement",
            initial = current.item?.let { PeriodFields(it.degree, it.institution, it.startDate, it.endDate.orEmpty()) },
            onDismiss = { editing = null },
        ) { f ->
            viewModel.saveEducation(Education(current.item?.id.orEmpty(), f.first, f.second, f.start, f.end.ifBlank { null }))
            editing = null
        }
        is Editing.LanguageItem -> LanguageDialog(current.item, onDismiss = { editing = null }) { name, level ->
            viewModel.saveLanguage(Language(current.item?.id.orEmpty(), name, level))
            editing = null
        }
    }
}

private fun period(start: String, end: String?) = "${start.take(7)} → ${end?.take(7) ?: "aujourd'hui"}"

@Composable
private fun CompletenessCard(completeness: Completeness, profile: Profile, onValidate: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Profil complété à ${completeness.percent} %", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (profile.validated) Tag("Validé")
            }
            LinearProgressIndicator(progress = { completeness.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            completeness.missing.take(3).forEach {
                Text("• À ajouter : $it", style = MaterialTheme.typography.bodySmall)
            }
            if (!profile.validated) {
                Button(onClick = onValidate, enabled = profile.isExploitable, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Valider mon profil")
                }
                if (!profile.isExploitable) {
                    Text("Il faut des compétences et une formation ou une expérience pour valider.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(title, Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Ajouter") }
    }
}

@Composable
private fun ItemRow(title: String, subtitle: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Modifier") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
    }
}

// --- Boîtes de dialogue d'ajout et de modification ---

@Composable
private fun FormDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit, extraAction: (() -> Unit)? = null,
                       content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Enregistrer") } },
        dismissButton = {
            Row {
                if (extraAction != null) TextButton(onClick = extraAction) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoDialog(profile: Profile, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var domain by remember { mutableStateOf(profile.domain.orEmpty()) }
    var level by remember { mutableStateOf(profile.level) }
    FormDialog("Domaine et niveau", onDismiss, onConfirm = { onSave(domain, level) }) {
        OutlinedTextField(domain, { domain = it }, label = { Text("Domaine (ex. Développement mobile)") }, singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Levels.all.forEach { (code, label) ->
                FilterChip(selected = level == code, onClick = { level = code }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun SkillDialog(item: Skill?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    FormDialog(if (item == null) "Ajouter une compétence" else "Modifier la compétence", onDismiss, { onSave(name) }, onDelete) {
        OutlinedTextField(name, { name = it }, label = { Text("Compétence (ex. Kotlin)") }, singleLine = true)
    }
}

private data class PeriodFields(val first: String = "", val second: String = "", val start: String = "", val end: String = "")

@Composable
private fun PeriodDialog(
    title: String,
    firstLabel: String,
    secondLabel: String,
    initial: PeriodFields?,
    onDismiss: () -> Unit,
    onSave: (PeriodFields) -> Unit,
) {
    var fields by remember { mutableStateOf(initial ?: PeriodFields()) }
    FormDialog(title, onDismiss, { onSave(fields) }) {
        OutlinedTextField(fields.first, { fields = fields.copy(first = it) }, label = { Text(firstLabel) }, singleLine = true)
        OutlinedTextField(fields.second, { fields = fields.copy(second = it) }, label = { Text(secondLabel) }, singleLine = true)
        OutlinedTextField(fields.start, { fields = fields.copy(start = it) }, label = { Text("Début (AAAA-MM-JJ)") }, singleLine = true)
        OutlinedTextField(fields.end, { fields = fields.copy(end = it) }, label = { Text("Fin (vide si en cours)") }, singleLine = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageDialog(item: Language?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var level by remember { mutableStateOf(item?.level ?: "B2") }
    FormDialog(if (item == null) "Ajouter une langue" else "Modifier la langue", onDismiss, { onSave(name, level) }) {
        OutlinedTextField(name, { name = it }, label = { Text("Langue (ex. Anglais)") }, singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LanguageLevels.all.forEach { code ->
                FilterChip(selected = level == code, onClick = { level = code }, label = { Text(code) })
            }
        }
    }
}
