package tn.careerpilot.domain

/** Modèles métier utilisés par les ViewModels et les écrans (indépendants de Retrofit et de Room). */

data class Location(val city: String, val country: String = "Tunisie")

data class Skill(val id: String, val name: String)

data class Experience(
    val id: String,
    val position: String,
    val company: String,
    val startDate: String,
    val endDate: String?,
)

data class Education(
    val id: String,
    val degree: String,
    val institution: String,
    val startDate: String,
    val endDate: String?,
)

data class Language(val id: String, val name: String, val level: String)

data class Profile(
    val domain: String? = null,
    val level: String? = null,
    val validated: Boolean = false,
    val skills: List<Skill> = emptyList(),
    val experiences: List<Experience> = emptyList(),
    val educations: List<Education> = emptyList(),
    val languages: List<Language> = emptyList(),
) {
    /** Règle métier : des compétences, et une formation ou une expérience. */
    val isExploitable: Boolean get() = skills.isNotEmpty() && (educations.isNotEmpty() || experiences.isNotEmpty())
}

data class Preferences(
    val jobTypes: Set<String> = JobTypes.codes.toSet(),
    val locations: List<Location> = emptyList(),
    val remoteWork: Boolean = true,
    val threshold: Int = 70,
    val notificationsEnabled: Boolean = true,
)

data class Cv(
    val id: String,
    val filename: String,
    val size: Long,
    val version: Int,
    val status: String,
    val active: Boolean,
    val createdAt: String,
    val errorCode: String? = null,
    val error: String? = null,
    /** Le profil déduit de ce CV a déjà été copié dans le profil. */
    val applied: Boolean = false,
) {
    val isAnalyzing: Boolean get() = CvStatus.isRunning(status)
}

/** Résultat de l'analyse d'un CV, résumé pour l'écran « Mes CV ». */
data class CvAnalysis(
    val cvId: String,
    val status: String,
    val errorCode: String?,
    val error: String?,
    val applied: Boolean,
    val engine: String?,
    val domain: String?,
    val skills: List<String>,
    val experienceCount: Int,
    val educationCount: Int,
    val languageCount: Int,
    /** Sections que l'analyse n'a pas su remplir : skills, experiences, educations. */
    val missingSections: List<String>,
    /** Éléments trouvés mais incomplets (sans date de début, sans niveau…), à compléter à la main. */
    val incompleteCount: Int,
)

/** Après « Utiliser ce profil » : ce qu'il reste à compléter dans le profil. */
data class ApplyProfileResult(val missingSections: List<String>, val incompleteCount: Int)

data class Offer(
    val id: String,
    val title: String,
    val company: String,
    val city: String?,
    val country: String?,
    val type: String,
    val workMode: String?,
    val level: String?,
    val description: String,
    val requiredSkills: List<String>,
    val source: String,
    val url: String?,
    val publicationDate: String?,
)

data class ScoreBreakdown(val semantic: Int, val skills: Int, val level: Int, val preferences: Int)

enum class MatchStatus { PENDING, VIEWED, ACCEPTED, REJECTED }

data class Match(
    val id: String,
    val score: Int,
    val breakdown: ScoreBreakdown,
    val reasons: List<String>,
    val missingSkills: List<String>,
    val status: MatchStatus,
    val createdAt: String,
    val offer: Offer,
) {
    val isSaved: Boolean get() = status == MatchStatus.ACCEPTED
}

/** Libellés français des codes renvoyés par l'API. */
object JobTypes {
    val all = listOf("stage" to "Stage", "emploi" to "Emploi", "freelance" to "Freelance", "temps_partiel" to "Temps partiel")
    val codes = all.map { it.first }
    fun label(code: String?): String = all.firstOrNull { it.first == code }?.second ?: code.orEmpty()
}

object WorkModes {
    fun label(code: String?): String = when (code) {
        "sur_site" -> "Sur site"
        "hybride" -> "Hybride"
        "teletravail" -> "Télétravail"
        else -> code.orEmpty()
    }
}

object Levels {
    val all = listOf("stage" to "Stage", "junior" to "Junior", "confirme" to "Confirmé")
    fun label(code: String?): String = all.firstOrNull { it.first == code }?.second ?: "Non précisé"
}

object LanguageLevels {
    val all = listOf("A1", "A2", "B1", "B2", "C1", "C2", "natif")
}
