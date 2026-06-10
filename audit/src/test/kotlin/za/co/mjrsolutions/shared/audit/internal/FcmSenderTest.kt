package za.co.mjrsolutions.shared.audit.internal

import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Robolectric: FcmSender + Crash use android.util.Log, which plain JUnit does not stub.
@RunWith(RobolectricTestRunner::class)
class FcmSenderTest {

    private lateinit var server: MockWebServer
    private val cleared = mutableListOf<String>()
    private val message = AuditMessage("T", "B", "/path", 1L, "App")

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun sender() = FcmSender(
        fcmUrl = server.url("/v1/projects/agrihost-7970a/messages:send").toString(),
        onStaleToken = { userId -> cleared.add(userId) }
    )

    @Test
    fun `posts fcm v1 body with bearer header`() {
        server.enqueue(MockResponse().setResponseCode(200))
        sender().send("oauth-token", AdminToken("u1", "tok-1"), message)

        val recorded = server.takeRequest()
        assertEquals("Bearer oauth-token", recorded.getHeader("Authorization"))
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))
        val msg = JsonParser.parseString(recorded.body.readUtf8())
            .asJsonObject.getAsJsonObject("message")
        assertEquals("tok-1", msg.get("token").asString)
        assertEquals("T", msg.getAsJsonObject("notification").get("title").asString)
        assertEquals("B", msg.getAsJsonObject("notification").get("body").asString)
        assertEquals("/path", msg.getAsJsonObject("data").get("path").asString)
    }

    @Test
    fun `404 clears stale token`() {
        server.enqueue(MockResponse().setResponseCode(404))
        sender().send("t", AdminToken("u1", "tok-1"), message)
        assertEquals(listOf("u1"), cleared)
    }

    @Test
    fun `410 clears stale token`() {
        server.enqueue(MockResponse().setResponseCode(410))
        sender().send("t", AdminToken("u2", "tok-2"), message)
        assertEquals(listOf("u2"), cleared)
    }

    @Test
    fun `500 does not clear and does not throw`() {
        server.enqueue(MockResponse().setResponseCode(500))
        sender().send("t", AdminToken("u3", "tok-3"), message)
        assertTrue(cleared.isEmpty())
    }
}
