package tn.careerpilot.domain

enum class MatchSort { SCORE, DATE }

/** Filtres dynamiques du fil d'offres, appliqués sur les correspondances en cache. */
data class MatchFilter(
    val type: String? = null,
    val query: String = "",
    val sort: MatchSort = MatchSort.SCORE,
    val savedOnly: Boolean = false,
) {
    fun apply(matches: List<Match>, threshold: Int): List<Match> {
        return matches
            .asSequence()
            .filter { it.status != MatchStatus.REJECTED }
            // US-3.2.4 : un seuil relevé masque aussitôt les offres en dessous. Une offre sauvegardée reste visible.
            .filter { it.score >= threshold || it.isSaved }
            .filter { !savedOnly || it.isSaved }
            .filter { type == null || it.offer.type == type }
            // Mot entier, sans casse ni accents, et par domaine (« IT » ne trouve plus « littéraire »).
            .filter { OfferSearch.matches(it.offer, query) }
            .let { seq ->
                when (sort) {
                    MatchSort.SCORE -> seq.sortedByDescending { it.score }
                    MatchSort.DATE -> seq.sortedByDescending { it.offer.publicationDate.orEmpty() }
                }
            }
            .toList()
    }
}
