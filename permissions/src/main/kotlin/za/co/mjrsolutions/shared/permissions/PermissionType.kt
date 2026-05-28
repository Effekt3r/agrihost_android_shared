package za.co.mjrsolutions.shared.permissions

import android.Manifest
import android.os.Build

/**
 * Runtime permission categories supported by AgriPermissions.
 *
 * Each value declares the manifest permission strings to request, the LangKey
 * identifiers for the rationale dialog title and body (resolved via AgriLang),
 * and the Android API level at which the permission first became a runtime
 * permission. Below that level the system grants automatically and AgriPermissions
 * short-circuits to onGranted().
 */
enum class PermissionType(
    val manifestPermissions: Array<String>,
    val rationaleTitleKey: String,
    val rationaleBodyKey: String,
    val minSdk: Int
) {
    LOCATION_FINE(
        manifestPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ),
        rationaleTitleKey = "permission_location_rationale_title",
        rationaleBodyKey = "permission_location_rationale_body",
        minSdk = Build.VERSION_CODES.M  // 23
    ),
    CAMERA(
        manifestPermissions = arrayOf(Manifest.permission.CAMERA),
        rationaleTitleKey = "permission_camera_rationale_title",
        rationaleBodyKey = "permission_camera_rationale_body",
        minSdk = Build.VERSION_CODES.M  // 23
    ),
    NOTIFICATIONS(
        manifestPermissions = arrayOf("android.permission.POST_NOTIFICATIONS"),
        rationaleTitleKey = "permission_notifications_rationale_title",
        rationaleBodyKey = "permission_notifications_rationale_body",
        minSdk = Build.VERSION_CODES.TIRAMISU  // 33
    ),
    BLUETOOTH(
        manifestPermissions = arrayOf(
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN"
        ),
        rationaleTitleKey = "permission_bluetooth_rationale_title",
        rationaleBodyKey = "permission_bluetooth_rationale_body",
        minSdk = Build.VERSION_CODES.S  // 31
    ),
    MEDIA_IMAGES(
        manifestPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf("android.permission.READ_MEDIA_IMAGES")
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        },
        rationaleTitleKey = "permission_media_rationale_title",
        rationaleBodyKey = "permission_media_rationale_body",
        minSdk = Build.VERSION_CODES.M
    );

    /**
     * True when the current runtime is below the version at which this permission
     * became user-grantable. The system has auto-granted; no prompt is needed.
     */
    fun isAutoGranted(): Boolean = Build.VERSION.SDK_INT < minSdk
}
