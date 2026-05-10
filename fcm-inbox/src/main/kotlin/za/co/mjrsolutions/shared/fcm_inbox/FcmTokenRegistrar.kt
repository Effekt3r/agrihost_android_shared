package za.co.mjrsolutions.shared.fcm_inbox

import android.content.Context
import android.os.Build
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import za.co.mjrsolutions.shared.fcm_inbox.model.Device

internal class FcmTokenRegistrar(private val context: Context) {

    private val cfg get() = FcmInbox.config
    private val db get() = FirebaseFirestore.getInstance()

    fun registerNow() {
        val username = cfg.usernameProvider() ?: return
        Tasks.whenAll(
            FirebaseInstallations.getInstance().id,
            FirebaseMessaging.getInstance().token
        ).addOnSuccessListener {
            val iid = Tasks.await(FirebaseInstallations.getInstance().id)
            val token = Tasks.await(FirebaseMessaging.getInstance().token)
            writeDoc(username, iid, token)
        }
    }

    fun update(newToken: String) {
        val username = cfg.usernameProvider() ?: return
        FirebaseInstallations.getInstance().id.addOnSuccessListener { iid ->
            db.collection("users").document(username).collection("devices").document(iid)
                .set(mapOf("fcmToken" to newToken, "lastSeen" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
        }
    }

    fun deregisterCurrentDevice() {
        val username = cfg.usernameProvider() ?: return
        FirebaseInstallations.getInstance().id.addOnSuccessListener { iid ->
            db.collection("users").document(username).collection("devices").document(iid).delete()
        }
    }

    /* ---- Test seams (Internal): bypass FCM/Installations to keep tests deterministic. ---- */
    internal fun registerNowBlocking(fakeToken: String, fakeInstallationId: String) {
        val username = cfg.usernameProvider() ?: return
        writeDoc(username, fakeInstallationId, fakeToken)
    }

    internal fun updateBlocking(newToken: String, fakeInstallationId: String) {
        val username = cfg.usernameProvider() ?: return
        Tasks.await(
            db.collection("users").document(username).collection("devices").document(fakeInstallationId)
                .set(mapOf("fcmToken" to newToken, "lastSeen" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
        )
    }

    internal fun deregisterBlocking(fakeInstallationId: String) {
        val username = cfg.usernameProvider() ?: return
        Tasks.await(
            db.collection("users").document(username).collection("devices").document(fakeInstallationId).delete()
        )
    }

    private fun writeDoc(username: String, installationId: String, token: String) {
        val now = System.currentTimeMillis()
        val device = Device(
            fcmToken = token,
            app = cfg.app,
            flavor = cfg.flavor,
            brand = BrandUtil.brandForFlavor(cfg.flavor),
            applicationId = cfg.applicationId,
            appVersion = cfg.appVersion,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            createdAt = now,
            lastSeen = now
        )
        Tasks.await(
            db.collection("users").document(username).collection("devices").document(installationId)
                .set(device, com.google.firebase.firestore.SetOptions.merge())
        )
    }
}
