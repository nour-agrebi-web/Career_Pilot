package tn.careerpilot.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import tn.careerpilot.domain.Location

/** Utilisateur connecté, mis en cache pour ouvrir l'application sans réseau. */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val language: String,
    val role: String,
    val syncedAt: Long,
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val domain: String?,
    val level: String?,
    val validated: Boolean,
)

@Entity(tableName = "skills")
data class SkillEntity(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "experiences")
data class ExperienceEntity(
    @PrimaryKey val id: String,
    val position: String,
    val company: String,
    val startDate: String,
    val endDate: String?,
)

@Entity(tableName = "educations")
data class EducationEntity(
    @PrimaryKey val id: String,
    val degree: String,
    val institution: String,
    val startDate: String,
    val endDate: String?,
)

@Entity(tableName = "languages")
data class LanguageEntity(@PrimaryKey val id: String, val name: String, val level: String)

/** Une seule ligne (id = 0) : les préférences de l'utilisateur connecté. */
@Entity(tableName = "preferences")
data class PreferencesEntity(
    @PrimaryKey val id: Int = 0,
    val jobTypes: List<String>,
    val locations: List<Location>,
    val remoteWork: Boolean,
    val threshold: Int,
    val notificationsEnabled: Boolean,
)

@Entity(tableName = "cvs")
data class CvEntity(
    @PrimaryKey val id: String,
    val filename: String,
    val size: Long,
    val version: Int,
    val status: String,
    val active: Boolean,
    val createdAt: String,
    val errorCode: String? = null,
    val error: String? = null,
    val applied: Boolean = false,
)

/** Correspondance et offre associée, à plat pour un affichage rapide du fil (< 2 s grâce au cache). */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val score: Int,
    val semanticScore: Int,
    val skillsScore: Int,
    val levelScore: Int,
    val preferencesScore: Int,
    val reasons: List<String>,
    val missingSkills: List<String>,
    val status: String,
    val createdAt: String,
    val offerId: String,
    val title: String,
    val company: String,
    val city: String?,
    val country: String?,
    val type: String,
    val workMode: String?,
    val offerLevel: String?,
    val description: String,
    val requiredSkills: List<String>,
    val source: String,
    val url: String?,
    val publicationDate: String?,
)
