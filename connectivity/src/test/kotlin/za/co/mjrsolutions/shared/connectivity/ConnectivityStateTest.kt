package za.co.mjrsolutions.shared.connectivity

import org.junit.Test
import za.co.mjrsolutions.shared.language.CommonPhrase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectivityStateTest {

    @Test
    fun `only a resolved non-online state counts as offline`() {
        assertFalse(ConnectivityState.UNKNOWN.isOffline, "UNKNOWN must never block an action")
        assertFalse(ConnectivityState.ONLINE.isOffline)
        assertTrue(ConnectivityState.NO_TRANSPORT.isOffline)
        assertTrue(ConnectivityState.NO_INTERNET.isOffline)
        assertTrue(ConnectivityState.SERVER_UNREACHABLE.isOffline)
    }

    @Test
    fun `the two problems the user can fix on the device are device-side`() {
        assertTrue(ConnectivityState.NO_TRANSPORT.isDeviceSideProblem)
        assertTrue(ConnectivityState.NO_INTERNET.isDeviceSideProblem)
        assertFalse(ConnectivityState.SERVER_UNREACHABLE.isDeviceSideProblem)
        assertFalse(ConnectivityState.ONLINE.isDeviceSideProblem)
        assertFalse(ConnectivityState.UNKNOWN.isDeviceSideProblem)
    }

    /**
     * Pins the state set. A new state added without classifying it in isOffline /
     * isDeviceSideProblem / phrase would silently default to "online and nothing is wrong".
     */
    @Test
    fun `the state set is pinned`() {
        assertEquals(5, ConnectivityState.values().size)
    }

    @Test
    fun `each offline reason has its own phrase and online states have none`() {
        assertEquals(CommonPhrase.GEEN_NETWERK_VERBINDING, ConnectivityState.NO_TRANSPORT.phrase)
        assertEquals(CommonPhrase.GEEN_INTERNET_TOEGANG, ConnectivityState.NO_INTERNET.phrase)
        assertEquals(CommonPhrase.GEEN_VERBINDING_MET_BEDIENER, ConnectivityState.SERVER_UNREACHABLE.phrase)
        assertNull(ConnectivityState.ONLINE.phrase)
        assertNull(ConnectivityState.UNKNOWN.phrase)
    }
}
