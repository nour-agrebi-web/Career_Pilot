package tn.careerpilot.data

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val email: String,
    val password: String,
    val language: String,
    val consent: Boolean,
)

data class TokenPair(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class CurrentUser(
    val id: String,
    val email: String,
    val language: String,
    val role: String,
)
