package tn.careerpilot

import android.app.Application
import androidx.room.withTransaction
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import tn.careerpilot.data.AuthRepository
import tn.careerpilot.data.BASE_URL
import tn.careerpilot.data.KeystoreTokenStore
import tn.careerpilot.data.RetrofitInstance
import tn.careerpilot.data.local.AppDatabase
import tn.careerpilot.data.remote.CareerPilotApi
import tn.careerpilot.data.repository.CvRepository
import tn.careerpilot.data.repository.MatchRepository
import tn.careerpilot.data.repository.PreferencesRepository
import tn.careerpilot.data.repository.ProfileRepository
import tn.careerpilot.data.repository.UriFileReader

/** Point d'entrée de l'application : crée une seule fois les objets partagés (injection manuelle). */
class CareerPilotApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Conteneur d'injection : une instance de chaque repository, fournie aux ViewModels par la factory. */
class AppContainer(app: Application) {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Émis quand le refresh token est refusé : l'utilisateur doit se reconnecter. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    private val tokens = KeystoreTokenStore(app)
    private val retrofit = RetrofitInstance.createRetrofit(BASE_URL, tokens) { _sessionExpired.tryEmit(Unit) }
    private val database = AppDatabase.create(app)
    private val careerApi: CareerPilotApi = retrofit.create(CareerPilotApi::class.java)

    val authRepository = AuthRepository(
        api = RetrofitInstance.authApi(retrofit),
        tokens = tokens,
        userDao = database.userDao(),
        clearLocalData = { database.withTransaction { clearCaches() } },
    )
    val profileRepository = ProfileRepository(careerApi, database.profileDao())
    val preferencesRepository = PreferencesRepository(careerApi, database.preferencesDao())
    val cvRepository = CvRepository(careerApi, database.cvDao(), UriFileReader(app.contentResolver))
    val matchRepository = MatchRepository(careerApi, database.matchDao())

    private suspend fun clearCaches() {
        database.userDao().clear()
        database.profileDao().clearAll()
        database.cvDao().clear()
        database.matchDao().clear()
        database.preferencesDao().clear()
    }
}
