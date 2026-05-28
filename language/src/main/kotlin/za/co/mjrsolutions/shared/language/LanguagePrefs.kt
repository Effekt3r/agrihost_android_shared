package za.co.mjrsolutions.shared.language

import android.content.Context
import android.content.SharedPreferences

internal object LanguagePrefs {
    private const val PREFS_NAME = "agrilang_prefs"
    private const val KEY_SELECTED = "selected_lang"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelected(): String? = prefs?.getString(KEY_SELECTED, null)

    fun setSelected(code: String) {
        prefs?.edit()?.putString(KEY_SELECTED, code)?.apply()
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_SELECTED)?.apply()
    }
}
