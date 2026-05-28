package za.co.mjrsolutions.shared.location

/**
 * Callback delivered by every gated AgriLocation entry point. Exactly one method
 * is invoked on the main thread.
 *
 *  - onReady           — permission granted, fused client armed and producing fixes.
 *  - onPermissionDenied — permission not granted; permanent=true when the user
 *                        selected "Don't ask again" or the dialog was bypassed via
 *                        the Settings-redirect variant.
 *  - onLocationDisabled — permission granted but device location services are off
 *                        (user must enable in system Settings).
 */
interface LocationGate {
    fun onReady()
    fun onPermissionDenied(permanent: Boolean)
    fun onLocationDisabled()
}
