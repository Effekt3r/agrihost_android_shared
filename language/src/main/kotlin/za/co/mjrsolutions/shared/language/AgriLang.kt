package za.co.mjrsolutions.shared.language

import android.content.Context
import android.view.View

object AgriLang {

    @Volatile private var configRef: LangConfig? = null

    private val config: LangConfig
        get() = configRef ?: error(
            "AgriLang.init(...) was not called. Call from your Application.onCreate."
        )

    @JvmStatic
    fun init(config: LangConfig) {
        configRef = config
    }

    @JvmStatic
    fun initStores(context: Context) {
        LanguagePrefs.init(context)
    }

    @JvmStatic
    fun reload(context: Context, callback: LoadCallback) {
        val cfg = config
        val code = resolveCode(cfg)
        loadInternal(context, cfg, code, persist = false, callback = callback)
    }

    @JvmStatic
    fun changeLanguage(context: Context, code: String, callback: LoadCallback) {
        val cfg = config
        loadInternal(context, cfg, code, persist = true, callback = callback)
    }

    @JvmStatic
    fun getLanguageOptions(): List<LanguageOption> = configRef?.supportedLanguages ?: emptyList()

    @JvmStatic
    fun getString(key: LangKey): String = getString(key.key())

    @JvmStatic
    fun getString(key: String): String = Dictionary.get(key) ?: key

    /**
     * Reverse lookup: the [LangKey.key] of the first key whose translation equals [value],
     * ignoring case, or null when nothing matches. Never throws: what to do on a miss (log
     * a non-fatal, fall back to the value, ...) is the caller's decision, not a crash in the
     * translation layer. While the dictionary is not loaded [getString] returns the key
     * itself, so a raw key name resolves to itself.
     */
    @JvmStatic
    fun keyForValue(keys: Array<out LangKey>, value: String?): String? {
        if (value == null) return null
        for (key in keys) {
            if (getString(key).equals(value, ignoreCase = true)) return key.key()
        }
        return null
    }

    @JvmStatic
    fun setViewsText(view: View) = ViewTextWalker.setViewsText(view)

    @JvmStatic
    fun hasLanguageLoaded(): Boolean = Dictionary.isLoaded()

    @JvmStatic
    fun getCurrentLanguageCode(): String? = Dictionary.currentCode()

    private fun resolveCode(cfg: LangConfig): String {
        cfg.userLangProvider?.invoke()?.takeIf { it.isNotBlank() }?.let { return it }
        LanguagePrefs.getSelected()?.takeIf { it.isNotBlank() }?.let { return it }
        return cfg.defaultLanguage
    }

    private fun loadInternal(
        context: Context,
        cfg: LangConfig,
        code: String,
        persist: Boolean,
        callback: LoadCallback
    ) {
        val assetName = cfg.assetFileNamer(code)
        LanguageLoader.load(context, code, assetName, object : LoadCallback {
            override fun onSuccess() {
                if (persist) LanguagePrefs.setSelected(code)
                callback.onSuccess()
            }
            override fun onFailure(message: String) {
                callback.onFailure(message)
            }
        })
    }
}
