package tn.careerpilot.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
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
import tn.careerpilot.domain.Cv
import tn.careerpilot.domain.Validators

/** Fichier choisi par l'utilisateur, lu par [UriFileReader]. */
class PickedFile(val name: String, val mimeType: String?, val bytes: ByteArray, val size: Long = bytes.size.toLong())

class CvRepository(
    private val api: CareerPilotApi,
    private val dao: CvDao,
    private val reader: UriFileReader? = null,
) {

    val cvs: Flow<List<Cv>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun refresh(): ApiResult<Unit> =
        safeApiCall { api.getCvs() }.alsoSave { list -> dao.replaceAll(list.map { it.toEntity() }) }

    /** Import depuis le sélecteur de fichiers Android. */
    suspend fun upload(uri: Uri): ApiResult<Unit> {
        val file = reader?.read(uri) ?: return ApiResult.Error("Impossible de lire ce fichier.")
        return upload(file)
    }

    suspend fun upload(file: PickedFile): ApiResult<Unit> {
        // Règles métier vérifiées avant l'envoi : PDF ou DOCX, 5 Mo maximum.
        Validators.cvFile(file.name, file.size)?.let { return ApiResult.Error(it) }
        val type = (file.mimeType ?: "application/octet-stream").toMediaType()
        val part = MultipartBody.Part.createFormData("file", file.name, file.bytes.toRequestBody(type))
        val result = safeApiCall { api.uploadCv(part) }
        // L'import archive l'ancien CV actif et peut supprimer la 11e version : on recharge la liste.
        return if (result is ApiResult.Success) refresh() else result.alsoSave { }
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
