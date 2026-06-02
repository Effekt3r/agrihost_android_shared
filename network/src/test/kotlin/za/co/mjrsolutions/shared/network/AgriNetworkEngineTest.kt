package za.co.mjrsolutions.shared.network

import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.network.log.NoopLogSink
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AgriNetworkEngineTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        AgriNetwork.init(
            ApplicationProvider.getApplicationContext(),
            AgriNetworkConfig(
                baseUrl = server.url("/api").toString(),
                authHeaderProvider = { mapOf("x-api-key" to "k") },
                logSink = NoopLogSink
            )
        )
    }

    @After fun tearDown() { server.shutdown() }

    @Test
    fun `GET success returns body and sends auth header`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val result = AgriNetwork.execute(AgriRequest(HttpMethod.GET, "/farms"))
        assertTrue(result is NetworkResult.Success)
        assertEquals("""{"ok":true}""", (result as NetworkResult.Success).data)
        assertEquals("k", server.takeRequest().getHeader("x-api-key"))
    }

    @Test
    fun `POST body is sent to the joined path`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("saved"))
        AgriNetwork.execute(AgriRequest(HttpMethod.POST, "/damage-reports", bodyJson = """{"a":1}"""))
        val recorded = server.takeRequest()
        assertEquals("/api/damage-reports", recorded.path)
        assertEquals("""{"a":1}""", recorded.body.readUtf8())
    }

    @Test
    fun `non-2xx is a ServerError`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val result = AgriNetwork.execute(AgriRequest(HttpMethod.GET, "/farms"))
        assertTrue(result is NetworkResult.ServerError)
        assertEquals(500, (result as NetworkResult.ServerError).code)
    }

    @Test
    fun `unreachable host is Offline`() {
        server.shutdown()
        val result = AgriNetwork.execute(AgriRequest(HttpMethod.GET, "/farms"))
        assertTrue(result is NetworkResult.Offline || result is NetworkResult.NetworkError)
    }
}
