package za.co.mjrsolutions.shared.fcm_inbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object EmulatorSetup {
    /**
     * Initialise Firebase with stub options (no google-services.json needed in a library module)
     * and point Firestore at the local emulator.
     *
     * For physical devices connected via USB: run `adb reverse tcp:8080 tcp:8080` before the
     * test so that `localhost:8080` on the device tunnels to the host Firestore emulator.
     * For AVD emulators the same `localhost` address works natively.
     */
    fun configure() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(
                ctx,
                FirebaseOptions.Builder()
                    .setProjectId("agrihost-7970a")
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("fake-api-key-for-emulator")
                    .build()
            )
        }
        val fs = FirebaseFirestore.getInstance()
        try {
            fs.useEmulator("localhost", 8080)
            fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
        } catch (_: IllegalStateException) {
            // already configured for this run
        }
    }
}
