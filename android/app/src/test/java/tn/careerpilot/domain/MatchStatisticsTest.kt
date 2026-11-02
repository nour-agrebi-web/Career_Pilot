package tn.careerpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchStatisticsTest {
    @Test
    fun `calcule volumes, moyenne, histogramme et competences manquantes`() {
        val stats = MatchStatistics.from(listOf(
            match("a", 86, "stage", missing = listOf("Docker", "CI")),
            match("b", 72, "freelance", missing = listOf("docker")),
            match("c", 64, "stage", MatchStatus.ACCEPTED, missing = listOf("Docker")),
            match("d", 95, "stage", MatchStatus.REJECTED, missing = listOf("Kubernetes")),
        ))
        assertEquals(3, stats.total)
        assertEquals(1, stats.saved)
        assertEquals(1, stats.hidden)
        assertEquals(74, stats.averageScore) // (86 + 72 + 64) / 3
        assertEquals(listOf("50-59" to 0, "60-69" to 1, "70-79" to 1, "80-89" to 1, "90-100" to 0), stats.histogram)
        assertEquals("stage", stats.byType.first().type)
        assertEquals(75, stats.byType.first().averageScore)
        assertEquals("Docker" to 3, stats.topMissingSkills.first())
    }

    @Test
    fun `aucune offre`() {
        val stats = MatchStatistics.from(emptyList())
        assertEquals(0, stats.total)
        assertEquals(0, stats.averageScore)
    }
}
