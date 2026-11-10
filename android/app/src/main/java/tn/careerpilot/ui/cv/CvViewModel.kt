package tn.careerpilot.ui.cv

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import tn.careerpilot.data.remote.ApiResult
import tn.careerpilot.data.repository.CvRepository
import tn.careerpilot.data.repository.ProfileRepository
import tn.careerpilot.domain.Cv
import tn.careerpilot.domain.CvAnalysis
import tn.careerpilot.domain.CvStatus
import tn.careerpilot.ui.common.BaseViewModel

class CvViewModel(
    private val repository: CvRepository,
    private val profileRepository: ProfileRepository,
) : BaseViewModel() {

    val cvs: LiveData<List<Cv>> = repository.cvs.asLiveData()

    private val _analysis = MutableLiveData<CvAnalysis?>(null)

    /** Analyse du CV actif : en cours, profil déduit ou erreur. */
    val analysis: LiveData<CvAnalysis?> = _analysis

    private val _openProfile = MutableLiveData(false)

    /** Passe à true quand l'écran doit ouvrir le profil (profil appliqué ou saisie manuelle). */
    val openProfile: LiveData<Boolean> = _openProfile

    init {
        refresh()
    }

    fun refresh() = launchRequest {
        val result = repository.refresh()
        if (result is ApiResult.Success) loadActiveAnalysis()
        result
    }

    fun upload(uri: Uri) = launchRequestWith(::analysisMessage) {
        _analysis.value = null
        when (val uploaded = repository.upload(uri)) {
            is ApiResult.Success -> {
                // Affiche tout de suite « analyse en cours », puis suit l'analyse jusqu'au résultat.
                repository.activeCv()?.let { _analysis.value = runningAnalysis(it) }
                repository.awaitAnalysis(uploaded.data).also { if (it is ApiResult.Success) _analysis.value = it.data }
            }
            is ApiResult.Error -> uploaded
        }
    }

    fun reanalyze(cv: CvAnalysis) = launchRequestWith(::analysisMessage) {
        when (val result = repository.reanalyze(cv.cvId)) {
            is ApiResult.Success -> {
                _analysis.value = cv.copy(status = CvStatus.UPLOADED)
                repository.awaitAnalysis(cv.cvId).also { if (it is ApiResult.Success) _analysis.value = it.data }
            }
            is ApiResult.Error -> result
        }
    }

    /** « Utiliser ce profil » : copie le profil déduit dans le profil, puis ouvre l'écran Profil. */
    fun applyProfile(cv: CvAnalysis) = launchRequestWith({ result ->
        val missing = CvStatus.missingLabel(result.missingSections)
        when {
            missing != null -> "Profil importé : complétez les $missing, puis validez."
            result.incompleteCount > 0 -> "Profil importé : ${result.incompleteCount} élément(s) à compléter, puis validez."
            else -> "Profil importé : relisez-le puis validez-le."
        }
    }) {
        repository.applyProfile(cv.cvId).also {
            if (it is ApiResult.Success) {
                profileRepository.refresh()
                _analysis.value = cv.copy(applied = true)
                _openProfile.value = true
            }
        }
    }

    /** Saisie manuelle du profil, proposée quand l'analyse échoue. */
    fun enterProfileManually() {
        _openProfile.value = true
    }

    fun profileOpened() {
        _openProfile.value = false
    }

    fun activate(cv: Cv) = launchRequest("Version ${cv.version} activée") {
        repository.activate(cv.id).also { if (it is ApiResult.Success) loadActiveAnalysis() }
    }

    fun delete(cv: Cv) = launchRequest("CV supprimé") {
        repository.delete(cv.id).also { if (it is ApiResult.Success) loadActiveAnalysis() }
    }

    private suspend fun loadActiveAnalysis() {
        val active = repository.activeCv()
        if (active == null) {
            _analysis.value = null
            return
        }
        if (active.isAnalyzing) _analysis.value = runningAnalysis(active)
        val result = if (active.isAnalyzing) repository.awaitAnalysis(active.id) else repository.analysis(active.id)
        if (result is ApiResult.Success) _analysis.value = result.data
    }

    private fun runningAnalysis(cv: Cv) = CvAnalysis(
        cvId = cv.id, status = cv.status, errorCode = null, error = null, applied = false, engine = null,
        domain = null, skills = emptyList(), experienceCount = 0, educationCount = 0, languageCount = 0,
        missingSections = emptyList(), incompleteCount = 0,
    )

    private fun analysisMessage(analysis: CvAnalysis): String? = when (analysis.status) {
        CvStatus.PROCESSED -> "CV analysé : vérifiez le profil déduit."
        else -> null // l'erreur est affichée dans la carte d'analyse, avec les deux choix
    }
}
