package za.co.mjrsolutions.shared.location

interface FixCallback {
    fun onFix(fix: AgriFix)
    fun onTimeout(bestEffort: AgriFix?)
    fun onPermissionDenied()
    fun onLocationDisabled()
}
