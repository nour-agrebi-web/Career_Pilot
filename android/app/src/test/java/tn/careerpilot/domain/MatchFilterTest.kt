package tn.careerpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchFilterTest {
    private val matches = listOf(
        match("a", 86, type = "stage", title = "Stage Android", date = "2026-10-01"),
        match("b", 72, type = "freelance", title = "Mission FastAPI", date = "2026-10-05"),
        match("c", 65, type = "emploi", title = "Dev Python", status = MatchStatus.ACCEPTED),
        match("d", 90, type = "stage", title = "Stage masqué", status = MatchStatus.REJECTED),
    )

    @Test
    fun `le seuil masque les offres en dessous sauf les sauvegardees`() {
        assertEquals(listOf("a", "b", "c"), MatchFilter().apply(matches, threshold = 70).map { it.id })
        assertEquals(listOf("a", "c"), MatchFilter().apply(matches, threshold = 80).map { it.id })
    }

    @Test
    fun `une offre masquee n est jamais affichee`() {
        assert(MatchFilter().apply(matches, threshold = 50).none { it.id == "d" })
    }

    @Test
    fun `filtre par type et recherche`() {
        assertEquals(listOf("a"), MatchFilter(type = "stage").apply(matches, 50).map { it.id })
        assertEquals(listOf("b"), MatchFilter(query = "fastapi").apply(matches, 50).map { it.id })
    }

    @Test
    fun `tri par score ou par date`() {
        assertEquals(listOf("a", "b", "c"), MatchFilter(sort = MatchSort.SCORE).apply(matches, 50).map { it.id })
        assertEquals("b", MatchFilter(sort = MatchSort.DATE).apply(matches, 50).first().id)
    }

    @Test
    fun `seulement les sauvegardees`() {
        assertEquals(listOf("c"), MatchFilter(savedOnly = true).apply(matches, 90).map { it.id })
    }
}
