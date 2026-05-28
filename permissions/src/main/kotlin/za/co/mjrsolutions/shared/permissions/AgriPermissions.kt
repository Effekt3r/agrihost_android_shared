package za.co.mjrsolutions.shared.permissions

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import za.co.mjrsolutions.shared.permissions.internal.PermissionFragment
import za.co.mjrsolutions.shared.permissions.internal.RationaleDialog

/**
 * Single entry point for runtime permissions across the four Agrihost Android apps.
 *
 * Lifecycle:
 *   1. AgriPermissions.init(application) — called once from Application.onCreate (via AgriInit).
 *   2. Synchronous checks (isGranted, shouldShowRationale, biometric*) — safe to call anywhere.
 *   3. Prompt API (request, requestAll) — requires FragmentActivity; mounts a headless
 *      PermissionFragment and shows a Material rationale dialog before the system prompt.
 */
object AgriPermissions {

    @Volatile private var appRef: Application? = null

    private val app: Application
        get() = appRef ?: error(
            "AgriInit.init(application, config) must be called in Application.onCreate " +
            "before any AgriPermissions usage. See HaelApplication for reference."
        )

    @JvmStatic
    fun init(app: Application) {
        appRef = app
    }

    // --- Synchronous checks -----------------------------------------------------

    @JvmStatic
    fun isGranted(ctx: Context, type: PermissionType): Boolean {
        if (type.isAutoGranted()) return true
        return type.manifestPermissions.all { perm ->
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    @JvmStatic
    fun shouldShowRationale(activity: Activity, type: PermissionType): Boolean {
        if (type.isAutoGranted()) return false
        return type.manifestPermissions.any { perm ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }
    }

    // --- Biometric (capability check, not a runtime permission per se) ----------

    @JvmStatic
    fun biometricAvailable(ctx: Context): Boolean = BiometricHelper.isBiometricAvailable(ctx)

    @JvmStatic
    fun biometricStatus(ctx: Context): BiometricStatus = BiometricHelper.checkBiometricStatus(ctx)

    // --- Prompt API -----------------------------------------------------------

    @JvmStatic
    fun request(activity: FragmentActivity, type: PermissionType, callback: PermissionCallback) {
        request(activity, PermissionRequest(type), callback)
    }

    @JvmStatic
    fun request(activity: FragmentActivity, req: PermissionRequest, callback: PermissionCallback) {
        if (isGranted(activity, req.type)) {
            callback.onGranted()
            return
        }

        val permanentlyDenied = !shouldShowRationale(activity, req.type) &&
            !isGranted(activity, req.type) && hasBeenRequestedBefore(activity, req.type)

        RationaleDialog.show(
            activity = activity,
            type = req.type,
            titleKey = req.resolvedTitleKey(),
            bodyKey = req.resolvedBodyKey(),
            settingsVariant = permanentlyDenied,
            onContinue = {
                if (permanentlyDenied) {
                    callback.onDenied(req.type.manifestPermissions.toList())
                    // Settings opens inside RationaleDialog after this onContinue returns.
                } else {
                    PermissionFragment.attach(activity).requestNow(req.type, callback)
                }
            },
            onCancel = {
                callback.onDenied(emptyList())
            }
        )
    }

    @JvmStatic
    fun requestAll(
        activity: FragmentActivity,
        types: List<PermissionType>,
        callback: PermissionCallback
    ) {
        val outstanding = types.filterNot { isGranted(activity, it) }
        if (outstanding.isEmpty()) {
            callback.onGranted()
            return
        }
        requestSequentially(activity, outstanding, mutableListOf(), callback)
    }

    private fun requestSequentially(
        activity: FragmentActivity,
        remaining: List<PermissionType>,
        denied: MutableList<String>,
        finalCallback: PermissionCallback
    ) {
        if (remaining.isEmpty()) {
            if (denied.isEmpty()) finalCallback.onGranted() else finalCallback.onDenied(denied)
            return
        }
        val next = remaining.first()
        val rest = remaining.drop(1)
        request(activity, next, object : PermissionCallback {
            override fun onGranted() {
                requestSequentially(activity, rest, denied, finalCallback)
            }
            override fun onDenied(permanentlyDenied: List<String>) {
                denied.addAll(permanentlyDenied)
                requestSequentially(activity, rest, denied, finalCallback)
            }
        })
    }

    /**
     * Tracks whether the app has previously prompted for [type] in this install.
     * Used in combination with shouldShowRationale to detect the "Don't ask again" state.
     */
    private fun hasBeenRequestedBefore(activity: FragmentActivity, type: PermissionType): Boolean {
        val prefs = activity.getSharedPreferences("agri_permissions", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean("requested_${type.name}", false)
    }

    internal fun markRequested(activity: FragmentActivity, type: PermissionType) {
        activity.getSharedPreferences("agri_permissions", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("requested_${type.name}", true)
            .apply()
    }
}
