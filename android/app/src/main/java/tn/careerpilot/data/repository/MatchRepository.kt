package tn.careerpilot.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tn.careerpilot.data.local.MatchDao
import tn.careerpilot.data.local.toDomain
import tn.careerpilot.data.local.toEntity
import tn.careerpilot.data.remote.ApiResult
import tn.careerpilot.data.remote.CareerPilotApi
import tn.careerpilot.data.remote.MatchStatusDto
import tn.careerpilot.data.remote.safeApiCall
import tn.careerpilot.data.remote.safeApiCallNoContent
import tn.careerpilot.domain.Match
import tn.careerpilot.domain.MatchStatus

class MatchRepository(private val api: CareerPilotApi, private val dao: MatchDao) {

    val matches: Flow<List<Match>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun match(id: String): Flow<Match?> = dao.observe(id).map { it?.toDomain() }

    /**
     * Synchronise le cache avec le serveur. Avec [runMatching], lance d'abord un cycle de matching
     * (refusé tant que le profil n'est pas validé : on affiche alors le cache tel quel).
     * Renvoie le nombre de nouvelles offres trouvées.
     */
    suspend fun refresh(runMatching: Boolean): ApiResult<Int> {
        var newMatches = 0
        if (runMatching) {
            when (val run = safeApiCall { api.refreshMatches() }) {
                is ApiResult.Success -> newMatches = run.data.newMatches
                is ApiResult.Error -> if (run.code != 409) return run
            }
        }
        return when (val result = safeApiCall { api.getMatches() }) {
            is ApiResult.Success -> {
                dao.replaceAll(result.data.map { it.toEntity() })
                ApiResult.Success(newMatches)
            }
            is ApiResult.Error -> result
        }
    }

    suspend fun setStatus(id: String, status: MatchStatus): ApiResult<Unit> =
        safeApiCall { api.updateMatchStatus(id, MatchStatusDto(status.name)) }.alsoSave { dao.upsert(it.toEntity()) }

    /** « Pas pour moi » : DELETE côté API, l'offre disparaît du fil et ne sera plus proposée. */
    suspend fun hide(id: String): ApiResult<Unit> {
        val result = safeApiCallNoContent { api.hideMatch(id) }
        if (result is ApiResult.Success) dao.updateStatus(id, MatchStatus.REJECTED.name)
        return result
    }
}
