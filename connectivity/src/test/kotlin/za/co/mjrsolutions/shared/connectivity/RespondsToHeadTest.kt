package za.co.mjrsolutions.shared.connectivity

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real HEAD rung against a local server. */
class RespondsToHeadTest {

    private val server = MockWebServer()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `any answer satisfies the base url check even a 405`() {
        server.enqueue(MockResponse().setResponseCode(405))
        assertTrue(ConnectivityProbe.respondsToHead(server.url("/api").toString(), -1))
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test
    fun `a bare 204 satisfies the neutral internet check`() {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(ConnectivityProbe.respondsToHead(server.url("/generate_204").toString(), 204))
    }

    @Test
    fun `a captive portal 200 fails the neutral internet check`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>login</html>"))
        assertFalse(ConnectivityProbe.respondsToHead(server.url("/generate_204").toString(), 204))
    }

    @Test
    fun `a redirect is the answer and is not followed`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/login"))
        server.enqueue(MockResponse().setResponseCode(200))
        assertFalse(ConnectivityProbe.respondsToHead(server.url("/generate_204").toString(), 204))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a refused connection reads as no answer`() {
        val dead = MockWebServer()
        dead.start()
        val url = dead.url("/api").toString()
        dead.shutdown()
        assertFalse(ConnectivityProbe.respondsToHead(url, -1))
    }
}
