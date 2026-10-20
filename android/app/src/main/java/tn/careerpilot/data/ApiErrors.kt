package tn.careerpilot.data

import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException

/** Transforme une erreur réseau ou HTTP en message lisible pour l'utilisateur. */
fun Throwable.toUserMessage(): String = when (this) {
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
    is IOException -> "Impossible de joindre le serveur. Vérifiez que l'API est lancée."
    else -> "Une erreur inattendue est survenue."
}
