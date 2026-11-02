package tn.careerpilot.ui.cv

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import tn.careerpilot.data.repository.CvRepository
import android.net.Uri
import tn.careerpilot.domain.Cv
import tn.careerpilot.ui.common.BaseViewModel

class CvViewModel(private val repository: CvRepository) : BaseViewModel() {

    val cvs: LiveData<List<Cv>> = repository.cvs.asLiveData()

    init {
        refresh()
    }

    fun refresh() = launchRequest { repository.refresh() }

    fun upload(uri: Uri) = launchRequest("CV importé : il devient votre CV actif") { repository.upload(uri) }

    fun activate(cv: Cv) = launchRequest("Version ${cv.version} activée") { repository.activate(cv.id) }

    fun delete(cv: Cv) = launchRequest("CV supprimé") { repository.delete(cv.id) }
}
