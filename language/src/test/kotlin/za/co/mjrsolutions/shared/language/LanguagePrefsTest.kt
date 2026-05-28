package za.co.mjrsolutions.shared.language

import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class LanguagePrefsTest {

    @Before
    fun setUp() {
        LanguagePrefs.init(ApplicationProvider.getApplicationContext())
        LanguagePrefs.clear()
    }

    @Test
    fun `getSelected returns null when nothing saved`() {
        assertNull(LanguagePrefs.getSelected())
    }

    @Test
    fun `setSelected then getSelected round-trips`() {
        LanguagePrefs.setSelected("en")
        assertEquals("en", LanguagePrefs.getSelected())
    }

    @Test
    fun `clear removes the saved value`() {
        LanguagePrefs.setSelected("af")
        LanguagePrefs.clear()
        assertNull(LanguagePrefs.getSelected())
    }
}
