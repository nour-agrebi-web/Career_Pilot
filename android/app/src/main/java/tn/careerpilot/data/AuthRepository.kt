package tn.careerpilot.data

class AuthRepository(
    private val api: ApiService,
    private val tokens: TokenStore,
) {
    val hasSession: Boolean get() = tokens.refreshToken != null

    suspend fun register(email: String, password: String, language: String, consent: Boolean) {
        api.register(RegisterRequest(email, password, language, consent))
        login(email, password)
    }

    suspend fun login(email: String, password: String) {
        val pair = api.login(email, password)
        tokens.save(pair.accessToken, pair.refreshToken)
    }

    suspend fun currentUser(): CurrentUser = api.me()

    suspend fun logout() {
        tokens.refreshToken?.let { runCatching { api.logout(RefreshRequest(it)) } }
        tokens.clear()
    }
}
