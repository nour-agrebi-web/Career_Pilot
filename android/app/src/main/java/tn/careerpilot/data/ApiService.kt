package tn.careerpilot.data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

/** Routes de l'API FastAPI (voir backend/app/modules/auth/router.py). */
interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest)

    // Le backend attend un formulaire OAuth2 : l'e-mail va dans le champ "username".
    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(@Field("username") email: String, @Field("password") password: String): TokenPair

    // Appel synchrone : utilisé par TokenAuthenticator, qui tourne déjà sur un thread OkHttp.
    @POST("auth/refresh")
    fun refresh(@Body body: RefreshRequest): Call<TokenPair>

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest)

    @GET("users/me")
    suspend fun me(): CurrentUser
}
