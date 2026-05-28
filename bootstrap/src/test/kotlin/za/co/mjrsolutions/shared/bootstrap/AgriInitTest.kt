package za.co.mjrsolutions.shared.bootstrap

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.app_config.AppConfigInit
import za.co.mjrsolutions.shared.auth.AuthConfig
import za.co.mjrsolutions.shared.language.LangConfig
import za.co.mjrsolutions.shared.language.LanguageOption
import za.co.mjrsolutions.shared.location.LocationConfig
import za.co.mjrsolutions.shared.permissions.AgriPermissions
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AgriInitTest {

    @Test fun `init wires AgriPermissions appRef`() {
        val app: Application = ApplicationProvider.getApplicationContext()
        // AgriAuth.initStores uses EncryptedSharedPreferences (AndroidKeyStore) which is
        // unavailable in a JVM unit-test environment — that call will throw. We still expect
        // AgriPermissions to be wired because it is initialised first in AgriInit.init.
        runCatching {
            AgriInit.init(app, AgriConfig(
                location = LocationConfig(),
                auth = AuthConfig(
                    baseUrl = "https://example.test/api/",
                    applicationId = "test.pkg",
                    appVersion = "1.0.0",
                    flavor = "test",
                    fcmTokenProvider = { null },
                    product = "Test"
                ),
                language = LangConfig(
                    defaultLanguage = "en",
                    supportedLanguages = listOf(LanguageOption(displayName = "English", code = "en"))
                ),
                appConfig = AppConfigInit(product = "Test", bundleId = "test.pkg", currentVersion = "1.0.0")
            ))
        }
        // After init, AgriPermissions sync checks must work without throwing.
        assertTrue(
            runCatching { AgriPermissions.isGranted(app, za.co.mjrsolutions.shared.permissions.PermissionType.CAMERA) }
                .isSuccess,
            "AgriPermissions.isGranted threw after AgriInit.init — init not wired."
        )
    }
}
