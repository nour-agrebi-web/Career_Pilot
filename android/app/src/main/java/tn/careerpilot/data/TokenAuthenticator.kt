package tn.careerpilot.data

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** Ajoute "Authorization: Bearer <jeton d'accès>" à chaque requête. */
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.accessToken ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().withBearer(token))
    }
}

/**
 * US-1.2.2 : appelé par OkHttp sur une réponse 401. Obtient un nouveau JWT avec le refresh token
 * et rejoue la requête ; si le refresh échoue, vide la session et prévient l'application.
 */
class TokenAuthenticator(
    private val tokens: TokenStore,
    private val refreshApi: () -> ApiService,
    private val onSessionExpired: () -> Unit,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.startsWith("/auth/")) return null
        if (response.priorResponse != null) return null // déjà réessayé une fois

        synchronized(this) {
            val usedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = tokens.accessToken
            // Une autre requête a déjà rafraîchi le jeton pendant qu'on attendait.
            if (current != null && current != usedToken) return response.request.withBearer(current)

            val refreshToken = tokens.refreshToken ?: return expire()
            val result = runCatching { refreshApi().refresh(RefreshRequest(refreshToken)).execute() }.getOrNull()
            val pair = result?.takeIf { it.isSuccessful }?.body() ?: return expire()
            tokens.save(pair.accessToken, pair.refreshToken)
            return response.request.withBearer(pair.accessToken)
        }
    }

    private fun expire(): Request? {
        tokens.clear()
        onSessionExpired()
        return null
    }
}

private fun Request.withBearer(token: String): Request =
    newBuilder().header("Authorization", "Bearer $token").build()
