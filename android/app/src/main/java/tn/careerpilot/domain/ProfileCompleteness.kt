package tn.careerpilot.domain

/**
 * Taux de complétude du profil : indique au candidat ce qu'il manque pour un matching de qualité.
 * Chaque critère rapporte des points ; le total fait 100.
 */
data class Completeness(val percent: Int, val missing: List<String>)

object ProfileCompleteness {
    private const val MIN_SKILLS = 5

    fun compute(profile: Profile): Completeness {
        val criteria = listOf(
            Triple(30, profile.skills.size >= MIN_SKILLS, "Au moins $MIN_SKILLS compétences (${profile.skills.size} actuellement)"),
            Triple(20, profile.educations.isNotEmpty(), "Une formation"),
            Triple(20, profile.experiences.isNotEmpty(), "Une expérience (stage, projet, job étudiant)"),
            Triple(10, profile.languages.isNotEmpty(), "Les langues parlées"),
            Triple(10, !profile.domain.isNullOrBlank(), "Votre domaine"),
            Triple(10, profile.level != null, "Votre niveau (stage, junior, confirmé)"),
        )
        // Les compétences comptent en partie tant que le minimum n'est pas atteint.
        val skillPoints = 30 * profile.skills.size.coerceAtMost(MIN_SKILLS) / MIN_SKILLS
        val percent = skillPoints + criteria.drop(1).filter { it.second }.sumOf { it.first }
        return Completeness(percent, criteria.filterNot { it.second }.map { it.third })
    }
}
