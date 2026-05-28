package za.co.mjrsolutions.shared.language

data class LangConfig(
    val defaultLanguage: String,
    val supportedLanguages: List<LanguageOption>,
    val assetFileNamer: (String) -> String = { "$it.json" },
    val userLangProvider: (() -> String?)? = null
)
