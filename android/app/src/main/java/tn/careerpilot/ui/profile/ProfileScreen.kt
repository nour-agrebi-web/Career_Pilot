package tn.careerpilot.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tn.careerpilot.data.repository.ProfileSection
import tn.careerpilot.domain.Completeness
import tn.careerpilot.domain.Education
import tn.careerpilot.domain.Experience
import tn.careerpilot.domain.Language
import tn.careerpilot.domain.LanguageLevels
import tn.careerpilot.domain.Levels
import tn.careerpilot.domain.Profile
import tn.careerpilot.domain.Skill
import tn.careerpilot.ui.common.CardDivider
import tn.careerpilot.ui.common.CpCard
import tn.careerpilot.ui.common.CpDialog
import tn.careerpilot.ui.common.CpTextField
import tn.careerpilot.ui.common.FieldLabel
import tn.careerpilot.ui.common.FilterPill
import tn.careerpilot.ui.common.LinkButton
import tn.careerpilot.ui.common.LoadingBar
import tn.careerpilot.ui.common.MutedText
import tn.careerpilot.ui.common.PrimaryButton
import tn.careerpilot.ui.common.ScoreBar
import tn.careerpilot.ui.common.ScreenHeader
import tn.careerpilot.ui.common.SkillTag
import tn.careerpilot.ui.common.SoftButton
import tn.careerpilot.ui.common.Tag
import tn.careerpilot.ui.common.TagStyle
import tn.careerpilot.ui.common.UiMessageEffect
import tn.careerpilot.ui.cv.CvSection
import tn.careerpilot.ui.cv.CvViewModel
import tn.careerpilot.ui.theme.CareerPilotTheme
import tn.careerpilot.ui.theme.DmSans

/** Élément en cours d'édition (null = ajout). */
private sealed interface Editing {
    data object Info : Editing
    data class SkillItem(val item: Skill?) : Editing
    data class ExperienceItem(val item: Experience?) : Editing
    data class EducationItem(val item: Education?) : Editing
    data class LanguageItem(val item: Language?) : Editing
}

/**
 * Onglet « Profil et CV » de la maquette : informations, carte « Mes CV », puis compétences et expériences.
 * La déconnexion est dans l'en-tête, comme sur la maquette.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, cvViewModel: CvViewModel, onLogout: () -> Unit) {
    val profile by viewModel.profile.observeAsState(Profile())
    val completeness by viewModel.completeness.observeAsState(Completeness(0, emptyList()))
    val state by viewModel.uiState.observeAsState()
    var editing by remember { mutableStateOf<Editing?>(null) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val c = CareerPilotTheme.colors
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Profil et CV", eyebrow = "Vos infos") {
            SoftButton("Déconnexion", onLogout, icon = Icons.AutoMirrored.Filled.ExitToApp)
        }
        if (state?.loading == true) LoadingBar(Modifier.padding(top = 8.dp))
        Column(Modifier.verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 22.dp)) {
            CompletenessCard(completeness, profile, onValidate = viewModel::validate)

            // .profile-card « Informations » : champs en lecture seule et bouton « Modifier ».
            CpCard(Modifier.padding(top = 15.dp)) {
                CardHeading("Informations", action = "Modifier") { editing = Editing.Info }
                ReadOnlyField("Domaine", profile.domain ?: "Non renseigné")
                ReadOnlyField("Niveau", Levels.label(profile.level))
            }

            CvSection(cvViewModel, onOpenProfile = { scope.launch { scroll.animateScrollTo(0) } }, modifier = Modifier.padding(top = 15.dp))

            // .profile-card « Compétences et expériences ».
            CpCard(Modifier.padding(top = 15.dp)) {
                CardHeading("Compétences (${profile.skills.size})", action = "Ajouter", small = true) { editing = Editing.SkillItem(null) }
                if (profile.skills.isEmpty()) {
                    MutedText("Aucune compétence pour l'instant.", Modifier.padding(top = 8.dp))
                } else {
                    FlowRow(
                        Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        profile.skills.forEach { skill -> SkillTag(skill.name, onClick = { editing = Editing.SkillItem(skill) }) }
                    }
                    MutedText("Touchez une compétence pour la modifier ou la supprimer.", Modifier.padding(top = 8.dp), size = 10)
                }

                CardDivider(Modifier.padding(top = 4.dp))
                CardHeading("Expériences", action = "Ajouter", small = true) { editing = Editing.ExperienceItem(null) }
                profile.experiences.forEach { exp ->
                    ItemRow(exp.position, "${exp.company} · ${period(exp.startDate, exp.endDate)}",
                        onEdit = { editing = Editing.ExperienceItem(exp) },
                        onDelete = { viewModel.delete(ProfileSection.EXPERIENCES, exp.id) })
                }

                CardDivider(Modifier.padding(top = 4.dp))
                CardHeading("Formations", action = "Ajouter", small = true) { editing = Editing.EducationItem(null) }
                profile.educations.forEach { edu ->
                    ItemRow(edu.degree, "${edu.institution} · ${period(edu.startDate, edu.endDate)}",
                        onEdit = { editing = Editing.EducationItem(edu) },
                        onDelete = { viewModel.delete(ProfileSection.EDUCATIONS, edu.id) })
                }

                CardDivider(Modifier.padding(top = 4.dp))
                CardHeading("Langues", action = "Ajouter", small = true) { editing = Editing.LanguageItem(null) }
                profile.languages.forEach { lang ->
                    ItemRow(lang.name, "Niveau ${lang.level}",
                        onEdit = { editing = Editing.LanguageItem(lang) },
                        onDelete = { viewModel.delete(ProfileSection.LANGUAGES, lang.id) })
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                LinkButton("Réinitialiser mon profil", viewModel::reset, color = c.error)
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

/** Avancement du profil, en tête de l'onglet. */
@Composable
private fun CompletenessCard(completeness: Completeness, profile: Profile, onValidate: () -> Unit) {
    val c = CareerPilotTheme.colors
    CpCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Profil complété à ${completeness.percent} %",
                color = c.title,
                fontFamily = DmSans,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            if (profile.validated) Tag("Validé", TagStyle.ACCENT) else Tag("À valider", TagStyle.WARNING)
        }
        ScoreBar(if (profile.validated) "Profil validé · référence du matching" else "Profil à valider", completeness.percent, 100)
        completeness.missing.take(3).forEach {
            MutedText("•  À ajouter : $it", Modifier.padding(top = 4.dp))
        }
        if (!profile.validated) {
            PrimaryButton("Valider mon profil", onValidate, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = profile.isExploitable)
            if (!profile.isExploitable) {
                MutedText("Il faut des compétences et une formation ou une expérience pour valider.", Modifier.padding(top = 6.dp), size = 10)
            }
        }
    }
}

