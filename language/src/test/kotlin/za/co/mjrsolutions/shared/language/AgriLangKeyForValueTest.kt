package za.co.mjrsolutions.shared.language

import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgriLangKeyForValueTest {

    private enum class Key : LangKey {
        JA, NEE, STOOR;

        override fun key(): String = name
    }

    @Before
    fun loadDictionary() {
        Dictionary.replace(mapOf("JA" to "Ja", "NEE" to "Nee", "STOOR" to "Stoor"), "af")
    }

    @After
    fun clearDictionary() {
        Dictionary.replace(emptyMap(), "af")
    }

    @Test
    fun `returns the key whose translation equals the value`() {
        assertEquals("NEE", AgriLang.keyForValue(Key.values(), "Nee"))
    }

    @Test
    fun `matches ignoring case`() {
        assertEquals("STOOR", AgriLang.keyForValue(Key.values(), "STOOR"))
        assertEquals("JA", AgriLang.keyForValue(Key.values(), "ja"))
    }

    @Test
    fun `returns null instead of throwing when no translation matches`() {
        assertNull(AgriLang.keyForValue(Key.values(), "Kanselleer"))
        assertNull(AgriLang.keyForValue(Key.values(), ""))
    }

    @Test
    fun `returns null for a null value`() {
        assertNull(AgriLang.keyForValue(Key.values(), null))
    }

    @Test
    fun `an untranslated key resolves to itself`() {
        // getString falls back to the key name when the dictionary has no entry, so a raw key
        // round-trips. hael relies on this when the dictionary is not loaded yet.
        Dictionary.replace(emptyMap(), "af")
        assertEquals("JA", AgriLang.keyForValue(Key.values(), "JA"))
    }
}
