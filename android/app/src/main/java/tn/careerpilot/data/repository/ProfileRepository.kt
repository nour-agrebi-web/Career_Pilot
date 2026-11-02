package tn.careerpilot.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tn.careerpilot.data.local.ProfileDao
import tn.careerpilot.data.local.ProfileEntity
import tn.careerpilot.data.local.toDomain
import tn.careerpilot.data.local.toEntity
import tn.careerpilot.data.remote.ApiResult
import tn.careerpilot.data.remote.CareerPilotApi
import tn.careerpilot.data.remote.EducationDto
import tn.careerpilot.data.remote.ExperienceDto
import tn.careerpilot.data.remote.LanguageDto
import tn.careerpilot.data.remote.ProfileDto
import tn.careerpilot.data.remote.ProfileUpdateDto
import tn.careerpilot.data.remote.SkillDto
import tn.careerpilot.data.remote.map
import tn.careerpilot.data.remote.safeApiCall
import tn.careerpilot.data.remote.safeApiCallNoContent
import tn.careerpilot.domain.Education
import tn.careerpilot.domain.Experience
import tn.careerpilot.domain.Language
import tn.careerpilot.domain.Profile

enum class ProfileSection(val path: String) { SKILLS("skills"), EXPERIENCES("experiences"), EDUCATIONS("educations"), LANGUAGES("languages") }

/**
 * Source de vérité unique du profil : l'écran observe le cache Room, et chaque modification passe
 * d'abord par l'API puis met à jour Room (ce qui rafraîchit l'écran automatiquement).
 */
class ProfileRepository(private val api: CareerPilotApi, private val dao: ProfileDao) {

    val profile: Flow<Profile> = combine(
        dao.observeProfile(), dao.observeSkills(), dao.observeExperiences(), dao.observeEducations(), dao.observeLanguages(),
    ) { info, skills, experiences, educations, languages ->
        Profile(
            domain = info?.domain,
            level = info?.level,
            validated = info?.validated ?: false,
            skills = skills.map { it.toDomain() },
            experiences = experiences.map { it.toDomain() },
            educations = educations.map { it.toDomain() },
            languages = languages.map { it.toDomain() },
        )
    }

    suspend fun refresh(): ApiResult<Unit> = saveProfile(safeApiCall { api.getProfile() })

    suspend fun updateInfo(domain: String?, level: String?): ApiResult<Unit> =
        saveProfile(safeApiCall { api.updateProfile(ProfileUpdateDto(domain?.takeIf { it.isNotBlank() }, level)) })

    suspend fun validate(): ApiResult<Unit> = saveProfile(safeApiCall { api.validateProfile() })

    suspend fun reset(): ApiResult<Unit> {
        val result = safeApiCallNoContent { api.resetProfile() }
        if (result is ApiResult.Success) dao.clearAll()
        return result
    }

    // --- Compétences ---
    suspend fun addSkill(name: String): ApiResult<Unit> =
        safeApiCall { api.addSkill(SkillDto(name = name.trim())) }.alsoSave { dao.upsertSkill(it.toEntity()) }

    suspend fun updateSkill(id: String, name: String): ApiResult<Unit> =
        safeApiCall { api.updateSkill(id, SkillDto(name = name.trim())) }.alsoSave { dao.upsertSkill(it.toEntity()) }

    // --- Expériences ---
    suspend fun saveExperience(item: Experience): ApiResult<Unit> {
        val body = ExperienceDto(null, item.position.trim(), item.company.trim(), item.startDate, item.endDate?.ifBlank { null })
        val call = if (item.id.isEmpty()) safeApiCall { api.addExperience(body) } else safeApiCall { api.updateExperience(item.id, body) }
        return call.alsoSave { dao.upsertExperience(it.toEntity()) }
    }

    // --- Formations ---
    suspend fun saveEducation(item: Education): ApiResult<Unit> {
        val body = EducationDto(null, item.degree.trim(), item.institution.trim(), item.startDate, item.endDate?.ifBlank { null })
        val call = if (item.id.isEmpty()) safeApiCall { api.addEducation(body) } else safeApiCall { api.updateEducation(item.id, body) }
        return call.alsoSave { dao.upsertEducation(it.toEntity()) }
    }

    // --- Langues ---
    suspend fun saveLanguage(item: Language): ApiResult<Unit> {
        val body = LanguageDto(null, item.name.trim(), item.level)
        val call = if (item.id.isEmpty()) safeApiCall { api.addLanguage(body) } else safeApiCall { api.updateLanguage(item.id, body) }
        return call.alsoSave { dao.upsertLanguage(it.toEntity()) }
    }

    suspend fun deleteItem(section: ProfileSection, id: String): ApiResult<Unit> {
        val result = safeApiCallNoContent { api.deleteProfileItem(section.path, id) }
        if (result is ApiResult.Success) {
            when (section) {
                ProfileSection.SKILLS -> dao.deleteSkill(id)
                ProfileSection.EXPERIENCES -> dao.deleteExperience(id)
                ProfileSection.EDUCATIONS -> dao.deleteEducation(id)
                ProfileSection.LANGUAGES -> dao.deleteLanguage(id)
            }
        }
        return result
    }

    private suspend fun saveProfile(result: ApiResult<ProfileDto>): ApiResult<Unit> = result.alsoSave { dto ->
        dao.replaceAll(
            ProfileEntity(dto.id, dto.domain, dto.level, dto.validated),
            dto.skills.orEmpty().map { it.toEntity() },
            dto.experiences.orEmpty().map { it.toEntity() },
            dto.educations.orEmpty().map { it.toEntity() },
            dto.languages.orEmpty().map { it.toEntity() },
        )
    }
}

/** En cas de succès, enregistre la réponse dans Room ; renvoie le résultat sans la donnée. */
internal suspend inline fun <T> ApiResult<T>.alsoSave(save: (T) -> Unit): ApiResult<Unit> {
    if (this is ApiResult.Success) save(data)
    return map { }
}
