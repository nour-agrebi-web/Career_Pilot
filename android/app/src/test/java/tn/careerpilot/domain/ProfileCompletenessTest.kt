package tn.careerpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCompletenessTest {
    private val skills = (1..5).map { Skill("$it", "Compétence $it") }
    private val education = Education("e", "Licence DSI", "ISET Radès", "2024-09-15", null)

    @Test
    fun `profil vide`() {
        val result = ProfileCompleteness.compute(Profile())
        assertEquals(0, result.percent)
        assertEquals(6, result.missing.size)
    }

    @Test
    fun `les competences comptent en partie avant le minimum`() {
        assertEquals(12, ProfileCompleteness.compute(Profile(skills = skills.take(2))).percent)
    }

    @Test
    fun `profil complet`() {
        val profile = Profile(
            domain = "Mobile", level = "stage", skills = skills, educations = listOf(education),
            experiences = listOf(Experience("x", "Stagiaire", "Nexa", "2025-07-01", "2025-08-31")),
            languages = listOf(Language("l", "Anglais", "B2")),
        )
        val result = ProfileCompleteness.compute(profile)
        assertEquals(100, result.percent)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun `regle profil exploitable`() {
        assertFalse(Profile(skills = skills).isExploitable)
        assertTrue(Profile(skills = skills, educations = listOf(education)).isExploitable)
    }
}
