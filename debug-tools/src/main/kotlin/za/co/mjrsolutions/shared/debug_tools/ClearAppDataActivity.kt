package za.co.mjrsolutions.shared.debug_tools

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * DEBUG-ONLY. Confirms, then wipes this app exactly as Settings - Storage - Clear storage
 * would: database, every SharedPreferences file (sign-in token, biometric state,
 * granted-permission bookkeeping, section-completion flags), cached dictionaries under
 * filesDir, and all caches.
 *
 * [ActivityManager.clearApplicationUserData] is used in preference to deleting the known
 * stores one by one. Enumerating them by hand rots: the next feature that adds a preferences
 * file or a cache directory silently escapes the wipe and leaves stale state behind to debug.
 * This call is complete by definition because it removes the whole app data directory.
 *
 * It also kills the process, which is what makes it safe: an app's database helper is
 * typically a singleton holding an open SQLiteDatabase, and deleting the file from under a
 * live handle would leave a half-dead connection. The process dies with it instead, so the
 * next launch builds a fresh helper and recreates the schema.
 *
 * Runtime permissions are reset too, so the app re-prompts on the next launch. That is
 * expected, not a fault.
 */
class ClearAppDataActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this)
            .setTitle(R.string.agridebug_clear_dialog_title)
            .setMessage(R.string.agridebug_clear_dialog_message)
            .setCancelable(false)
            .setNegativeButton(R.string.agridebug_clear_dialog_cancel) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setPositiveButton(R.string.agridebug_clear_dialog_confirm) { _, _ -> clearEverything() }
            .show()
    }

    private fun clearEverything() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        val wipeStarted = try {
            activityManager?.clearApplicationUserData() ?: false
        } catch (e: Exception) {
            Log.w(TAG, "clearApplicationUserData threw", e)
            false
        }

        // On success the system kills the process, so nothing below runs. Only a refusal
        // (some OEM builds, or a device-policy restriction) gets here.
        if (!wipeStarted) {
            Toast.makeText(this, R.string.agridebug_clear_failed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private companion object {
        const val TAG = "ClearAppDataActivity"
    }
}
