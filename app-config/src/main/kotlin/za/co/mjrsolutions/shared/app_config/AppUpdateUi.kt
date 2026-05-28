package za.co.mjrsolutions.shared.app_config

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog

object AppUpdateUi {

    /**
     * Show an update dialog. The positive button opens Play Store for [packageName].
     * If [laterButtonText] is non-null, a dismissible "Later" button is shown.
     * Pass null to make the dialog non-cancellable (force update).
     */
    @JvmStatic
    @JvmOverloads
    fun showUpdateDialog(
        activity: Activity,
        title: String,
        message: String,
        updateButtonText: String,
        laterButtonText: String? = null,
        packageName: String = activity.packageName,
        onLater: Runnable? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val builder = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(laterButtonText != null)
            .setPositiveButton(updateButtonText) { _, _ ->
                openPlayStore(activity, packageName)
            }
        if (laterButtonText != null) {
            builder.setNegativeButton(laterButtonText) { dialog, _ ->
                dialog.dismiss()
                onLater?.run()
            }
        }
        builder.show()
    }

    /**
     * Backwards-compatible alias for [showUpdateDialog]. Always non-cancellable.
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
        showUpdateDialog(
            activity = activity,
            title = title,
            message = message,
            updateButtonText = updateButtonText,
            laterButtonText = null,
            packageName = packageName
        )
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
