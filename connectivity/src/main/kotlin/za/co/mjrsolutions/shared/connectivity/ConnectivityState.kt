package za.co.mjrsolutions.shared.connectivity

import za.co.mjrsolutions.shared.language.CommonPhrase
import za.co.mjrsolutions.shared.language.LangKey

/**
 * WHY the server cannot be reached, not just whether it can, so the message the assessor
 * sees names the actual problem instead of always blaming the bediener.
 */
enum class ConnectivityState {
    /** No probe has resolved yet. Treated as "not offline" so nothing is ever blocked on an unknown. */
    UNKNOWN,

    /** The server answered. */
    ONLINE,

    /** No active network: wifi and mobile data are both off, or wifi is on but not associated. */
    NO_TRANSPORT,

    /** Attached to a network, but it has no working route to the internet (or a captive portal). */
    NO_INTERNET,

    /** The internet works, but our base URL did not answer. */
    SERVER_UNREACHABLE;

    /** True while the server cannot be reached. [UNKNOWN] deliberately does not count. */
    val isOffline: Boolean
        get() = this != ONLINE && this != UNKNOWN

    /** True for the states the user can fix on the device, so a banner can offer wifi settings. */
    val isDeviceSideProblem: Boolean
        get() = this == NO_TRANSPORT || this == NO_INTERNET

    /** The one message per reason, shared by a banner and a blocked-action dialog. Null when online. */
    val phrase: LangKey?
        get() = when (this) {
            NO_TRANSPORT -> CommonPhrase.GEEN_NETWERK_VERBINDING
            NO_INTERNET -> CommonPhrase.GEEN_INTERNET_TOEGANG
            SERVER_UNREACHABLE -> CommonPhrase.GEEN_VERBINDING_MET_BEDIENER
            ONLINE, UNKNOWN -> null
        }
}
