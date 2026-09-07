package za.co.mjrsolutions.shared.connectivity

/** What the OS says about the default network, before any socket is opened. */
internal enum class TransportStatus {
    /** No active network at all. */
    NONE,

    /** A network is attached but the OS has not validated a route to the internet. */
    ATTACHED_UNVALIDATED,

    /** The OS validated the network (its own captive-portal probe succeeded). */
    VALIDATED,
}
