package za.co.mjrsolutions.shared.permissions

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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
}
