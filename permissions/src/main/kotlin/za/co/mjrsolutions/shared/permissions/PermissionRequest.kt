package za.co.mjrsolutions.shared.permissions

/**
 * Per-call overrides for [AgriPermissions.request]. When omitted, the rationale
 * dialog uses the default LangKeys declared on the PermissionType enum.
 */
data class PermissionRequest(
    val type: PermissionType,
    val titleKey: String? = null,
    val bodyKey: String? = null
) {
    fun resolvedTitleKey(): String = titleKey ?: type.rationaleTitleKey
    fun resolvedBodyKey(): String = bodyKey ?: type.rationaleBodyKey
}
