package za.co.mjrsolutions.shared.location

interface LocationListener {
    fun onLocationUpdate(fix: AgriFix)
}
