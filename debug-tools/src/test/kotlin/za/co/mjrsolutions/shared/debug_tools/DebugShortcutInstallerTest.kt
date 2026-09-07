package za.co.mjrsolutions.shared.debug_tools

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class DebugShortcutInstallerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `provider creation installs the clear-app-data launcher shortcut`() {
        // The authority is irrelevant here (the real one is ${applicationId}.debugshortcuts,
        // resolved when the consuming app merges the manifest); onCreate is what matters.
        Robolectric.setupContentProvider(DebugShortcutInstaller::class.java, "test.debugshortcuts")

        val shortcut = ShortcutManagerCompat.getDynamicShortcuts(context)
            .firstOrNull { it.id == DebugShortcutInstaller.SHORTCUT_ID }
        assertNotNull(shortcut, "shortcut not installed")
        assertEquals(ClearAppDataActivity::class.java.name, shortcut.intent.component?.className)
        assertEquals(context.getString(R.string.agridebug_shortcut_clear_short), shortcut.shortLabel.toString())
    }
}
