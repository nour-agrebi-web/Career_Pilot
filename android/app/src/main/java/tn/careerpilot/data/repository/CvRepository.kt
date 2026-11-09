package tn.careerpilot.data.repository

import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import tn.careerpilot.data.local.CvDao
import tn.careerpilot.data.local.toDomain
import tn.careerpilot.data.local.toEntity
import tn.careerpilot.data.remote.ApiResult
import tn.careerpilot.data.remote.CareerPilotApi
import tn.careerpilot.data.remote.safeApiCall
import tn.careerpilot.data.remote.safeApiCallNoContent
import tn.careerpilot.domain.ApplyProfileResult
import tn.careerpilot.domain.Cv
import tn.careerpilot.domain.CvAnalysis
import tn.careerpilot.domain.CvStatus
import tn.careerpilot.domain.Validators

/** Fichier choisi par l'utilisateur, lu par [UriFileReader]. */
class PickedFile(val name: String, val mimeType: String?, val bytes: ByteArray, val size: Long = bytes.size.toLong())

class CvRepository(
    private val api: CareerPilotApi,
    private val dao: CvDao,
    private val reader: UriFileReader? = null,
    /** Intervalle et durée maximale du suivi de l'analyse (le cahier des charges vise moins de 30 s). */
    private val pollIntervalMs: Long = 2_000,
    private val pollTimeoutMs: Long = 45_000,
) {

    val cvs: Flow<List<Cv>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun refresh(): ApiResult<Unit> =
        safeApiCall { api.getCvs() }.alsoSave { list -> dao.replaceAll(list.map { it.toEntity() }) }

    /** Import depuis le sélecteur de fichiers Android. */
    suspend fun upload(uri: Uri): ApiResult<String> {
        val file = reader?.read(uri) ?: return ApiResult.Error("Impossible de lire ce fichier.")
        return upload(file)
    }

    /** Envoie le fichier et renvoie l'identifiant du CV créé ; l'analyse démarre côté serveur. */
    suspend fun upload(file: PickedFile): ApiResult<String> {
        // Règles métier vérifiées avant l'envoi : PDF ou DOCX, 5 Mo maximum.
        Validators.cvFile(file.name, file.size)?.let { return ApiResult.Error(it) }
        val type = (file.mimeType ?: "application/octet-stream").toMediaType()
        val part = MultipartBody.Part.createFormData("file", file.name, file.bytes.toRequestBody(type))
        return when (val result = safeApiCall { api.uploadCv(part) }) {
            // L'import archive l'ancien CV actif et peut supprimer la 11e version : on recharge la liste.
            is ApiResult.Success -> {
                refresh()
                ApiResult.Success(result.data.id)
            }
            is ApiResult.Error -> result
        }
    }

    /** CV actif du cache local (null si aucun CV). */
    suspend fun activeCv(): Cv? = cvs.first().firstOrNull { it.active }

    suspend fun analysis(id: String): ApiResult<CvAnalysis> = when (val result = safeApiCall { api.getCvAnalysis(id) }) {
        is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
        is ApiResult.Error -> result
    }

    /**
     * Suit l'analyse jusqu'à PROCESSED ou ERROR (US-2.1.1 : l'écran affiche que le traitement est en cours).
     * La liste Room est mise à jour à chaque étape, l'écran se rafraîchit donc tout seul.
     */
    suspend fun awaitAnalysis(id: String): ApiResult<CvAnalysis> {
        var waited = 0L
        while (true) {
            val result = analysis(id)
            if (result is ApiResult.Error) return result
            val analysis = (result as ApiResult.Success).data
            if (!CvStatus.isRunning(analysis.status)) {
                refresh()
                return result
            }
            if (waited >= pollTimeoutMs) {
                return ApiResult.Error("L'analyse prend plus de temps que prévu. Revenez sur cet écran dans un instant.")
            }
            delay(pollIntervalMs)
            waited += pollIntervalMs
        }
    }

    /** Relance l'analyse (compte comme une opération IA : 5 par jour). */
    suspend fun reanalyze(id: String): ApiResult<Unit> = when (val result = safeApiCall { api.analyzeCv(id) }) {
        is ApiResult.Success -> refresh()
        is ApiResult.Error -> result
    }

    /** Copie le profil déduit dans le profil, qui repasse « non validé » jusqu'à la validation du candidat. */
    suspend fun applyProfile(id: String): ApiResult<ApplyProfileResult> =
        when (val result = safeApiCall { api.applyCvProfile(id) }) {
            is ApiResult.Success -> {
                refresh()
                ApiResult.Success(ApplyProfileResult(result.data.missingSections, result.data.incompleteItems.size))
            }
            is ApiResult.Error -> result
        }

    suspend fun activate(id: String): ApiResult<Unit> = when (val result = safeApiCall { api.activateCv(id) }) {
        is ApiResult.Success -> refresh()
        is ApiResult.Error -> result
    }

    suspend fun delete(id: String): ApiResult<Unit> = when (val result = safeApiCallNoContent { api.deleteCv(id) }) {
        is ApiResult.Success -> refresh()
        is ApiResult.Error -> result
    }
}
