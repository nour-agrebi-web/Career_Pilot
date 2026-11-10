package tn.careerpilot.data.local

import tn.careerpilot.data.remote.CvDto
import tn.careerpilot.data.remote.EducationDto
import tn.careerpilot.data.remote.ExperienceDto
import tn.careerpilot.data.remote.LanguageDto
import tn.careerpilot.data.remote.LocationDto
import tn.careerpilot.data.remote.MatchDto
import tn.careerpilot.data.remote.PreferencesDto
import tn.careerpilot.data.remote.SkillDto
import tn.careerpilot.domain.Cv
import tn.careerpilot.domain.Education
import tn.careerpilot.domain.Experience
import tn.careerpilot.domain.Language
import tn.careerpilot.domain.Location
import tn.careerpilot.domain.Match
import tn.careerpilot.domain.MatchStatus
import tn.careerpilot.domain.Offer
import tn.careerpilot.domain.Preferences
import tn.careerpilot.domain.ScoreBreakdown
import tn.careerpilot.domain.Skill

// Conversions API (DTO) -> cache (Entity) -> modèle métier (domain).

fun SkillDto.toEntity() = SkillEntity(id.orEmpty(), name)
fun ExperienceDto.toEntity() = ExperienceEntity(id.orEmpty(), position, company, startDate, endDate)
fun EducationDto.toEntity() = EducationEntity(id.orEmpty(), degree, institution, startDate, endDate)
fun LanguageDto.toEntity() = LanguageEntity(id.orEmpty(), name, level)

fun SkillEntity.toDomain() = Skill(id, name)
fun ExperienceEntity.toDomain() = Experience(id, position, company, startDate, endDate)
fun EducationEntity.toDomain() = Education(id, degree, institution, startDate, endDate)
fun LanguageEntity.toDomain() = Language(id, name, level)

fun PreferencesDto.toEntity() = PreferencesEntity(
    jobTypes = jobTypes,
    locations = locations.map { Location(it.city, it.country) },
    remoteWork = remoteWork,
    threshold = threshold,
    notificationsEnabled = notificationsEnabled,
)

fun PreferencesEntity.toDomain() = Preferences(jobTypes.toSet(), locations, remoteWork, threshold, notificationsEnabled)

fun Preferences.toDto() = PreferencesDto(
    jobTypes = jobTypes.toList(),
    locations = locations.map { LocationDto(it.city, it.country) },
    remoteWork = remoteWork,
    threshold = threshold,
    notificationsEnabled = notificationsEnabled,
)

fun CvDto.toEntity() = CvEntity(id, filename, size, version, status, active, createdAt)
fun CvEntity.toDomain() = Cv(id, filename, size, version, status, active, createdAt)

fun MatchDto.toEntity() = MatchEntity(
    id = id,
    score = score,
    semanticScore = breakdown.semantic,
    skillsScore = breakdown.skills,
    levelScore = breakdown.level,
    preferencesScore = breakdown.preferences,
    reasons = reasons.orEmpty(),
    missingSkills = missingSkills.orEmpty(),
    status = status,
    createdAt = createdAt,
    offerId = offer.id,
    title = offer.title,
    company = offer.company,
    city = offer.location?.city,
    country = offer.location?.country,
    type = offer.type,
    workMode = offer.workMode,
    offerLevel = offer.level,
    description = offer.description.orEmpty(),
    requiredSkills = offer.requiredSkills.orEmpty(),
    source = offer.source,
    url = offer.url,
    publicationDate = offer.publicationDate,
)

fun MatchEntity.toDomain() = Match(
    id = id,
    score = score,
    breakdown = ScoreBreakdown(semanticScore, skillsScore, levelScore, preferencesScore),
    reasons = reasons,
    missingSkills = missingSkills,
    status = runCatching { MatchStatus.valueOf(status) }.getOrDefault(MatchStatus.PENDING),
    createdAt = createdAt,
    offer = Offer(
        id = offerId, title = title, company = company, city = city, country = country, type = type,
        workMode = workMode, level = offerLevel, description = description, requiredSkills = requiredSkills,
        source = source, url = url, publicationDate = publicationDate,
    ),
)
