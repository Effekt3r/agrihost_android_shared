package za.co.mjrsolutions.shared.location

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object AgriLocation {

    @Volatile private var configRef: LocationConfig? = null
    private const val DEFAULT_DIALOG_TITLE = "Getting GPS fix"
    private const val DEFAULT_DIALOG_MESSAGE = "Please wait while we lock onto your location…"

    private val config: LocationConfig
        get() = configRef ?: error(
            "AgriLocation.init(...) was not called. Call from your Application.onCreate."
        )

    @JvmStatic
    fun configure(config: LocationConfig) {
        configRef = config
        ContinuousStream.init(config)
    }

    @Deprecated(
        message = "Renamed to configure(). init() will be removed in Plan 5.",
        replaceWith = ReplaceWith("configure(config)")
    )
    @JvmStatic
    fun init(config: LocationConfig) {
        configure(config)
    }

    @JvmStatic
    fun requestFreshFix(context: Context, callback: FixCallback) {
        FreshFixRequester(context.applicationContext, config, callback).start()
    }

    @JvmStatic
    @JvmOverloads
    fun requestFreshFixWithProgress(
        activity: FragmentActivity,
        callback: FixCallback,
        dialogTitle: String = DEFAULT_DIALOG_TITLE,
        dialogMessage: String = DEFAULT_DIALOG_MESSAGE
    ) {
        val dialog = LocationProgressDialog(activity)
        dialog.show(dialogTitle, dialogMessage)
        val wrapped = object : FixCallback {
            override fun onFix(fix: AgriFix) {
                dialog.dismiss()
                callback.onFix(fix)
            }
            override fun onTimeout(bestEffort: AgriFix?) {
                dialog.dismiss()
                callback.onTimeout(bestEffort)
            }
            override fun onPermissionDenied() {
                dialog.dismiss()
                callback.onPermissionDenied()
            }
            override fun onLocationDisabled() {
                dialog.dismiss()
                callback.onLocationDisabled()
            }
        }
        FreshFixRequester(activity.applicationContext, config, wrapped).start()
    }

    @JvmStatic
    fun startUpdates(context: Context, listener: LocationListener) {
        ContinuousStream.addListener(context.applicationContext, listener)
    }

    @JvmStatic
    fun stopUpdates(listener: LocationListener) {
        ContinuousStream.removeListener(listener)
    }

    @JvmStatic
    fun getLatestFresh(): AgriFix? = ContinuousStream.getLatestFresh()

    @JvmStatic
    fun getLatestAny(): AgriFix? = ContinuousStream.getLatestAny()

    @JvmStatic
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun requestLocationPermission(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            requestCode
        )
    }

    @JvmStatic
    fun isLocationEnabled(context: Context): Boolean = FusedClient.isLocationEnabled(context)

    @JvmStatic
    fun showLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
