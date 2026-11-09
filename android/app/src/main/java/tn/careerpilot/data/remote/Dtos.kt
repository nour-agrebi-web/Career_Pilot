package tn.careerpilot.data.remote

import com.google.gson.annotations.SerializedName

/** Corps JSON échangés avec l'API FastAPI (noms en snake_case côté serveur). */

data class LocationDto(val city: String, val country: String)

data class SkillDto(val id: String? = null, val name: String)

data class ExperienceDto(
    val id: String? = null,
    val position: String,
    val company: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String?,
)

data class EducationDto(
    val id: String? = null,
    val degree: String,
    val institution: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String?,
)

data class LanguageDto(val id: String? = null, val name: String, val level: String)

data class ProfileDto(
    val id: String,
    val domain: String?,
    val level: String?,
    val validated: Boolean,
    val skills: List<SkillDto>?,
    val experiences: List<ExperienceDto>?,
    val educations: List<EducationDto>?,
    val languages: List<LanguageDto>?,
)

data class ProfileUpdateDto(val domain: String?, val level: String?)

data class PreferencesDto(
    @SerializedName("job_types") val jobTypes: List<String>,
    val locations: List<LocationDto>,
    @SerializedName("remote_work") val remoteWork: Boolean,
    val threshold: Int,
    @SerializedName("notifications_enabled") val notificationsEnabled: Boolean,
)

data class CvDto(
    val id: String,
    val filename: String,
    val size: Long,
    val version: Int,
    val status: String,
    val active: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("error_code") val errorCode: String? = null,
    val error: String? = null,
    val applied: Boolean = false,
)

/** Profil déduit du CV (brouillon) : les champs peuvent manquer, le candidat les complète. */
data class ProfileDraftDto(
    val domain: String?,
    val level: String?,
    val skills: List<SkillDto>?,
    val experiences: List<DraftExperienceDto>?,
    val educations: List<DraftEducationDto>?,
    val languages: List<DraftLanguageDto>?,
)

data class DraftExperienceDto(
    val position: String?,
    val company: String?,
    @SerializedName("start_date") val startDate: String?,
    @SerializedName("end_date") val endDate: String?,
)

data class DraftEducationDto(
    val degree: String?,
    val institution: String?,
    @SerializedName("start_date") val startDate: String?,
    @SerializedName("end_date") val endDate: String?,
)

data class DraftLanguageDto(val name: String, val level: String?)

data class IncompleteItemDto(
    val section: String,
    @SerializedName("missing_fields") val missingFields: List<String>,
)

/** GET /cvs/{id}/analysis */
data class CvAnalysisDto(
    val id: String,
    val status: String,
    @SerializedName("error_code") val errorCode: String?,
    val error: String?,
    val applied: Boolean = false,
    val engine: String?,
    @SerializedName("extraction_method") val extractionMethod: String?,
    @SerializedName("duration_ms") val durationMs: Long?,
    val draft: ProfileDraftDto?,
    @SerializedName("missing_sections") val missingSections: List<String> = emptyList(),
    @SerializedName("incomplete_items") val incompleteItems: List<IncompleteItemDto> = emptyList(),
)

/** POST /cvs/{id}/apply-profile */
data class ApplyProfileResultDto(
    val profile: ProfileDto,
    @SerializedName("missing_sections") val missingSections: List<String> = emptyList(),
    @SerializedName("incomplete_items") val incompleteItems: List<IncompleteItemDto> = emptyList(),
)

data class AiQuotaDto(val used: Int, val limit: Int, val remaining: Int)

data class OfferDto(
    val id: String,
    val source: String,
    val title: String,
    val company: String,
    val location: LocationDto?,
    val type: String,
    @SerializedName("work_mode") val workMode: String?,
    val level: String?,
    val description: String?,
    @SerializedName("required_skills") val requiredSkills: List<String>?,
    @SerializedName("publication_date") val publicationDate: String?,
    val url: String?,
)

data class BreakdownDto(val semantic: Int, val skills: Int, val level: Int, val preferences: Int)

data class MatchDto(
    val id: String,
    val score: Int,
    val breakdown: BreakdownDto,
    val reasons: List<String>?,
    @SerializedName("missing_skills") val missingSkills: List<String>?,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    val offer: OfferDto,
)

data class MatchStatusDto(val status: String)

data class RefreshResultDto(@SerializedName("new_matches") val newMatches: Int)
