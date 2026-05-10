package za.co.mjrsolutions.shared.fcm_inbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AppAdminGateTest {

    @Before fun setUp() {
        EmulatorSetup.configure()
        FcmInbox.init(FcmInboxConfig(
            app = "agrihost_hael", flavor = "AGRIHOST_TEST",
            applicationId = "za.co.test", appVersion = "0.0.1",
            usernameProvider = { "alice" }
        ))
        val db = FirebaseFirestore.getInstance()
        Tasks.await(db.collection("appAdmins").document("alice").delete())
    }

    @Test fun returns_false_when_doc_missing() {
        val gate = AppAdminGate()
        assertFalse(gate.isAdminForCurrentBrandBlocking())
    }

    @Test fun returns_true_when_brand_matches() {
        FirebaseFirestore.getInstance().collection("appAdmins").document("alice")
            .set(mapOf("username" to "alice", "brands" to listOf("AGRIHOST"), "notificationsEnabled" to true))
            .let(Tasks::await)
        assertTrue(AppAdminGate().isAdminForCurrentBrandBlocking())
    }

    @Test fun returns_false_when_brand_does_not_match() {
        FirebaseFirestore.getInstance().collection("appAdmins").document("alice")
            .set(mapOf("username" to "alice", "brands" to listOf("SAFIRE"), "notificationsEnabled" to true))
            .let(Tasks::await)
        assertFalse(AppAdminGate().isAdminForCurrentBrandBlocking())
    }
}
