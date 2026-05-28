package za.co.mjrsolutions.shared.bootstrap

import za.co.mjrsolutions.shared.app_config.AppConfigInit
import za.co.mjrsolutions.shared.auth.AuthConfig
import za.co.mjrsolutions.shared.language.LangConfig
import za.co.mjrsolutions.shared.location.LocationConfig

/**
 * Aggregate configuration for AgriInit. Every field is non-null — Kotlin callers
 * cannot construct a partial instance. Java callers use the data class's
 * generated constructor; missing fields are a compile error.
 */
data class AgriConfig(
    val location: LocationConfig,
    val auth: AuthConfig,
    val language: LangConfig,
    val appConfig: AppConfigInit
)