/** Titre de carte avec bouton turquoise pâle à droite (.profile-heading + .small-btn). */
@Composable
private fun CardHeading(title: String, action: String, small: Boolean = false, onAction: () -> Unit) {
    val c = CareerPilotTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = c.title,
            fontFamily = DmSans,
            fontWeight = FontWeight.Bold,
            fontSize = if (small) 14.sp else 22.sp,
            letterSpacing = if (small) 0.sp else (-0.5).sp,
            modifier = Modifier.weight(1f),
        )
        SoftButton(action, onAction, icon = if (small) Icons.Filled.Add else null)
    }
}

/** Champ en lecture seule (.readonly) : libellé gras puis valeur. */
@Composable
private fun ReadOnlyField(label: String, value: String) {
    FieldLabel(label)
    Text(value, color = CareerPilotTheme.colors.text, fontFamily = DmSans, fontSize = 14.sp)
}

/** Une expérience, formation ou langue (.experience). */
@Composable
private fun ItemRow(title: String, subtitle: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = CareerPilotTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.title, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            MutedText(subtitle, Modifier.padding(top = 3.dp))
        }
        RoundIcon(Icons.Filled.Edit, "Modifier", onEdit)
        RoundIcon(Icons.Filled.Delete, "Supprimer", onDelete)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = CareerPilotTheme.colors.muted, modifier = Modifier.size(18.dp))
    }
}

// --- Boîtes de dialogue d'ajout et de modification ---

@Composable
private fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    extraAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    CpDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = "Enregistrer",
        onConfirm = onConfirm,
        extraAction = extraAction?.let { "Supprimer" to it },
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoDialog(profile: Profile, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var domain by remember { mutableStateOf(profile.domain.orEmpty()) }
    var level by remember { mutableStateOf(profile.level) }
    FormDialog("Domaine et niveau", onDismiss, onConfirm = { onSave(domain, level) }) {
        CpTextField(domain, { domain = it }, label = "Domaine", placeholder = "ex. Développement mobile")
        FieldLabel("Niveau")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Levels.all.forEach { (code, label) -> FilterPill(label, level == code) { level = code } }
        }
    }
}

@Composable
private fun SkillDialog(item: Skill?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    FormDialog(if (item == null) "Ajouter une compétence" else "Modifier la compétence", onDismiss, { onSave(name) }, onDelete) {
        CpTextField(name, { name = it }, label = "Compétence", placeholder = "ex. Kotlin")
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
        CpTextField(fields.first, { fields = fields.copy(first = it) }, label = firstLabel)
        CpTextField(fields.second, { fields = fields.copy(second = it) }, label = secondLabel)
        CpTextField(fields.start, { fields = fields.copy(start = it) }, label = "Début", placeholder = "AAAA-MM-JJ")
        CpTextField(fields.end, { fields = fields.copy(end = it) }, label = "Fin", placeholder = "Vide si en cours")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageDialog(item: Language?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var level by remember { mutableStateOf(item?.level ?: "B2") }
    FormDialog(if (item == null) "Ajouter une langue" else "Modifier la langue", onDismiss, { onSave(name, level) }) {
        CpTextField(name, { name = it }, label = "Langue", placeholder = "ex. Anglais")
        FieldLabel("Niveau")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LanguageLevels.all.forEach { code -> FilterPill(code, level == code) { level = code } }
        }
    }
}
