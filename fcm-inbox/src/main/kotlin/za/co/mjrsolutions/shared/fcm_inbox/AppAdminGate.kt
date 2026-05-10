package za.co.mjrsolutions.shared.fcm_inbox

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import za.co.mjrsolutions.shared.fcm_inbox.model.AppAdmin

class AppAdminGate {

    private val cfg get() = FcmInbox.config

    suspend fun isAdminForCurrentBrand(): Boolean {
        val username = cfg.usernameProvider() ?: return false
        return try {
            val snap = FirebaseFirestore.getInstance().collection("appAdmins").document(username).get().await()
            val admin = snap.toObject(AppAdmin::class.java) ?: return false
            admin.notificationsEnabled && admin.brands.contains(BrandUtil.brandForFlavor(cfg.flavor))
        } catch (_: Exception) {
            false
        }
    }

    /** Test seam: synchronous variant for Robolectric/instrumentation tests. */
    internal fun isAdminForCurrentBrandBlocking(): Boolean {
        val username = cfg.usernameProvider() ?: return false
        return try {
            val snap = Tasks.await(FirebaseFirestore.getInstance().collection("appAdmins").document(username).get())
            val admin = snap.toObject(AppAdmin::class.java) ?: return false
            admin.notificationsEnabled && admin.brands.contains(BrandUtil.brandForFlavor(cfg.flavor))
        } catch (_: Exception) {
            false
        }
    }
}
