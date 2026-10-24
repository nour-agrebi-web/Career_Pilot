package tn.careerpilot.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {

    private class MemoryTokenStore : TokenStore {
        override var accessToken: String? = null
        override var refreshToken: String? = null
        override fun save(accessToken: String, refreshToken: String) {
            this.accessToken = accessToken
            this.refreshToken = refreshToken
        }
        override fun clear() {
            accessToken = null
            refreshToken = null
        }
    }

    private val server = MockWebServer()
    private val tokens = MemoryTokenStore()
    private var expiredCount = 0

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun api() = RetrofitInstance.createApi(server.url("/").toString(), tokens) { expiredCount++ }

    private val meJson = """{"id":"1","email":"nour@example.com","language":"fr","role":"candidate"}"""

    @Test
    fun `un 401 declenche un refresh puis rejoue la requete`() = runBlocking {
        tokens.save("ancien", "refresh-1")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"access_token":"nouveau","refresh_token":"refresh-2"}"""))
        server.enqueue(MockResponse().setBody(meJson))

        val user = api().me()

        assertEquals("nour@example.com", user.email)
        assertEquals("Bearer ancien", server.takeRequest().getHeader("Authorization"))
        assertEquals("/auth/refresh", server.takeRequest().path)
        assertEquals("Bearer nouveau", server.takeRequest().getHeader("Authorization"))
        assertEquals("refresh-2", tokens.refreshToken)
        assertEquals(0, expiredCount)
    }

    @Test
    fun `un refresh refuse vide la session`() = runBlocking {
        tokens.save("ancien", "refresh-revoque")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Session expirée, reconnectez-vous."}"""))

        val error = runCatching { api().me() }.exceptionOrNull()

        assertEquals(true, error != null)
        assertNull(tokens.refreshToken)
        assertEquals(1, expiredCount)
    }

    @Test
    fun `le message d erreur vient du champ detail de l API`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Un compte existe déjà avec cet e-mail."}"""))

        val error = runCatching { api().register(RegisterRequest("a@b.com", "motdepasse123", "fr", true)) }.exceptionOrNull()

        assertEquals("Un compte existe déjà avec cet e-mail.", error?.toUserMessage())
    }
}
