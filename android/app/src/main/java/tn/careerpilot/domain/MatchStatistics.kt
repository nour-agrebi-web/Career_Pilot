package tn.careerpilot.domain

import kotlin.math.roundToInt

/** Tableau de bord calculé à partir des correspondances : volumes, scores et compétences à renforcer. */
data class MatchStatistics(
    val total: Int,
    val saved: Int,
    val hidden: Int,
    val averageScore: Int,
    /** Nombre d'offres par tranche de score : "50-59", "60-69", "70-79", "80-89", "90-100". */
    val histogram: List<Pair<String, Int>>,
    /** Nombre d'offres et score moyen par type de contrat. */
    val byType: List<TypeStat>,
    /** Compétences les plus souvent demandées et absentes du profil (analyse des écarts). */
    val topMissingSkills: List<Pair<String, Int>>,
) {
    data class TypeStat(val type: String, val count: Int, val averageScore: Int)

    companion object {
        private val buckets = listOf(50..59, 60..69, 70..79, 80..89, 90..100)

        fun from(matches: List<Match>, topSkills: Int = 5): MatchStatistics {
            val visible = matches.filter { it.status != MatchStatus.REJECTED }
            val histogram = buckets.map { range ->
                val label = "${range.first}-${range.last}"
                label to visible.count { it.score in range }
            }
            val byType = visible.groupBy { it.offer.type }.map { (type, list) ->
                TypeStat(type, list.size, list.map { it.score }.average().roundToInt())
            }.sortedByDescending { it.count }
            // « Docker » et « docker » comptent comme la même compétence.
            val missing = visible.flatMap { m -> m.missingSkills.map { it.trim() }.distinctBy { it.lowercase() } }
                .groupBy { it.lowercase() }
                .values
                .map { spellings -> spellings.first() to spellings.size }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
                .take(topSkills)
            return MatchStatistics(
                total = visible.size,
                saved = visible.count { it.isSaved },
                hidden = matches.size - visible.size,
                averageScore = if (visible.isEmpty()) 0 else visible.map { it.score }.average().roundToInt(),
                histogram = histogram,
                byType = byType,
                topMissingSkills = missing,
            )
        }
    }
}
