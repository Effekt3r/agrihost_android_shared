package za.co.mjrsolutions.shared.network

/**
 * Selects how a request reaches the backend.
 * - DIRECT: straight to {baseUrl}/path (Phase 1).
 * - FUNCTIONS_GATEWAY: routed through a Firebase Cloud Function (deferred — Phase H).
 *   The seam is implemented and tested in TransportInterceptor, but no app enables it yet.
 */
enum class Transport { DIRECT, FUNCTIONS_GATEWAY }
