package za.co.mjrsolutions.shared.app_config

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

object AgriAppConfig {

    @Volatile private var configRef: AppConfigInit? = null
    @Volatile private var listener: ListenerRegistration? = null
    @Volatile private var callbackRef: AppConfigCallback? = null
    @Volatile private var latestSnapshot: AppConfigSnapshot? = null

    private val init: AppConfigInit
        get() = configRef ?: error(
            "AgriAppConfig.init(...) was not called. Call from your Application.onCreate."
        )

    @JvmStatic
    fun init(config: AppConfigInit) {
        configRef = config
        startListening()
    }

    @JvmStatic
    fun setCallback(callback: AppConfigCallback?) {
        callbackRef = callback
        // If we already have a snapshot, deliver it immediately
        val s = latestSnapshot
        if (s != null && callback != null) {
            callback.onConfigChanged(s)
        }
    }

    @JvmStatic
    fun stop() {
        listener?.remove()
        listener = null
    }

    @JvmStatic
    fun getSnapshot(): AppConfigSnapshot? = latestSnapshot

    @JvmStatic
    fun isUpdateRequired(): Boolean = latestSnapshot?.updateRequired ?: false

    @JvmStatic
    fun getMinVersion(): String? = latestSnapshot?.minVersion

    @JvmStatic
    fun getMaintenanceMessage(): String? = latestSnapshot?.maintenanceMessage

    @JvmStatic
    fun isForceUpdate(): Boolean = latestSnapshot?.forceUpdate ?: false

    @JvmStatic
    fun getAnnouncementMessage(): String? = latestSnapshot?.announcementMessage

    @JvmStatic
    fun isMaintenanceMode(): Boolean = latestSnapshot?.maintenanceMode ?: false

    @JvmStatic
    fun getMinSyncIntervalMinutes(): Int? = latestSnapshot?.minSyncIntervalMinutes

    @JvmStatic
    fun isDebugLoggingEnabled(): Boolean = latestSnapshot?.debugLoggingEnabled ?: false

    @JvmStatic
    fun getExtras(): Map<String, Any?> = latestSnapshot?.extras ?: emptyMap()

    private fun startListening() {
        val cfg = init
        val db = FirebaseFirestore.getInstance()
        val ref = db.collection("appConfig")
            .document(cfg.product)
            .collection("bundles")
            .document(cfg.bundleId)

        listener?.remove()
        listener = ref.addSnapshotListener { snapshot, e ->
            if (e != null) {
                FirebaseCrashlytics.getInstance().recordException(e)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            if (snapshot.exists()) {
                val data = snapshot.data ?: emptyMap()
                val minVersion = data["minVersion"] as? String
                val maintenanceMessage = data["maintenanceMessage"] as? String
                val forceUpdate = data["forceUpdate"] as? Boolean ?: false
                val announcementMessage = data["announcementMessage"] as? String
                val maintenanceMode = data["maintenanceMode"] as? Boolean ?: false
                val minSyncIntervalMinutes = (data["minSyncIntervalMinutes"] as? Number)?.toInt()
                val debugLoggingEnabled = data["debugLoggingEnabled"] as? Boolean ?: false
                val updateRequired = !minVersion.isNullOrBlank() &&
                    VersionCompare.compare(cfg.currentVersion, minVersion) < 0

                // Extras = everything except the known typed fields
                val known = setOf(
                    "minVersion",
                    "maintenanceMessage",
                    "forceUpdate",
                    "announcementMessage",
                    "maintenanceMode",
                    "minSyncIntervalMinutes",
                    "debugLoggingEnabled"
                )
                val extras = data.filterKeys { it !in known }

                val snap = AppConfigSnapshot(
                    currentVersion = cfg.currentVersion,
                    minVersion = minVersion,
                    maintenanceMessage = maintenanceMessage,
                    updateRequired = updateRequired,
                    forceUpdate = forceUpdate,
                    announcementMessage = announcementMessage,
                    maintenanceMode = maintenanceMode,
                    minSyncIntervalMinutes = minSyncIntervalMinutes,
                    debugLoggingEnabled = debugLoggingEnabled,
                    extras = extras
                )
                latestSnapshot = snap
                callbackRef?.onConfigChanged(snap)
            } else {
                // Seed the document with the current version so the web app has a baseline
                val seed = mapOf<String, Any>("minVersion" to cfg.currentVersion)
                ref.set(seed, SetOptions.merge())
                    .addOnFailureListener { ex ->
                        FirebaseCrashlytics.getInstance().recordException(ex)
                    }
            }
        }
    }
}
