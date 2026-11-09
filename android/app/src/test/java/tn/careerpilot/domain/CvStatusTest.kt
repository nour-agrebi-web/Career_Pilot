package tn.careerpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CvStatusTest {
    @Test
    fun `analyse en cours`() {
        assertTrue(CvStatus.isRunning(CvStatus.UPLOADED))
        assertTrue(CvStatus.isRunning(CvStatus.PROCESSING))
        assertFalse(CvStatus.isRunning(CvStatus.PROCESSED))
        assertFalse(CvStatus.isRunning(CvStatus.ERROR))
    }

    @Test
    fun `sections manquantes en francais`() {
        assertNull(CvStatus.missingLabel(emptyList()))
        assertEquals("compétences", CvStatus.missingLabel(listOf("skills")))
        assertEquals("formations et expériences", CvStatus.missingLabel(listOf("educations", "experiences")))
        assertEquals("formations, expériences et compétences", CvStatus.missingLabel(listOf("educations", "experiences", "skills")))
    }

    @Test
    fun `messages d erreur`() {
        assertEquals("Ce fichier est endommagé ou illisible.", CvStatus.errorMessage(CvStatus.UNREADABLE, null))
        assertEquals("Erreur du serveur", CvStatus.errorMessage("AI_FAILED", "Erreur du serveur"))
    }
}
