package tn.careerpilot.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object Network {
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
