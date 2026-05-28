package za.co.mjrsolutions.shared.language

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputLayout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ViewTextWalkerTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    // TextInputLayout requires a Material Components theme
    private val context: Context = ContextThemeWrapper(
        appContext,
        R.style.Theme_AgriLang_Base
    )

    @Before
    fun setUp() {
        Dictionary.replace(
            mapOf("HELLO" to "Hallo", "WORLD" to "Wêreld", "USERNAME" to "Gebruikersnaam"),
            "af"
        )
    }

    @Test
    fun `TextView with matching tag is translated`() {
        val tv = TextView(context)
        tv.tag = "HELLO"
        ViewTextWalker.setViewsText(tv)
        assertEquals("Hallo", tv.text.toString())
    }

    @Test
    fun `Button with matching tag is translated`() {
        val btn = Button(context)
        btn.tag = "WORLD"
        ViewTextWalker.setViewsText(btn)
        assertEquals("Wêreld", btn.text.toString())
    }

    @Test
    fun `EditText with matching tag is translated`() {
        val et = EditText(context)
        et.tag = "HELLO"
        ViewTextWalker.setViewsText(et)
        assertEquals("Hallo", et.text.toString())
    }

    @Test
    fun `TextInputLayout with matching tag has hint set`() {
        val til = TextInputLayout(context)
        til.tag = "USERNAME"
        ViewTextWalker.setViewsText(til)
        assertEquals("Gebruikersnaam", til.hint.toString())
    }

    @Test
    fun `unknown tag leaves view text empty`() {
        val tv = TextView(context)
        tv.tag = "UNKNOWN_KEY"
        ViewTextWalker.setViewsText(tv)
        assertEquals("", tv.text.toString())
    }

    @Test
    fun `recursive ViewGroup translates all children`() {
        val parent = LinearLayout(context)
        val child1 = TextView(context).apply { tag = "HELLO" }
        val child2 = TextView(context).apply { tag = "WORLD" }
        parent.addView(child1)
        parent.addView(child2)

        ViewTextWalker.setViewsText(parent)

        assertEquals("Hallo", child1.text.toString())
        assertEquals("Wêreld", child2.text.toString())
    }

    @Test
    fun `non-string tag is ignored`() {
        val tv = TextView(context)
        tv.tag = 42  // Integer, not String
        // Must not throw
        ViewTextWalker.setViewsText(tv)
        assertEquals("", tv.text.toString())
    }
}
