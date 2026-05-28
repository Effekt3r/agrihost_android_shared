package za.co.mjrsolutions.shared.app_config

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog

object AppUpdateUi {

    /**
     * Show a non-cancellable update dialog. The positive button opens Play Store for [packageName].
     */
    @JvmStatic
    @JvmOverloads
    fun showForceUpdateDialog(
        activity: Activity,
        title: String,
        message: String,
        updateButtonText: String,
        packageName: String = activity.packageName,
        cancellable: Boolean = false
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(cancellable)
            .setPositiveButton(updateButtonText) { _, _ ->
                openPlayStore(activity, packageName)
            }
            .show()
    }

    /**
     * Open Google Play with the given package, falling back to the web URL if Play Store isn't installed.
     */
    @JvmStatic
    fun openPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
