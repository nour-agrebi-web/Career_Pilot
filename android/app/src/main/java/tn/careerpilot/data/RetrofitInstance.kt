package tn.careerpilot.data

import android.os.Build
import okhttp3.OkHttpClient
import tn.careerpilot.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Adresse de l'API FastAPI locale (python -m app), choisie au lancement de l'app :
// - sur l'ÉMULATEUR : http://10.0.2.2:8000/ (10.0.2.2 = le PC hôte), rien à configurer ;
// - sur un VRAI TÉLÉPHONE : l'IPv4 du PC (ipconfig), à mettre dans android/local.properties :
//       careerpilot.apiHost=192.168.1.24
//   puis Build > Rebuild Project et relancer l'app. Le PC et le téléphone doivent être sur le même Wi-Fi.
// - avec `adb reverse tcp:8000 tcp:8000` (USB) : careerpilot.apiHost=127.0.0.1
const val API_PORT = BuildConfig.API_PORT
const val EMULATOR_HOST = "10.0.2.2"

val BASE_URL: String = "http://${apiHost()}:$API_PORT/"

private fun apiHost(): String = when {
    isEmulator() -> EMULATOR_HOST
    BuildConfig.API_DEVICE_HOST.isNotBlank() -> BuildConfig.API_DEVICE_HOST
    else -> EMULATOR_HOST // non configuré : le message d'erreur affichera 10.0.2.2
}

/** Détection classique de l'émulateur Android Studio (images « goldfish » / « ranchu »). */
private fun isEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.orEmpty()
    val hardware = Build.HARDWARE.orEmpty()
    val product = Build.PRODUCT.orEmpty()
    return fingerprint.startsWith("generic") || fingerprint.contains("emulator") ||
        hardware == "goldfish" || hardware == "ranchu" ||
        product.contains("sdk_gphone") || product.contains("emulator") || product.contains("sdk")
}

object RetrofitInstance {
    /**
     * Retrofit partagé par toutes les interfaces d'API : chaque requête reçoit le jeton d'accès,
     * et un 401 déclenche le rafraîchissement automatique du jeton.
     */
    fun createRetrofit(baseUrl: String, tokens: TokenStore, onSessionExpired: () -> Unit): Retrofit {
        // Client sans authenticator pour le refresh, sinon un 401 sur /auth/refresh bouclerait.
        val plainApi = authApi(retrofit(baseUrl, OkHttpClient.Builder().timeouts().build()))

        val client = OkHttpClient.Builder()
            .timeouts()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens, { plainApi }, onSessionExpired))
            .build()
        return retrofit(baseUrl, client)
    }

    fun createApi(baseUrl: String, tokens: TokenStore, onSessionExpired: () -> Unit): ApiService =
        authApi(createRetrofit(baseUrl, tokens, onSessionExpired))

    fun authApi(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private fun OkHttpClient.Builder.timeouts() = apply {
        connectTimeout(15, TimeUnit.SECONDS)
        readTimeout(30, TimeUnit.SECONDS)
    }
}
