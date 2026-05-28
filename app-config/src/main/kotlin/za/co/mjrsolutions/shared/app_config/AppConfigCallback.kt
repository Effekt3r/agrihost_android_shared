package za.co.mjrsolutions.shared.app_config

interface AppConfigCallback {
    fun onConfigChanged(snapshot: AppConfigSnapshot)
}
