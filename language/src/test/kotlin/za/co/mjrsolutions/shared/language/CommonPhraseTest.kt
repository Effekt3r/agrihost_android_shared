package za.co.mjrsolutions.shared.language

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommonPhraseTest {

    @Test
    fun `key returns enum name`() {
        assertEquals("YES", CommonPhrase.YES.key())
        assertEquals("CHANGE_LANGUAGE", CommonPhrase.CHANGE_LANGUAGE.key())
    }

    @Test
    fun `implements LangKey`() {
        val key: LangKey = CommonPhrase.OK
        assertEquals("OK", key.key())
    }

    @Test
    fun `expected entries are present`() {
        val names = CommonPhrase.values().map { it.name }.toSet()
        listOf("YES", "NO", "OK", "CANCEL", "CHANGE_LANGUAGE", "LOADING", "ERROR")
            .forEach { assertTrue(names.contains(it), "Missing: $it") }
    }
}
