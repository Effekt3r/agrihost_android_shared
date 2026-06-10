package za.co.mjrsolutions.shared.bootstrap

import android.app.Application
import za.co.mjrsolutions.shared.audit.AgriAudit
import za.co.mjrsolutions.shared.app_config.AgriAppConfig
import za.co.mjrsolutions.shared.auth.AgriAuth
import za.co.mjrsolutions.shared.language.AgriLang
import za.co.mjrsolutions.shared.location.AgriLocation
import za.co.mjrsolutions.shared.permissions.AgriPermissions

/**
 * Single entry point for shared-lib initialisation. Call from Application.onCreate:
 *
 *   AgriInit.init(this, AgriConfig(...))
 *
 * Initialises permissions, auth (incl. encrypted store), language config, app-config
 * snapshot listener, the audit log, and configures (but does NOT start) the location module. Location
 * services begin only when an Activity invokes a gated AgriLocation entry point and
 * permission is granted.
 */
object AgriInit {

    @JvmStatic
    fun init(app: Application, config: AgriConfig) {
        AgriPermissions.init(app)
        AgriLocation.configure(config.location)
        AgriAuth.init(config.auth)
        AgriAuth.initStores(app)
        AgriLang.init(config.language)
        AgriLang.initStores(app)
        AgriAppConfig.init(config.appConfig)
        AgriAudit.init(app, config.audit)
    }
}
