package za.co.mjrsolutions.shared.auth

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Sets a standardised set of Crashlytics keys on every successful login so crash reports
 * and breadcrumbs carry forensic identifiers. Called from [AgriAuth.login].
 *
 * Key contract (consistent across all 4 apps):
 *  - setUserId(serverUserId)            — Firebase's first-class user id (searchable in console)
 *  - "username"        : login username
 *  - "user_fullname"   : "name surname"
 *  - "user_email"      : email if present
 *  - "product"         : config-level product name (Hael / MR / Brand / Vrugte)
 *  - "flavor"          : BuildConfig.FLAVOR (per-flavor breakdown)
 *  - "app_version"     : BuildConfig.VERSION_NAME
 *  - "offline_login"   : true if the credentials were verified offline (degraded forensic signal)
 *
 * Wrapped in try/catch — Crashlytics not being available in a particular build (e.g. a stripped
 * variant) must never break login.
 */
internal object CrashlyticsKeys {

    fun setForUser(user: AuthUser, config: AuthConfig, offlineLogin: Boolean) {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setUserId(user.serverUserId)
            crashlytics.setCustomKey("username", user.username)
            crashlytics.setCustomKey("user_fullname", "${user.name} ${user.surname}".trim())
            user.email?.let { crashlytics.setCustomKey("user_email", it) }
            crashlytics.setCustomKey("product", config.product)
            crashlytics.setCustomKey("flavor", config.flavor)
            crashlytics.setCustomKey("app_version", config.appVersion)
            crashlytics.setCustomKey("offline_login", offlineLogin)
        }
    }

    fun clear() {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setUserId("")
            crashlytics.setCustomKey("username", "")
            crashlytics.setCustomKey("user_fullname", "")
            crashlytics.setCustomKey("user_email", "")
            crashlytics.setCustomKey("offline_login", false)
        }
    }
}
