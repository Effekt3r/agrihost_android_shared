package za.co.mjrsolutions.shared.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client(headers: Map<String, String>) =
        OkHttpClient.Builder().addInterceptor(AuthInterceptor { headers }).build()

    @Test
    fun `provider headers are injected`() {
        server.enqueue(MockResponse().setResponseCode(200))
        client(mapOf("x-api-key" to "k", "Authorization" to "Bearer t"))
            .newCall(Request.Builder().url(server.url("/x")).build()).execute().close()

        val recorded = server.takeRequest()
        assertEquals("k", recorded.getHeader("x-api-key"))
        assertEquals("Bearer t", recorded.getHeader("Authorization"))
    }

    @Test
    fun `existing per-request header is not overwritten`() {
        server.enqueue(MockResponse().setResponseCode(200))
        client(mapOf("Authorization" to "Bearer provider"))
            .newCall(
                Request.Builder().url(server.url("/x"))
                    .header("Authorization", "Bearer explicit").build()
            ).execute().close()

        assertEquals("Bearer explicit", server.takeRequest().getHeader("Authorization"))
    }
}
