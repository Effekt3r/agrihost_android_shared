package za.co.mjrsolutions.shared.fcm_inbox

import org.junit.Test
import kotlin.test.assertEquals

class BrandUtilTest {

    @Test
    fun `every fixture row maps as expected`() {
        val csv = javaClass.classLoader!!
            .getResourceAsStream("flavor-brand-fixtures.csv")!!
            .bufferedReader()
            .readLines()
            .drop(1)                           // header
            .filter { it.isNotBlank() }

        for (line in csv) {
            val (flavor, expectedBrand) = line.split(',')
            val actual = BrandUtil.brandForFlavor(flavor)
            assertEquals(expectedBrand, actual, "flavor=$flavor")
        }
    }

    @Test
    fun `unknown flavor returns the input uppercased`() {
        assertEquals("WHATEVER", BrandUtil.brandForFlavor("whatever"))
    }
}
