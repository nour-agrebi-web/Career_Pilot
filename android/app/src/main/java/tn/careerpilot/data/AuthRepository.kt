package tn.careerpilot.data

import tn.careerpilot.data.local.UserDao
import tn.careerpilot.data.local.UserEntity

/**
 * Authentification uniquement (inscription, connexion, session). Les données métier passent par
 * des repositories distincts (profil, préférences, CV, correspondances).
 *
 * Session : les jetons JWT sont chiffrés dans des SharedPreferences ([KeystoreTokenStore]) et
 * l'utilisateur connecté est gardé dans Room ([UserEntity]) pour rouvrir l'application hors ligne.
 */
class AuthRepository(
    private val api: ApiService,
    private val tokens: TokenStore,
    private val userDao: UserDao,
    private val clearLocalData: suspend () -> Unit,
) {
    val hasSession: Boolean get() = tokens.refreshToken != null

    suspend fun register(email: String, password: String, language: String, consent: Boolean) {
        api.register(RegisterRequest(email, password, language, consent))
        login(email, password)
    }

    suspend fun login(email: String, password: String) {
        val pair = api.login(email, password)
        // Un autre compte a pu être utilisé sur ce téléphone : on repart d'un cache vide.
        clearLocalData()
        tokens.save(pair.accessToken, pair.refreshToken)
    }

    /** Utilisateur connecté, lu sur le serveur puis enregistré dans Room. */
    suspend fun currentUser(): CurrentUser {
        val user = api.me()
        userDao.upsert(UserEntity(user.id, user.email, user.language, user.role, System.currentTimeMillis()))
        return user
    }

    /** Dernier utilisateur connu (cache Room), utilisé quand le serveur est injoignable. */
    suspend fun cachedUser(): CurrentUser? =
        userDao.current()?.let { CurrentUser(it.id, it.email, it.language, it.role) }

    suspend fun logout() {
        tokens.refreshToken?.let { runCatching { api.logout(RefreshRequest(it)) } }
        tokens.clear()
        clearLocalData()
    }
}
