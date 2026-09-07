package za.co.mjrsolutions.shared.debug_tools

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * DEBUG-ONLY. Installs the "Clear app data" shortcut that appears when the launcher icon
 * is long-pressed.
 *
 * This is a ContentProvider purely to get a startup hook: the framework creates providers
 * before any activity, so the shortcut registers itself with no call from the app. It never
 * serves data - every query method is a no-op.
 *
 * The shortcut is DYNAMIC rather than a static res/xml/shortcuts.xml entry because a static
 * shortcut's intent needs android:targetPackage, which differs per flavor, and manifest
 * placeholders are not expanded inside res/xml. Several flavors are routinely installed on
 * the same test tablet, so a shared implicit action would also raise a disambiguation
 * dialog. Building the Intent here with an explicit component sidesteps both problems.
 *
 * Shortcuts need API 25; ShortcutManagerCompat no-ops below that, so older devices simply
 * do not show it.
 */
class DebugShortcutInstaller : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return true
        try {
            // A shortcut intent must carry an action or the launcher rejects it.
            val intent = Intent(context, ClearAppDataActivity::class.java).setAction(Intent.ACTION_VIEW)

            val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel(context.getString(R.string.agridebug_shortcut_clear_short))
                .setLongLabel(context.getString(R.string.agridebug_shortcut_clear_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_agridebug_clear_data))
                .setIntent(intent)
                .build()

            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut))
        } catch (e: Exception) {
            // A dev convenience must never take the app down on a launcher that handles
            // shortcuts differently.
            Log.w(TAG, "Could not install the debug shortcut", e)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val SHORTCUT_ID = "debug_clear_app_data"
        private const val TAG = "DebugShortcutInstaller"
    }
}
