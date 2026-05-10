package za.co.mjrsolutions.shared.fcm_inbox

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class FcmTokenRegistrarTest {

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db get() = FirebaseFirestore.getInstance()

    @Before fun setUp() {
        EmulatorSetup.configure()
        FcmInbox.init(FcmInboxConfig(
            app = "agrihost_hael",
            flavor = "AGRIHOST_TEST",
            applicationId = "za.co.test",
            appVersion = "0.0.1",
            usernameProvider = { "alice" }
        ))
        // Wipe alice's devices
        Tasks.await(db.collection("users").document("alice").collection("devices").get())
            .documents.forEach { Tasks.await(it.reference.delete()) }
    }

    @Test
    fun writes_a_device_doc_with_required_fields() {
        FcmTokenRegistrar(ctx).registerNowBlocking(fakeToken = "FAKE-TOKEN-123", fakeInstallationId = "iid-1")
        val doc = Tasks.await(db.collection("users").document("alice").collection("devices").document("iid-1").get())
        assertNotNull(doc.data)
        assertEquals("FAKE-TOKEN-123", doc.getString("fcmToken"))
        assertEquals("agrihost_hael", doc.getString("app"))
        assertEquals("AGRIHOST_TEST", doc.getString("flavor"))
        assertEquals("AGRIHOST", doc.getString("brand"))
    }

    @Test
    fun update_overwrites_only_fcmToken_keeping_other_fields() {
        FcmTokenRegistrar(ctx).registerNowBlocking(fakeToken = "T1", fakeInstallationId = "iid-1")
        val createdAt = Tasks.await(
            db.collection("users").document("alice").collection("devices").document("iid-1").get()
        ).getLong("createdAt")
        FcmTokenRegistrar(ctx).updateBlocking(newToken = "T2", fakeInstallationId = "iid-1")
        val updated = Tasks.await(
            db.collection("users").document("alice").collection("devices").document("iid-1").get()
        )
        assertEquals("T2", updated.getString("fcmToken"))
        assertEquals(createdAt, updated.getLong("createdAt"))
    }

    @Test
    fun no_user_skips_writes() {
        FcmInbox.init(FcmInboxConfig(
            app = "agrihost_hael", flavor = "AGRIHOST_TEST",
            applicationId = "za.co.test", appVersion = "0.0.1",
            usernameProvider = { null }
        ))
        FcmTokenRegistrar(ctx).registerNowBlocking(fakeToken = "X", fakeInstallationId = "iid-x")
        val list = Tasks.await(db.collection("users").document("alice").collection("devices").get())
        assertEquals(0, list.size())
    }

    @Test
    fun deregister_deletes_doc() {
        FcmTokenRegistrar(ctx).registerNowBlocking(fakeToken = "T1", fakeInstallationId = "iid-1")
        FcmTokenRegistrar(ctx).deregisterBlocking(fakeInstallationId = "iid-1")
        val doc = Tasks.await(db.collection("users").document("alice").collection("devices").document("iid-1").get())
        assertEquals(false, doc.exists())
    }
}
