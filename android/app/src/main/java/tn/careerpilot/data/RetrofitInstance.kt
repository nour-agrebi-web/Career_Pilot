package tn.careerpilot.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Configuration du port de l'API FastAPI locale (python -m app)
const val API_PORT = 8000

// --- CHOISIR L'ADRESSE EN FONCTION DE VOTRE CIBLE ---
// 1. Pour l'ÉMULATEUR ANDROID (10.0.2.2 = le PC hôte)
const val BASE_URL = "http://10.0.2.2:$API_PORT/"
// 2. Pour un APPAREIL PHYSIQUE (même Wi-Fi que le PC ; IPv4 donnée par ipconfig)
// const val LOCAL_IP = "192.168.1.5"
// const val BASE_URL = "http://$LOCAL_IP:$API_PORT/"

object RetrofitInstance {
    fun createApi(baseUrl: String, tokens: TokenStore, onSessionExpired: () -> Unit): ApiService {
        // Client sans authenticator pour le refresh, sinon un 401 sur /auth/refresh bouclerait.
        val plainApi = retrofit(baseUrl, OkHttpClient.Builder().timeouts().build())

        val client = OkHttpClient.Builder()
            .timeouts()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens, { plainApi }, onSessionExpired))
            .build()
        return retrofit(baseUrl, client)
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient): ApiService =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

    private fun OkHttpClient.Builder.timeouts() = apply {
        connectTimeout(15, TimeUnit.SECONDS)
        readTimeout(30, TimeUnit.SECONDS)
    }
}
