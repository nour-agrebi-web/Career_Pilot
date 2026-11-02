package tn.careerpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    @Test
    fun `seuil de 50 a 90 et au moins un type`() {
        assertTrue(Validators.preferences(Preferences()).isEmpty())
        assertEquals(1, Validators.preferences(Preferences(threshold = 95)).size)
        assertEquals(1, Validators.preferences(Preferences(threshold = 45)).size)
        assertEquals(1, Validators.preferences(Preferences(jobTypes = emptySet())).size)
    }

    @Test
    fun `fichier CV`() {
        assertNull(Validators.cvFile("cv.PDF", 1000))
        assertNull(Validators.cvFile("cv.docx", 1000))
        assertNotNull(Validators.cvFile("cv.png", 1000))
        assertNotNull(Validators.cvFile("cv.pdf", 5L * 1024 * 1024 + 1))
        assertNotNull(Validators.cvFile("cv.pdf", 0))
    }

    @Test
    fun `competence en double`() {
        val skills = listOf(Skill("1", "Kotlin"))
        assertNotNull(Validators.skill(" kotlin ", skills))
        assertNull(Validators.skill("Kotlin", skills, ignoreId = "1"))
        assertNotNull(Validators.skill("", skills))
    }

    @Test
    fun `periode`() {
        assertNull(Validators.period("2025-07-01", null))
        assertNull(Validators.period("2025-07-01", "2025-08-31"))
        assertNotNull(Validators.period("2025-08-31", "2025-07-01"))
        assertNotNull(Validators.period("01/07/2025", null))
    }

    @Test
    fun `localisation en double`() {
        assertNotNull(Validators.location("tunis", listOf(Location("Tunis"))))
        assertNull(Validators.location("Sousse", listOf(Location("Tunis"))))
    }
}
