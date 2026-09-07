package za.co.mjrsolutions.shared.app_config

fun interface GlobalConfigCallback {
    fun onGlobalConfigChanged(snapshot: GlobalConfigSnapshot)
}
