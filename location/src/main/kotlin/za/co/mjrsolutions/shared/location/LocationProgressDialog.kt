package za.co.mjrsolutions.shared.location

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog

internal class LocationProgressDialog(private val activity: Activity) {

    private var dialog: Dialog? = null

    fun show(title: String, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val b = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
        dialog = b.create().also { it.show() }
    }

    fun dismiss() {
        try { dialog?.dismiss() } catch (_: Exception) {}
        dialog = null
    }
}
