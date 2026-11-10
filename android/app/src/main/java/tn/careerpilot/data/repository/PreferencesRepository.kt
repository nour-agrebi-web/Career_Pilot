package tn.careerpilot.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tn.careerpilot.data.local.PreferencesDao
import tn.careerpilot.data.local.toDomain
import tn.careerpilot.data.local.toDto
import tn.careerpilot.data.local.toEntity
import tn.careerpilot.data.remote.ApiResult
import tn.careerpilot.data.remote.CareerPilotApi
import tn.careerpilot.data.remote.safeApiCall
import tn.careerpilot.data.remote.safeApiCallNoContent
import tn.careerpilot.domain.Preferences

class PreferencesRepository(private val api: CareerPilotApi, private val dao: PreferencesDao) {

    /** Préférences en cache ; valeurs par défaut tant que le serveur n'a pas répondu. */
    val preferences: Flow<Preferences> = dao.observe().map { it?.toDomain() ?: Preferences() }

    suspend fun refresh(): ApiResult<Unit> =
        safeApiCall { api.getPreferences() }.alsoSave { dao.upsert(it.toEntity()) }

    suspend fun save(preferences: Preferences): ApiResult<Unit> =
        safeApiCall { api.updatePreferences(preferences.toDto()) }.alsoSave { dao.upsert(it.toEntity()) }

    suspend fun reset(): ApiResult<Unit> = when (val result = safeApiCallNoContent { api.resetPreferences() }) {
        is ApiResult.Success -> refresh()
        is ApiResult.Error -> result
    }
}
