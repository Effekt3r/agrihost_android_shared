package za.co.mjrsolutions.shared.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import za.co.mjrsolutions.shared.network.Transport
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransportInterceptorTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `DIRECT leaves the url unchanged`() {
        server.enqueue(MockResponse().setResponseCode(200))
        OkHttpClient.Builder()
            .addInterceptor(TransportInterceptor(Transport.DIRECT, functionsBaseUrl = null))
            .build()
            .newCall(Request.Builder().url(server.url("/damage-reports")).build())
            .execute().close()

        assertEquals("/damage-reports", server.takeRequest().path)
    }

    @Test
    fun `FUNCTIONS_GATEWAY rewrites host and prefixes the original path`() {
        // server stands in for the Functions host; assert it received the rewritten request.
        server.enqueue(MockResponse().setResponseCode(200))
        val functionsBase = server.url("/api").toString().removeSuffix("/")
        OkHttpClient.Builder()
            .addInterceptor(TransportInterceptor(Transport.FUNCTIONS_GATEWAY, functionsBase))
            .build()
            // original target host is irrelevant — it must be rewritten to the functions host
            .newCall(Request.Builder().url("https://demo.agrihost.co.za/api/damage-reports").build())
            .execute().close()

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api"))
        assertTrue(recorded.path!!.endsWith("/damage-reports"))
    }

    @Test
    fun `FUNCTIONS_GATEWAY without configured url is a pass-through`() {
        server.enqueue(MockResponse().setResponseCode(200))
        OkHttpClient.Builder()
            .addInterceptor(TransportInterceptor(Transport.FUNCTIONS_GATEWAY, functionsBaseUrl = null))
            .build()
            .newCall(Request.Builder().url(server.url("/x")).build())
            .execute().close()

        assertEquals("/x", server.takeRequest().path)
    }
}
