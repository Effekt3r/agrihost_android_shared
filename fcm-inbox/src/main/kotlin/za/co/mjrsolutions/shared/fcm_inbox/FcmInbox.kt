package za.co.mjrsolutions.shared.fcm_inbox

import android.content.Context

object FcmInbox {

    @Volatile private var configRef: FcmInboxConfig? = null

    fun init(config: FcmInboxConfig) {
        configRef = config
    }

    internal val config: FcmInboxConfig
        get() = configRef ?: error("FcmInbox.init(...) was not called. " +
            "Call from your Application.onCreate before any inbox usage.")

    /** Convenience: register/refresh the current device's FCM token. Safe to call many times. */
    fun registerCurrentDevice(context: Context) {
        FcmTokenRegistrar(context.applicationContext).registerNow()
    }

    /** Tear-down on logout: drops this device's token doc. */
    fun onLogout(context: Context) {
        FcmTokenRegistrar(context.applicationContext).deregisterCurrentDevice()
    }
}
