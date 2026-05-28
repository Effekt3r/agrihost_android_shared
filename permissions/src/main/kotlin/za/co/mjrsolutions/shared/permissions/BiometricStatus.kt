package za.co.mjrsolutions.shared.permissions

/** Result of a biometric capability check (no enrollment / no hardware / available / etc.). */
enum class BiometricStatus {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NOT_ENROLLED,
    UNKNOWN_ERROR
}
