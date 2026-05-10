package za.co.mjrsolutions.shared.fcm_inbox.model

data class Device(
    var fcmToken: String = "",
    var app: String = "",
    var flavor: String = "",
    var brand: String = "",
    var applicationId: String = "",
    var appVersion: String = "",
    var deviceModel: String = "",
    var osVersion: String = "",
    var createdAt: Long = 0L,
    var lastSeen: Long = 0L
)
