package za.co.mjrsolutions.shared.location

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.roundToInt

internal class LocationProgressDialog(private val activity: Activity) {

    private var dialog: Dialog? = null
    private var accuracyView: TextView? = null

    fun show(title: String, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return

        val density = activity.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val messageView = TextView(activity).apply {
            text = message
        }
        val progress = ProgressBar(activity) // indeterminate spinner
        val accuracy = TextView(activity).apply {
            setPadding(dp(16), 0, 0, 0)
            text = ""
        }
        accuracyView = accuracy

        val progressRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
            addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(accuracy)
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(messageView)
            addView(progressRow)
        }

        val b = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(container)
            .setCancelable(false)
        dialog = b.create().also { it.show() }
    }

    /** Show the live fix accuracy ("± 12 m") next to the spinner. Language-neutral
     *  on purpose — no dictionary key needed. Must be called on the main looper. */
    fun updateAccuracy(accuracyMeters: Float) {
        if (activity.isFinishing || activity.isDestroyed) return
        accuracyView?.text = "± ${accuracyMeters.roundToInt()} m"
    }

    fun dismiss() {
        try { dialog?.dismiss() } catch (_: Exception) {}
        dialog = null
        accuracyView = null
    }
}
