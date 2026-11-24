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
}
