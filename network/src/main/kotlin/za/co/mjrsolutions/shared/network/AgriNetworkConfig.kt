package za.co.mjrsolutions.shared.network

import za.co.mjrsolutions.shared.network.log.CrashlyticsLogSink
import za.co.mjrsolutions.shared.network.log.NetworkLogSink

/**
 * Init-time configuration. The provider lambdas mirror AuthConfig.fcmTokenProvider and are
 * evaluated at request time, so module init ordering does not matter and :network needs no
 * compile dependency on :auth or :app-config.
 */
data class AgriNetworkConfig(
    val baseUrl: String,
    val transport: Transport = Transport.DIRECT,
    val functionsBaseUrl: String? = null,                       // used only by Phase H
    val authHeaderProvider: () -> Map<String, String>,          // wire to AgriAuth.getAuthHeaders()
    val logSink: NetworkLogSink = CrashlyticsLogSink,
    val debugLoggingProvider: () -> Boolean = { false },        // wire to AppConfig debugLoggingEnabled
    val syncIntervalMinutesProvider: () -> Long = { 15L },      // wire to AppConfig minSyncIntervalMinutes
    val connectTimeoutMs: Long = 30_000,
    val readTimeoutMs: Long = 60_000
)
