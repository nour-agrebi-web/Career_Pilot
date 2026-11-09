package tn.careerpilot.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import tn.careerpilot.data.local.CvDao
import tn.careerpilot.data.local.CvEntity
import tn.careerpilot.data.remote.ApiResult
import tn.careerpilot.data.remote.CareerPilotApi
import tn.careerpilot.domain.ApplyProfileResult
import tn.careerpilot.domain.CvStatus

/** Cache Room simulé en mémoire. */
private class FakeCvDao : CvDao() {
    val rows = MutableStateFlow<List<CvEntity>>(emptyList())
    override fun observeAll(): Flow<List<CvEntity>> = rows
    override suspend fun upsertAll(cvs: List<CvEntity>) { rows.value = rows.value + cvs }
    override suspend fun clear() { rows.value = emptyList() }
}

class CvRepositoryTest {
    private val server = MockWebServer()
    private val dao = FakeCvDao()
    private lateinit var repository: CvRepository

    private fun cvJson(status: String, errorCode: String? = null) = """
        {"id":"c1","filename":"cv.pdf","size":2048,"version":1,"status":"$status","active":true,
         "created_at":"2026-11-09T10:00:00Z","error_code":${errorCode?.let { "\"$it\"" } ?: "null"},"error":null,"applied":false}
    """.trimIndent()

    private fun analysisJson(status: String, draft: String = "null", errorCode: String? = null) = """
        {"id":"c1","status":"$status","error_code":${errorCode?.let { "\"$it\"" } ?: "null"},"error":null,
         "applied":false,"engine":"regles","extraction_method":"pdf","duration_ms":120,"draft":$draft,
         "missing_sections":["experiences"],"incomplete_items":[{"section":"languages","missing_fields":["level"]}]}
    """.trimIndent()

    private val draftJson = """
        {"domain":"Développement mobile","level":"stage","skills":[{"name":"Kotlin"},{"name":"Python"}],
         "experiences":[],"educations":[{"degree":"Licence DSI","institution":"ISET Radès","start_date":"2024-09-01","end_date":null}],
         "languages":[{"name":"Allemand","level":null}]}
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
        repository = CvRepository(api, dao, pollIntervalMs = 10, pollTimeoutMs = 100)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `suit l analyse jusqu au profil deduit`() = runTest {
        server.enqueue(MockResponse().setBody(analysisJson(CvStatus.PROCESSING)))
        server.enqueue(MockResponse().setBody(analysisJson(CvStatus.PROCESSED, draftJson)))
        server.enqueue(MockResponse().setBody("[${cvJson(CvStatus.PROCESSED)}]"))

        val result = repository.awaitAnalysis("c1")

        assertTrue(result is ApiResult.Success)
        val analysis = (result as ApiResult.Success).data
        assertEquals(CvStatus.PROCESSED, analysis.status)
        assertEquals(listOf("Kotlin", "Python"), analysis.skills)
        assertEquals(1, analysis.educationCount)
        assertEquals(listOf("experiences"), analysis.missingSections)
        assertEquals(1, analysis.incompleteCount)
        assertEquals("/cvs/c1/analysis", server.takeRequest().path)
        assertEquals("/cvs/c1/analysis", server.takeRequest().path)
        assertEquals("/cvs", server.takeRequest().path)
        assertEquals(CvStatus.PROCESSED, dao.rows.first().single().status)
    }

    @Test
    fun `cv illisible en erreur`() = runTest {
        server.enqueue(MockResponse().setBody(analysisJson(CvStatus.ERROR, errorCode = CvStatus.UNREADABLE)))
        server.enqueue(MockResponse().setBody("[${cvJson(CvStatus.ERROR, CvStatus.UNREADABLE)}]"))

        val analysis = (repository.awaitAnalysis("c1") as ApiResult.Success).data

        assertEquals(CvStatus.UNREADABLE, analysis.errorCode)
        assertEquals(CvStatus.UNREADABLE, dao.rows.first().single().errorCode)
    }

    @Test
    fun `analyse trop longue`() = runTest {
        repeat(20) { server.enqueue(MockResponse().setBody(analysisJson(CvStatus.PROCESSING))) }

        assertTrue(repository.awaitAnalysis("c1") is ApiResult.Error)
    }

    @Test
    fun `quota atteint affiche le message de l API`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"detail":"Quota atteint : 5 opérations IA par jour."}"""))

        val result = repository.upload(PickedFile("cv.pdf", "application/pdf", ByteArray(10)))

        assertEquals(ApiResult.Error("Quota atteint : 5 opérations IA par jour.", 429), result)
    }

    @Test
    fun `utiliser le profil deduit`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"profile":{"id":"p1","domain":"Développement mobile","level":"stage","validated":false,
             "skills":[{"id":"s1","name":"Kotlin"}],"experiences":[],"educations":[],"languages":[]},
             "missing_sections":["educations","experiences"],"incomplete_items":[]}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("[${cvJson(CvStatus.PROCESSED)}]"))

        val result = repository.applyProfile("c1")

        assertEquals(ApiResult.Success(ApplyProfileResult(listOf("educations", "experiences"), 0)), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/cvs/c1/apply-profile", request.path)
    }
}
