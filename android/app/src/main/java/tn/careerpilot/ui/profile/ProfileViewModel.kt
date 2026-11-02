package tn.careerpilot.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.map
import tn.careerpilot.data.remote.ApiResult
import tn.careerpilot.data.repository.ProfileRepository
import tn.careerpilot.data.repository.ProfileSection
import tn.careerpilot.domain.Completeness
import tn.careerpilot.domain.Education
import tn.careerpilot.domain.Experience
import tn.careerpilot.domain.Language
import tn.careerpilot.domain.Profile
import tn.careerpilot.domain.ProfileCompleteness
import tn.careerpilot.domain.Validators
import tn.careerpilot.ui.common.BaseViewModel

class ProfileViewModel(private val repository: ProfileRepository) : BaseViewModel() {

    val profile: LiveData<Profile> = repository.profile.asLiveData()

    /** Taux de complétude recalculé à chaque modification du profil. */
    val completeness: LiveData<Completeness> = repository.profile.map { ProfileCompleteness.compute(it) }.asLiveData()

    init {
        refresh()
    }

    fun refresh() = launchRequest { repository.refresh() }

    fun updateInfo(domain: String, level: String?) =
        launchRequest("Profil mis à jour") { repository.updateInfo(domain, level) }

    fun validate() {
        val current = profile.value ?: Profile()
        if (!current.isExploitable) {
            showError("Ajoutez des compétences et une formation ou une expérience avant de valider.")
            return
        }
        launchRequest("Profil validé : la veille peut démarrer") { repository.validate() }
    }

    fun saveSkill(id: String?, name: String) {
        Validators.skill(name, profile.value?.skills.orEmpty(), ignoreId = id)?.let { return showError(it) }
        launchRequest(if (id == null) "Compétence ajoutée" else "Compétence modifiée") {
            if (id == null) repository.addSkill(name) else repository.updateSkill(id, name)
        }
    }

    fun saveExperience(item: Experience) {
        if (item.position.isBlank() || item.company.isBlank()) return showError("Indiquez le poste et l'entreprise.")
        Validators.period(item.startDate, item.endDate)?.let { return showError(it) }
        launchRequest("Expérience enregistrée") { repository.saveExperience(item) }
    }

    fun saveEducation(item: Education) {
        if (item.degree.isBlank() || item.institution.isBlank()) return showError("Indiquez le diplôme et l'établissement.")
        Validators.period(item.startDate, item.endDate)?.let { return showError(it) }
        launchRequest("Formation enregistrée") { repository.saveEducation(item) }
    }

    fun saveLanguage(item: Language) {
        if (item.name.isBlank()) return showError("Indiquez la langue.")
        launchRequest("Langue enregistrée") { repository.saveLanguage(item) }
    }

    fun delete(section: ProfileSection, id: String) = launchRequest("Élément supprimé") { repository.deleteItem(section, id) }

    fun reset() = launchRequest<Unit>("Profil réinitialisé") { repository.reset() }
}
