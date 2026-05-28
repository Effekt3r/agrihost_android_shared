package za.co.mjrsolutions.shared.location

import android.location.Location

internal object FixFilter {

    fun toAgriFix(location: Location, config: LocationConfig, nowEpochMs: Long): AgriFix {
        val age = nowEpochMs - location.time
        val ageMs = if (age < 0) 0 else age
        val isFresh = ageMs <= config.maxFixAgeMs
        val isAccurate = location.hasAccuracy() && location.accuracy <= config.maxAccuracyMeters
        return AgriFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
            capturedAtEpochMs = location.time,
            ageMs = ageMs,
            provider = location.provider,
            isFresh = isFresh,
            isAccurate = isAccurate
        )
    }
}
