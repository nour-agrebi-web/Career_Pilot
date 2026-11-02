package tn.careerpilot.data.remote

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException

/** Résultat d'un appel réseau : jamais d'exception jusqu'aux ViewModels. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Error -> this
}

/**
 * Exécute un appel Retrofit dans un try/catch (erreurs réseau) et vérifie `response.isSuccessful`
 * (erreurs HTTP 4xx/5xx). Le message du champ `detail` de FastAPI est affiché tel quel.
 */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> = try {
    val response = call()
    val body = response.body()
    when {
        response.isSuccessful && body != null -> ApiResult.Success(body)
        response.isSuccessful -> ApiResult.Error("Réponse vide du serveur.", response.code())
        else -> ApiResult.Error(errorMessage(response), response.code())
    }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w("CareerPilot", "Erreur réseau", e)
    ApiResult.Error("Impossible de joindre le serveur. Vérifiez votre connexion.")
} catch (e: Exception) {
    Log.e("CareerPilot", "Erreur inattendue", e)
    ApiResult.Error("Une erreur inattendue est survenue.")
}

/** Variante pour les routes sans corps de réponse (204 No Content). */
suspend fun safeApiCallNoContent(call: suspend () -> Response<Unit>): ApiResult<Unit> = try {
    val response = call()
    if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Error(errorMessage(response), response.code())
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    ApiResult.Error("Impossible de joindre le serveur. Vérifiez votre connexion.")
} catch (e: Exception) {
    Log.e("CareerPilot", "Erreur inattendue", e)
    ApiResult.Error("Une erreur inattendue est survenue.")
}

fun errorMessage(response: Response<*>): String {
    val detail = runCatching {
        JsonParser.parseString(response.errorBody()?.string()).asJsonObject.get("detail")
    }.getOrNull()
    return when {
        detail != null && detail.isJsonPrimitive -> detail.asString
        response.code() == 401 -> "Session expirée, reconnectez-vous."
        response.code() == 404 -> "Élément introuvable."
        response.code() == 422 -> "Certaines valeurs sont invalides."
        response.code() >= 500 -> "Erreur du serveur (${response.code()}). Réessayez plus tard."
        else -> "Erreur (${response.code()})."
    }
}
