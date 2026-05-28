package za.co.mjrsolutions.shared.location

data class LocationConfig(
    val maxFixAgeMs: Long = 10_000,
    val maxAccuracyMeters: Float = 50.0f,
    val freshFixTimeoutMs: Long = 15_000,
    val continuousIntervalMs: Long = 5_000,
    val continuousMinUpdateIntervalMs: Long = 2_000
)
