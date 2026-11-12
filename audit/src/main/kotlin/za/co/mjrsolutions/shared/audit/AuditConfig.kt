package za.co.mjrsolutions.shared.audit

/**
 * Init-time configuration. [userNameProvider] is evaluated at log time (lazy), so init
 * order vs. the app's user store does not matter. Java callers: `userNameProvider` is required (no default overload exists for it);
 * a Java lambda `() -> name` satisfies the Kotlin `Function0<String?>` parameter directly.
 *
 * @param clientId frozen per app (spec decision #4): hael=FLAVOR, vrugte=FLAVOR+"_vrugte",
 *        Brand=FLAVOR, mr=FLAVOR+"_mr".
 * @param userNameProvider the assessor's FULL NAME: the console's user folder for every entry
 *        (hael's convention, the name the console's Users tab shows).
 * @param bundleId + [staleBundleTokenField]: when an FCM send hits 404/410 the global
 *        users/{id}.pushToken is always cleared; if BOTH of these are set, the per-app
 *        users/{id}/bundle/{bundleId}.{staleBundleTokenField} is cleared too (hael parity).
 * @param loginIdProvider the login id, written as "username" on photo/PDF entries and used as the
 *        users/{id} document id by [AgriUserRecord].
 * @param appVersion the app's versionName, on photo/PDF entries, user records and calculation logs.
 * @param applicationId the app's package; the users/{id}/bundle/{applicationId} document id.
 */
data class AuditConfig @JvmOverloads constructor(
    val clientId: String,
    val userNameProvider: () -> String?,
    val sender: String = "App",
    val bundleId: String? = null,
    val staleBundleTokenField: String? = null,
    val loginIdProvider: () -> String? = { null },
    val appVersion: String = "",
    val applicationId: String = ""
)
