package tn.careerpilot.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import tn.careerpilot.data.local.MatchDao
import tn.careerpilot.data.local.MatchEntity
import tn.careerpilot.data.remote.ApiResult
import tn.careerpilot.data.remote.CareerPilotApi
import tn.careerpilot.domain.MatchStatus

/** Cache Room simulé en mémoire. */
private class FakeMatchDao : MatchDao() {
    val rows = MutableStateFlow<Map<String, MatchEntity>>(emptyMap())
    override fun observeAll(): Flow<List<MatchEntity>> = rows.map { it.values.sortedByDescending(MatchEntity::score) }
    override fun observe(id: String): Flow<MatchEntity?> = rows.map { it[id] }
    override suspend fun upsert(match: MatchEntity) { rows.value = rows.value + (match.id to match) }
    override suspend fun upsertAll(matches: List<MatchEntity>) { rows.value = rows.value + matches.associateBy { it.id } }
    override suspend fun updateStatus(id: String, status: String) {
        rows.value[id]?.let { rows.value = rows.value + (id to it.copy(status = status)) }
    }
    override suspend fun clear() { rows.value = emptyMap() }
}

class MatchRepositoryTest {
    private val server = MockWebServer()
    private val dao = FakeMatchDao()
    private lateinit var repository: MatchRepository

    private val matchJson = """
        {"id":"m1","score":86,"breakdown":{"semantic":35,"skills":23,"level":15,"preferences":13},
         "reasons":["Kotlin présent dans votre profil"],"missing_skills":["Docker"],"status":"PENDING",
         "created_at":"2026-10-08T10:00:00Z",
         "offer":{"id":"o1","source":"TanitJobs","title":"Stage PFE Android","company":"Nexa",
                  "location":{"city":"Tunis","country":"Tunisie"},"type":"stage","work_mode":"hybride",
                  "level":"stage","description":"MVVM","required_skills":["Kotlin","Docker"],
                  "publication_date":"2026-10-08T00:00:00Z","url":"https://example.com"}}
    """.trimIndent()

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CareerPilotApi::class.java)
        repository = MatchRepository(api, dao)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `refresh lance le matching puis remplit le cache`() = runTest {
        server.enqueue(MockResponse().setBody("""{"new_matches":1}"""))
        server.enqueue(MockResponse().setBody("[$matchJson]"))

        val result = repository.refresh(runMatching = true)

        assertEquals(ApiResult.Success(1), result)
        assertEquals("/matches/refresh", server.takeRequest().path)
        assertEquals("/matches?min_score=0", server.takeRequest().path)
        val cached = repository.matches.first().single()
        assertEquals(86, cached.score)
        assertEquals("Tunis", cached.offer.city)
        assertEquals(listOf("Docker"), cached.missingSkills)
    }

    @Test
    fun `profil non valide (409) affiche quand meme le cache`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Validez votre profil."}"""))
        server.enqueue(MockResponse().setBody("[]"))

        assertEquals(ApiResult.Success(0), repository.refresh(runMatching = true))
    }

    @Test
    fun `erreur serveur remontee avec le message de l API`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"Base indisponible"}"""))

        val result = repository.refresh(runMatching = false)

        assertEquals(ApiResult.Error("Base indisponible", 500), result)
    }

    @Test
    fun `masquer une offre envoie DELETE et met a jour le cache`() = runTest {
        server.enqueue(MockResponse().setBody("[$matchJson]"))
        repository.refresh(runMatching = false)
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(204))

        assertTrue(repository.hide("m1") is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/matches/m1", request.path)
        assertEquals(MatchStatus.REJECTED, repository.matches.first().single().status)
    }

    @Test
    fun `serveur injoignable`() = runTest {
        server.shutdown()
        val result = repository.refresh(runMatching = false)
        assertTrue(result is ApiResult.Error)
    }
}
