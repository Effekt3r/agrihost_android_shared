package za.co.mjrsolutions.shared.permissions.internal

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import za.co.mjrsolutions.shared.language.AgriLang
import za.co.mjrsolutions.shared.permissions.PermissionType

internal object RationaleDialog {

    fun show(
        activity: FragmentActivity,
        type: PermissionType,
        titleKey: String,
        bodyKey: String,
        settingsVariant: Boolean,
        onContinue: () -> Unit,
        onCancel: () -> Unit
    ) {
        val title = AgriLang.getString(titleKey)
        val body = AgriLang.getString(bodyKey)
        val positiveLabel = AgriLang.getString(
            if (settingsVariant) "permission_open_settings" else "permission_continue"
        )
        val negativeLabel = AgriLang.getString("permission_cancel")

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(body)
            .setCancelable(false)
            .setPositiveButton(positiveLabel) { _, _ ->
                if (settingsVariant) {
                    onContinue()  // caller fires onPermissionDenied(permanent=true) BEFORE settings opens
                    SettingsLauncher.openAppSettings(activity)
                } else {
                    onContinue()
                }
            }
            .setNegativeButton(negativeLabel) { _, _ -> onCancel() }
            .show()
    }
}

internal object SettingsLauncher {
    fun openAppSettings(activity: FragmentActivity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
