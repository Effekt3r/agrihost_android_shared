package za.co.mjrsolutions.shared.connectivity

import org.junit.Test
import kotlin.test.assertEquals

/** The ladder, with every rung faked: no sockets, no Android. */
class ConnectivityProbeTest {

    private val base = "https://dev.example.test/api"

    private var transport: () -> TransportStatus = { TransportStatus.VALIDATED }
    private var headAnswer: (String, Int) -> Boolean = { _, _ -> true }
    private val headCalls = mutableListOf<Pair<String, Int>>()
    private var now = 1_000_000L
    private val errors = mutableListOf<Throwable>()

    private val probe = ConnectivityProbe(
        transport = { transport() },
        head = { url, code -> headCalls += url to code; headAnswer(url, code) },
        clock = { now },
        onError = { errors += it },
    )

    @Test
    fun `no active network reads as no transport without opening a socket`() {
        transport = { TransportStatus.NONE }
        assertEquals(ConnectivityState.NO_TRANSPORT, probe.probe(base))
        assertEquals(0, headCalls.size)
    }

    @Test
    fun `an attached but unvalidated network reads as no internet without opening a socket`() {
        transport = { TransportStatus.ATTACHED_UNVALIDATED }
        assertEquals(ConnectivityState.NO_INTERNET, probe.probe(base))
        assertEquals(0, headCalls.size)
    }

    @Test
    fun `a validated network whose base url answers is online`() {
        assertEquals(ConnectivityState.ONLINE, probe.probe(base))
        assertEquals(listOf(base to -1), headCalls)
    }

    @Test
    fun `base url silent but the neutral check answers 204 means the server is unreachable`() {
        headAnswer = { url, _ -> url != base }
        assertEquals(ConnectivityState.SERVER_UNREACHABLE, probe.probe(base))
        assertEquals(listOf(base to -1, ConnectivityProbe.INTERNET_CHECK_URL to 204), headCalls)
    }

    @Test
    fun `base url silent and the neutral check silent means no internet`() {
        headAnswer = { _, _ -> false }
        assertEquals(ConnectivityState.NO_INTERNET, probe.probe(base))
    }

    @Test
    fun `the internet verdict is cached so a sustained outage costs one socket per tick`() {
        headAnswer = { url, _ -> url != base }
        probe.probe(base)
        now += 30_000L
        probe.probe(base)
        val neutralChecks = headCalls.count { it.first == ConnectivityProbe.INTERNET_CHECK_URL }
        assertEquals(1, neutralChecks)
        assertEquals(2, headCalls.count { it.first == base })
    }

    @Test
    fun `the cached verdict expires after the ttl`() {
        headAnswer = { url, _ -> url != base }
        probe.probe(base)
        now += ConnectivityProbe.INTERNET_VERDICT_TTL_MS + 1
        probe.probe(base)
        assertEquals(2, headCalls.count { it.first == ConnectivityProbe.INTERNET_CHECK_URL })
    }

    @Test
    fun `invalidating the verdict forces a fresh neutral check`() {
        headAnswer = { url, _ -> url != base }
        probe.probe(base)
        probe.invalidateInternetVerdict()
        probe.probe(base)
        assertEquals(2, headCalls.count { it.first == ConnectivityProbe.INTERNET_CHECK_URL })
    }

    @Test
    fun `a throwing transport check reads as no transport and is reported`() {
        transport = { throw IllegalStateException("connectivity service unavailable") }
        assertEquals(ConnectivityState.NO_TRANSPORT, probe.probe(base))
        assertEquals(1, errors.size)
        assertEquals(0, headCalls.size)
    }
}
