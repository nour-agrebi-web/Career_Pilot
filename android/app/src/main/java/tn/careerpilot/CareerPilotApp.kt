package tn.careerpilot

import android.app.Application
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import tn.careerpilot.data.AuthRepository
import tn.careerpilot.data.KeystoreTokenStore
import tn.careerpilot.data.Network

/** Point d'entrée de l'application : crée une seule fois les objets partagés (injection manuelle). */
class CareerPilotApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Émis quand le refresh token est refusé : l'utilisateur doit se reconnecter. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    private val tokens = KeystoreTokenStore(app)
    private val api = Network.createApi(BuildConfig.API_URL, tokens) { _sessionExpired.tryEmit(Unit) }

    val authRepository = AuthRepository(api, tokens)
}
