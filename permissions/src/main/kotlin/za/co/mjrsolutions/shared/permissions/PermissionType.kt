package za.co.mjrsolutions.shared.permissions

import android.Manifest
import android.os.Build

/**
 * Runtime permission categories supported by AgriPermissions.
 *
 * Each value declares the LangKey identifiers for the rationale dialog title
 * and body (resolved via AgriLang), and the Android API level at which the
 * permission first became a runtime permission. Below that level the system
 * grants automatically and AgriPermissions short-circuits to onGranted().
 *
 * The manifest permission strings are computed at access time (not at enum
 * load time) so that any SDK-dependent branch (MEDIA_IMAGES) re-evaluates
 * under Robolectric @Config(sdk=[...]) and on every real-device call.
 */
enum class PermissionType(
    val rationaleTitleKey: String,
    val rationaleBodyKey: String,
    val minSdk: Int
) {
    LOCATION_FINE(
        rationaleTitleKey = "permission_location_rationale_title",
        rationaleBodyKey = "permission_location_rationale_body",
        minSdk = Build.VERSION_CODES.M  // 23
    ),
    CAMERA(
        rationaleTitleKey = "permission_camera_rationale_title",
        rationaleBodyKey = "permission_camera_rationale_body",
        minSdk = Build.VERSION_CODES.M  // 23
    ),
    NOTIFICATIONS(
        rationaleTitleKey = "permission_notifications_rationale_title",
        rationaleBodyKey = "permission_notifications_rationale_body",
        minSdk = Build.VERSION_CODES.TIRAMISU  // 33
    ),
    BLUETOOTH(
        rationaleTitleKey = "permission_bluetooth_rationale_title",
        rationaleBodyKey = "permission_bluetooth_rationale_body",
        minSdk = Build.VERSION_CODES.S  // 31
    ),
    MEDIA_IMAGES(
        rationaleTitleKey = "permission_media_rationale_title",
        rationaleBodyKey = "permission_media_rationale_body",
        minSdk = Build.VERSION_CODES.M  // 23
    );

    /**
     * Manifest permission strings to request. Computed at access time so any
     * SDK-conditional entry (MEDIA_IMAGES) re-evaluates per call.
     */
    val manifestPermissions: List<String>
        get() = when (this) {
            LOCATION_FINE -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            CAMERA -> listOf(Manifest.permission.CAMERA)
            NOTIFICATIONS -> listOf("android.permission.POST_NOTIFICATIONS")
            BLUETOOTH -> listOf(
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_SCAN"
            )
            MEDIA_IMAGES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf("android.permission.READ_MEDIA_IMAGES")
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    /**
     * True when the current runtime is below the version at which this permission
     * became user-grantable. The system has auto-granted; no prompt is needed.
     */
    fun isAutoGranted(): Boolean = Build.VERSION.SDK_INT < minSdk
}
