package tn.careerpilot.ui.auth

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import tn.careerpilot.data.AuthRepository
import tn.careerpilot.data.BASE_URL
import tn.careerpilot.data.CurrentUser
import tn.careerpilot.data.toUserMessage
import java.io.IOException

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: CurrentUser, val offline: Boolean = false) : SessionState
}

/**
 * État du formulaire. [lockedUntil] (horloge SystemClock.elapsedRealtime) est renseigné quand le
 * serveur bloque les connexions après trop d'échecs (réponse 429 + en-tête Retry-After).
 */
data class FormState(val loading: Boolean = false, val error: String? = null, val lockedUntil: Long? = null) {
    fun lockSecondsLeft(): Int {
        val until = lockedUntil ?: return 0
        return ((until - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
    }
}

class AuthViewModel(
    private val repository: AuthRepository,
    sessionExpired: SharedFlow<Unit>,
) : ViewModel() {

    private val _session = MutableLiveData<SessionState>(SessionState.Loading)
    val session: LiveData<SessionState> = _session

    /** Utilisateur connecté, ou null : la MainActivity l'observe pour choisir les écrans à afficher. */
    val authenticatedUser: LiveData<CurrentUser?> = _session.map { (it as? SessionState.LoggedIn)?.user }

    /** Vrai pendant la restauration de la session au démarrage (écran d'attente). */
    val isRestoringSession: LiveData<Boolean> = _session.map { it == SessionState.Loading }

    private val _form = MutableLiveData(FormState())
    val form: LiveData<FormState> = _form

    init {
        // US-1.2.2 : refresh token refusé → retour à l'écran de connexion.
        viewModelScope.launch { sessionExpired.collect { _session.value = SessionState.LoggedOut } }
        viewModelScope.launch { _session.value = restoreSession() }
    }

    /** Au démarrage : jetons présents → utilisateur relu sur le serveur, ou depuis Room si hors ligne. */
    private suspend fun restoreSession(): SessionState {
        if (!repository.hasSession) return SessionState.LoggedOut
        return try {
            SessionState.LoggedIn(repository.currentUser())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            repository.cachedUser()?.let { SessionState.LoggedIn(it, offline = true) } ?: SessionState.LoggedOut
        } catch (e: Exception) {
            SessionState.LoggedOut
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
        _form.value = _form.value?.copy(error = null)
    }

    private fun submit(action: suspend () -> Unit) {
        val form = _form.value ?: FormState()
        if (form.loading || form.lockSecondsLeft() > 0) return
        viewModelScope.launch {
            _form.value = FormState(loading = true)
            try {
                action()
                _session.value = SessionState.LoggedIn(repository.currentUser())
                _form.value = FormState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CareerPilot", "Appel API échoué (${BASE_URL})", e) // visible dans Logcat
                val retryAfter = (e as? HttpException)?.takeIf { it.code() == 429 }
                    ?.response()?.headers()?.get("Retry-After")?.toLongOrNull()
                _form.value = if (retryAfter != null) {
                    FormState(lockedUntil = SystemClock.elapsedRealtime() + retryAfter * 1000)
                } else {
                    FormState(error = e.toUserMessage(BASE_URL))
                }
            }
        }
    }
}
