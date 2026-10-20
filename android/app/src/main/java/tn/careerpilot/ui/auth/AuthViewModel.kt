package tn.careerpilot.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tn.careerpilot.CareerPilotApp
import tn.careerpilot.data.AuthRepository
import tn.careerpilot.data.CurrentUser
import tn.careerpilot.data.toUserMessage

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: CurrentUser) : SessionState
}

data class FormState(val loading: Boolean = false, val error: String? = null)

class AuthViewModel(
    private val repository: AuthRepository,
    sessionExpired: SharedFlow<Unit>,
) : ViewModel() {

    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    init {
        // US-1.2.2 : refresh token refusé → retour à l'écran de connexion.
        viewModelScope.launch { sessionExpired.collect { _session.value = SessionState.LoggedOut } }
        // Au démarrage, on restaure la session si des jetons existent.
        viewModelScope.launch {
            _session.value = if (!repository.hasSession) {
                SessionState.LoggedOut
            } else {
                runCatching { SessionState.LoggedIn(repository.currentUser()) }.getOrElse { SessionState.LoggedOut }
            }
        }
    }

    fun login(email: String, password: String) = submit { repository.login(email.trim(), password) }

    fun register(email: String, password: String, language: String, consent: Boolean) =
        submit { repository.register(email.trim(), password, language, consent) }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _session.value = SessionState.LoggedOut
        }
    }

    fun clearError() {
        _form.value = _form.value.copy(error = null)
    }

    private fun submit(action: suspend () -> Unit) {
        if (_form.value.loading) return
        viewModelScope.launch {
            _form.value = FormState(loading = true)
            try {
                action()
                _session.value = SessionState.LoggedIn(repository.currentUser())
                _form.value = FormState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _form.value = FormState(error = e.toUserMessage())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CareerPilotApp
                AuthViewModel(app.container.authRepository, app.container.sessionExpired)
            }
        }
    }
}
