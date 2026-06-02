package za.co.mjrsolutions.shared.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import za.co.mjrsolutions.shared.network.log.NetworkLogRecord
import za.co.mjrsolutions.shared.network.log.NetworkLogSink
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoggingInterceptorTest {

    private lateinit var server: MockWebServer

    private class CapturingSink : NetworkLogSink {
        val calls = mutableListOf<NetworkLogRecord>()
        val errors = mutableListOf<Pair<NetworkLogRecord, Throwable>>()
        override fun onCall(record: NetworkLogRecord) { calls.add(record) }
        override fun onError(record: NetworkLogRecord, cause: Throwable) { errors.add(record to cause) }
    }

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client(sink: NetworkLogSink, debug: Boolean): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(LoggingInterceptor(sink) { debug })
            .build()

    @Test
    fun `successful call is logged with code and correlation id`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val sink = CapturingSink()
        client(sink, debug = false).newCall(
            Request.Builder().url(server.url("/x")).build()
        ).execute().close()

        assertEquals(1, sink.calls.size)
        val rec = sink.calls.first()
        assertEquals(200, rec.code)
        assertEquals("GET", rec.method)
        assertTrue(rec.correlationId.isNotBlank())
    }

    @Test
    fun `debug on captures redacted headers`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val sink = CapturingSink()
        client(sink, debug = true).newCall(
            Request.Builder().url(server.url("/x"))
                .header("Authorization", "Bearer secret")
                .header("Accept", "application/json")
                .build()
        ).execute().close()

        val headers = sink.calls.first().redactedHeaders
        assertEquals(SensitiveData.REDACTED, headers["Authorization"])
        assertEquals("application/json", headers["Accept"])
    }

    @Test
    fun `debug off does not capture headers`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val sink = CapturingSink()
        client(sink, debug = false).newCall(
            Request.Builder().url(server.url("/x")).header("Authorization", "Bearer s").build()
        ).execute().close()

        assertTrue(sink.calls.first().redactedHeaders.isEmpty())
    }

    @Test
    fun `connection failure is logged as error and rethrown`() {
        server.shutdown() // force a connection failure
        val sink = CapturingSink()
        var threw = false
        try {
            client(sink, debug = false).newCall(
                Request.Builder().url("http://localhost:1/x").build()
            ).execute().close()
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
        assertEquals(1, sink.errors.size)
        assertNotNull(sink.errors.first().second)
    }
}
