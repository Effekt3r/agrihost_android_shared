package za.co.mjrsolutions.shared.language

import java.util.concurrent.Callable

object LangConfigJava {
    @JvmStatic
    @JvmOverloads
    fun create(
        defaultLanguage: String,
        supportedLanguages: List<LanguageOption>,
        userLangProvider: Callable<String?>? = null
    ): LangConfig = LangConfig(
        defaultLanguage = defaultLanguage,
        supportedLanguages = supportedLanguages,
        assetFileNamer = { "$it.json" },
        userLangProvider = userLangProvider?.let { c -> { runCatching { c.call() }.getOrNull() } }
    )
}
