package za.co.mjrsolutions.shared.location

data class AgriFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMs: Long,
    val ageMs: Long,
    val provider: String?,
    val isFresh: Boolean,
    val isAccurate: Boolean
) {
    fun toDisplayString(): String = "$latitude,$longitude"
}
