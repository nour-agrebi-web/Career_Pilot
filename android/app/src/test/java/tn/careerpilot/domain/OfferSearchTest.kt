package tn.careerpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferSearchTest {
    private val litteraire = offer(title = "Traducteur littéraire", skills = listOf("Rédaction"),
        description = "Traduction d'ouvrages littéraires, qualité éditoriale.")
    private val support = offer(title = "Technicien support IT", skills = listOf("Windows"),
        description = "Assistance aux utilisateurs.")
    private val dataScientist = offer(title = "Data Scientist junior", skills = listOf("Python", "Pandas"),
        description = "Conception de modèles de machine learning pour la prévision des ventes.")
    private val ingenieurIa = offer(title = "Ingénieur IA", skills = listOf("Python"),
        description = "Déploiement de modèles.")
    private val android = offer(title = "Développeur Android", skills = listOf("Kotlin"),
        description = "Application mobile.")
    private val mecanicien = offer(title = "Technicien de maintenance", skills = listOf("Soudure"),
        description = "Maintenance industrielle et réglages électromécaniques.")
    private val anglais = offer(title = "Sales assistant", skills = listOf("Excel"),
        description = "It is a great opportunity, j'ai dit oui.")
    private val all = listOf(litteraire, support, dataScientist, ingenieurIa, android, mecanicien, anglais)

    private fun search(query: String) = all.filter { OfferSearch.matches(it, query) }.map { it.title }

    @Test
    fun `IT ne trouve plus litteraire mais trouve les offres informatiques`() {
        val found = search("IT")
        assertFalse(litteraire.title in found)
        assertFalse(anglais.title in found)
        assertTrue(support.title in found)
        assertTrue(android.title in found)
        assertTrue(dataScientist.title in found)
        assertFalse(mecanicien.title in found)
    }

    @Test
    fun `la recherche ignore la casse`() {
        assertEquals(search("IT"), search("it"))
        assertEquals(search("IA"), search("ia"))
    }

    @Test
    fun `IA trouve le mot IA et les offres d IA sans le mot`() {
        val found = search("IA")
        assertTrue(ingenieurIa.title in found)
        // « IA » n'est pas dans l'offre, mais elle parle de machine learning.
        assertTrue(dataScientist.title in found)
        assertFalse(android.title in found)
        assertFalse(litteraire.title in found)
        // « j'ai » n'est pas « AI ».
        assertFalse(anglais.title in found)
    }

    @Test
    fun `mecanique trouve les offres du domaine meme sans accent`() {
        assertEquals(listOf(mecanicien.title), search("mécanique"))
        assertEquals(listOf(mecanicien.title), search("mecanique"))
    }

    @Test
    fun `developpement trouve les offres de developpeur`() {
        val found = search("développement")
        assertTrue(android.title in found)
        assertFalse(mecanicien.title in found)
        assertFalse(litteraire.title in found)
    }

    @Test
    fun `mot entier ou debut de mot de 4 lettres et plus`() {
        assertEquals(listOf(litteraire.title), search("litteraire"))
        assertEquals(listOf(android.title), search("androi"))
        assertTrue(search("tra").isEmpty())
        assertTrue(search("").size == all.size)
    }

    @Test
    fun `plusieurs mots doivent tous correspondre`() {
        assertEquals(listOf(android.title), search("développeur kotlin"))
        assertTrue(search("développeur soudure").isEmpty())
    }

    // --- Catégorie enregistrée par le scraper ---

    @Test
    fun `la categorie enregistree prime sur les mots-cles`() {
        // Le texte parle de Python, mais le site range l'offre en Finance.
        val analyste = offer(title = "Analyste financier", skills = listOf("Python"),
            description = "Modèles Excel et scripts Python.", categories = listOf("Finance"))
        assertFalse(OfferSearch.matches(analyste, "développement"))
        assertTrue(OfferSearch.matches(analyste, "finance"))
        assertTrue(OfferSearch.matches(analyste, "Comptabilité"))
        // Rangée en IA par le site, trouvée même si le texte ne le dit pas.
        val rd = offer(title = "Ingénieur R&D", skills = emptyList(), description = "Projet innovant.",
            categories = listOf("IA", "IT"))
        assertTrue(OfferSearch.matches(rd, "IA"))
        assertTrue(OfferSearch.matches(rd, "informatique"))
        assertFalse(OfferSearch.matches(rd, "mécanique"))
    }

    @Test
    fun `categorie vide - seul le mot ecrit dans l'offre compte`() {
        val conseiller = offer(title = "Conseiller mécanique auto", skills = emptyList(),
            description = "Accueil clients.", categories = emptyList())
        assertTrue(OfferSearch.matches(conseiller, "mécanique"))
        assertFalse(OfferSearch.matches(conseiller, "IT"))
    }

    @Test
    fun `sans categorie connue - repli sur les mots-cles`() {
        assertEquals(null, dataScientist.categories)
        assertTrue(OfferSearch.matches(dataScientist, "IA"))
    }
}
