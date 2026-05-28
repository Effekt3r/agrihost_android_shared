package za.co.mjrsolutions.shared.location

import android.location.Location
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class FixFilterTest {

    private val cfg = LocationConfig(
        maxFixAgeMs = 10_000,
        maxAccuracyMeters = 50.0f
    )

    private fun mkLocation(
        lat: Double = -25.7479,
        lng: Double = 28.2293,
        accuracy: Float = 10f,
        timeMs: Long = 1_000_000L,
        provider: String = "gps"
    ): Location {
        val loc = Location(provider)
        loc.latitude = lat
        loc.longitude = lng
        loc.accuracy = accuracy
        loc.time = timeMs
        return loc
    }

    @Test
    fun `recent accurate fix is fresh and accurate`() {
        val now = 1_005_000L
        val loc = mkLocation(timeMs = 1_000_000L, accuracy = 10f)
        val fix = FixFilter.toAgriFix(loc, cfg, now)
        assertTrue(fix.isFresh)
        assertTrue(fix.isAccurate)
        assertEquals(5_000L, fix.ageMs)
    }

    @Test
    fun `old fix flagged not fresh`() {
        val now = 1_020_000L
        val loc = mkLocation(timeMs = 1_000_000L, accuracy = 10f)
        val fix = FixFilter.toAgriFix(loc, cfg, now)
        assertFalse(fix.isFresh)
        assertTrue(fix.isAccurate)
    }

    @Test
    fun `inaccurate fix flagged not accurate`() {
        val now = 1_005_000L
        val loc = mkLocation(timeMs = 1_000_000L, accuracy = 200f)
        val fix = FixFilter.toAgriFix(loc, cfg, now)
        assertTrue(fix.isFresh)
        assertFalse(fix.isAccurate)
    }

    @Test
    fun `boundary at exactly maxFixAgeMs is fresh`() {
        val now = 1_010_000L
        val loc = mkLocation(timeMs = 1_000_000L, accuracy = 10f)
        val fix = FixFilter.toAgriFix(loc, cfg, now)
        assertTrue(fix.isFresh)
    }

    @Test
    fun `boundary at exactly maxAccuracyMeters is accurate`() {
        val now = 1_005_000L
        val loc = mkLocation(timeMs = 1_000_000L, accuracy = 50f)
        val fix = FixFilter.toAgriFix(loc, cfg, now)
        assertTrue(fix.isAccurate)
    }

    @Test
    fun `provider is propagated`() {
        val loc = mkLocation(provider = "gps")
        val fix = FixFilter.toAgriFix(loc, cfg, 1_000_000L)
        assertEquals("gps", fix.provider)
    }

    @Test
    fun `lat lng accuracy are propagated`() {
        val loc = mkLocation(lat = -25.0, lng = 28.5, accuracy = 12.5f)
        val fix = FixFilter.toAgriFix(loc, cfg, 1_000_000L)
        assertEquals(-25.0, fix.latitude, 1e-9)
        assertEquals(28.5, fix.longitude, 1e-9)
        assertEquals(12.5f, fix.accuracyMeters)
    }
}
