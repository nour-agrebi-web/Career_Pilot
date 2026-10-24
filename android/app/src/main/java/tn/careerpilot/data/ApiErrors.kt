package tn.careerpilot.data

import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException

/** Transforme une erreur réseau ou HTTP en message lisible pour l'utilisateur. */
fun Throwable.toUserMessage(apiUrl: String? = null): String = when (this) {
    is HttpException -> {
        val detail = runCatching {
            JsonParser.parseString(response()?.errorBody()?.string()).asJsonObject.get("detail")
        }.getOrNull()
        when {
            detail != null && detail.isJsonPrimitive -> detail.asString
            code() == 422 -> "Vérifiez les champs : e-mail valide et mot de passe de 8 caractères minimum."
            else -> "Erreur du serveur (${code()})."
        }
    }
    // Le détail technique (type d'erreur + message) aide à savoir pourquoi la connexion échoue :
    // ConnectException = API non lancée ou mauvaise adresse, UnknownServiceException = HTTP bloqué, etc.
    is IOException -> "Impossible de joindre le serveur" + (apiUrl?.let { " ($it)" } ?: "") +
        ".\nDétail : ${javaClass.simpleName}: $message"
    else -> "Une erreur inattendue est survenue."
}
