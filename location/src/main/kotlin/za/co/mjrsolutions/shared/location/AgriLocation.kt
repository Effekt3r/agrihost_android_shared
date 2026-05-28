package za.co.mjrsolutions.shared.location

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import za.co.mjrsolutions.shared.permissions.AgriPermissions
import za.co.mjrsolutions.shared.permissions.PermissionCallback
import za.co.mjrsolutions.shared.permissions.PermissionType
import za.co.mjrsolutions.shared.permissions.internal.LocationStateProbe

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

    @Deprecated(
        message = "Migrate to requestFreshFix(activity, callback, gate). Removed in Plan 5.",
        replaceWith = ReplaceWith("requestFreshFix(activity, callback, gate)")
    )
    @JvmStatic
    fun requestFreshFix(context: Context, callback: FixCallback) {
        FreshFixRequester(context.applicationContext, config, callback).start()
    }

    @JvmStatic
    fun requestFreshFix(
        activity: FragmentActivity,
        callback: FixCallback,
        gate: LocationGate
    ) {
        val appCtx = activity.applicationContext
        if (AgriPermissions.isGranted(activity, PermissionType.LOCATION_FINE)) {
            if (!LocationStateProbe.isLocationEnabled(appCtx)) {
                gate.onLocationDisabled()
                return
            }
            gate.onReady()
            FreshFixRequester(appCtx, config, callback).start()
            return
        }
        AgriPermissions.request(activity, PermissionType.LOCATION_FINE, object : PermissionCallback {
            override fun onGranted() {
                if (!LocationStateProbe.isLocationEnabled(appCtx)) {
                    gate.onLocationDisabled()
                    return
                }
                gate.onReady()
                FreshFixRequester(appCtx, config, callback).start()
            }
            override fun onDenied(permanentlyDenied: List<String>) {
                gate.onPermissionDenied(permanent = permanentlyDenied.isNotEmpty())
            }
        })
    }

    @Deprecated(
        message = "Migrate to requestFreshFixWithProgress(activity, callback, gate, ...) for permission self-gating. Removed in Plan 5.",
        replaceWith = ReplaceWith("requestFreshFixWithProgress(activity, callback, gate, dialogTitle, dialogMessage)")
    )
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
    @JvmOverloads
    fun requestFreshFixWithProgress(
        activity: FragmentActivity,
        callback: FixCallback,
        gate: LocationGate,
        dialogTitle: String = DEFAULT_DIALOG_TITLE,
        dialogMessage: String = DEFAULT_DIALOG_MESSAGE
    ) {
        val appCtx = activity.applicationContext

        fun startWithProgress() {
            val dialog = LocationProgressDialog(activity)
            dialog.show(dialogTitle, dialogMessage)
            val wrapped = object : FixCallback {
                override fun onFix(fix: AgriFix) { dialog.dismiss(); callback.onFix(fix) }
                override fun onTimeout(bestEffort: AgriFix?) { dialog.dismiss(); callback.onTimeout(bestEffort) }
                override fun onPermissionDenied() { dialog.dismiss(); callback.onPermissionDenied() }
                override fun onLocationDisabled() { dialog.dismiss(); callback.onLocationDisabled() }
            }
            FreshFixRequester(appCtx, config, wrapped).start()
        }

        if (AgriPermissions.isGranted(activity, PermissionType.LOCATION_FINE)) {
            if (!LocationStateProbe.isLocationEnabled(appCtx)) {
                gate.onLocationDisabled()
                return
            }
            gate.onReady()
            startWithProgress()
            return
        }
        AgriPermissions.request(activity, PermissionType.LOCATION_FINE, object : PermissionCallback {
            override fun onGranted() {
                if (!LocationStateProbe.isLocationEnabled(appCtx)) {
                    gate.onLocationDisabled()
                    return
                }
                gate.onReady()
                startWithProgress()
            }
            override fun onDenied(permanentlyDenied: List<String>) {
                gate.onPermissionDenied(permanent = permanentlyDenied.isNotEmpty())
            }
        })
    }

    @Deprecated(
        message = "Migrate to startUpdates(activity, listener, gate) for permission self-gating. Removed in Plan 5.",
        replaceWith = ReplaceWith(
            "startUpdates(activity, listener, gate)",
            "androidx.fragment.app.FragmentActivity"
        )
    )
    @JvmStatic
    fun startUpdates(context: Context, listener: LocationListener) {
        ContinuousStream.addListener(context.applicationContext, listener)
    }

    @JvmStatic
    fun startUpdates(
        activity: FragmentActivity,
        listener: LocationListener,
        gate: LocationGate
    ) {
        val appCtx = activity.applicationContext
        if (AgriPermissions.isGranted(activity, PermissionType.LOCATION_FINE)) {
            if (!LocationStateProbe.isLocationEnabled(appCtx)) {
                gate.onLocationDisabled()
                return
            }
            ContinuousStream.addListener(appCtx, listener)
            gate.onReady()
            return
        }
        AgriPermissions.request(activity, PermissionType.LOCATION_FINE, object : PermissionCallback {
            override fun onGranted() {
                if (!LocationStateProbe.isLocationEnabled(appCtx)) {
                    gate.onLocationDisabled()
                    return
                }
                ContinuousStream.addListener(appCtx, listener)
                gate.onReady()
            }
            override fun onDenied(permanentlyDenied: List<String>) {
                gate.onPermissionDenied(permanent = permanentlyDenied.isNotEmpty())
            }
        })
    }

    @JvmStatic
    fun stopUpdates(listener: LocationListener) {
        ContinuousStream.removeListener(listener)
    }

    @JvmStatic
    fun getLatestFresh(): AgriFix? = ContinuousStream.getLatestFresh()

    @JvmStatic
    fun getLatestAny(): AgriFix? = ContinuousStream.getLatestAny()

    @Deprecated(
        message = "Use LocationStateProbe.openLocationSettings(context) directly. Removed in Plan 5.",
        replaceWith = ReplaceWith(
            "context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))",
            "android.content.Intent",
            "android.provider.Settings"
        )
    )
    @JvmStatic
    fun showLocationSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
}
