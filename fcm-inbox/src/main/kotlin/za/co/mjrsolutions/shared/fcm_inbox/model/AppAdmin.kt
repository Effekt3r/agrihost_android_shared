package za.co.mjrsolutions.shared.fcm_inbox.model

data class AppAdmin(
    var username: String = "",
    var displayName: String = "",
    var brands: List<String> = emptyList(),
    var notificationsEnabled: Boolean = true
)
