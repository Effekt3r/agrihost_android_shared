package za.co.mjrsolutions.shared.network

import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorClassifierTest {

    @Test
    fun `unknown host is Offline`() {
        val r = ErrorClassifier.fromException(UnknownHostException("no dns"))
        assertEquals(NetworkResult.Offline, r)
    }

    @Test
    fun `connection refused is Offline`() {
        val r = ErrorClassifier.fromException(ConnectException("refused"))
        assertEquals(NetworkResult.Offline, r)
    }

    @Test
    fun `socket timeout is NetworkError`() {
        val r = ErrorClassifier.fromException(SocketTimeoutException("timeout"))
        assertTrue(r is NetworkResult.NetworkError)
    }

    @Test
    fun `generic io is NetworkError`() {
        val r = ErrorClassifier.fromException(IOException("reset"))
        assertTrue(r is NetworkResult.NetworkError)
    }

    @Test
    fun `non-2xx maps to ServerError preserving code and body`() {
        val r = ErrorClassifier.fromResponse(503, "maintenance")
        assertTrue(r is NetworkResult.ServerError)
        r as NetworkResult.ServerError
        assertEquals(503, r.code)
        assertEquals("maintenance", r.body)
    }
}
