package tn.careerpilot.ui.common

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import tn.careerpilot.data.remote.ApiResult

/** État commun des écrans : chargement en cours et message ponctuel (succès ou erreur). */
data class UiState(val loading: Boolean = false, val message: UiMessage? = null)

data class UiMessage(val text: String, val isError: Boolean)

/**
 * Base des ViewModels métier : lance les appels dans le viewModelScope et publie l'état en LiveData.
 * La vue n'a qu'à observer [uiState] et appeler les fonctions publiques du ViewModel.
 */
abstract class BaseViewModel : ViewModel() {
    private val _uiState = MutableLiveData(UiState())
    val uiState: LiveData<UiState> = _uiState

    /** Appelé par la vue une fois le message affiché (Snackbar). */
    fun messageShown() {
        _uiState.value = _uiState.value?.copy(message = null)
    }

    protected fun showError(text: String) {
        _uiState.value = UiState(message = UiMessage(text, isError = true))
    }

    /** Exécute [block] ; affiche [success] s'il réussit, le message d'erreur de l'API sinon. */
    protected fun <T> launchRequest(success: String? = null, block: suspend () -> ApiResult<T>) =
        launchRequestWith({ success }, block)

    /** Comme [launchRequest], avec un message de succès calculé à partir du résultat. */
    protected fun <T> launchRequestWith(success: (T) -> String?, block: suspend () -> ApiResult<T>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(loading = true) ?: UiState(loading = true)
            val message = when (val result = block()) {
                is ApiResult.Success -> success(result.data)?.let { UiMessage(it, isError = false) }
                is ApiResult.Error -> UiMessage(result.message, isError = true)
            }
            _uiState.value = UiState(loading = false, message = message)
        }
    }
}
