package za.co.mjrsolutions.shared.connectivity

/** The seam [ConnectivityWatcher] probes through. [ConnectivityProbe] is the real one. */
interface ConnectivityProber {

    /** Background thread only: the real implementation opens sockets. */
    fun probe(baseUrl: String): ConnectivityState

    /** Drop any cached "does the internet work" verdict; the next probe re-checks. */
    fun invalidateInternetVerdict()
}
