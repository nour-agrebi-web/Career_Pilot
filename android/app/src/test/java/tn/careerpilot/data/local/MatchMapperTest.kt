package tn.careerpilot.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tn.careerpilot.data.remote.BreakdownDto
import tn.careerpilot.data.remote.LocationDto
import tn.careerpilot.data.remote.MatchDto
import tn.careerpilot.data.remote.OfferDto

class MatchMapperTest {
    private fun dto(categories: List<String>?) = MatchDto(
        id = "m1", score = 80, breakdown = BreakdownDto(30, 25, 15, 10), reasons = null, missingSkills = null,
        status = "PENDING", createdAt = "2026-11-24T00:00:00Z",
        offer = OfferDto(
            id = "o1", source = "Keejob", title = "Développeur Kotlin", company = "Nexa",
            location = LocationDto("Tunis", "Tunisie"), type = "emploi", workMode = null, level = null,
            description = null, requiredSkills = null, publicationDate = null, url = null,
            category = categories?.firstOrNull(), categories = categories,
        ),
    )

    @Test
    fun `les categories passent par le cache`() {
        assertEquals(listOf("Développement", "IT"), dto(listOf("Développement", "IT")).toEntity().toDomain().offer.categories)
    }

    @Test
    fun `liste vide et categorie inconnue restent distinctes`() {
        assertEquals(emptyList<String>(), dto(emptyList()).toEntity().toDomain().offer.categories)
        assertNull(dto(null).toEntity().toDomain().offer.categories)
    }
}
