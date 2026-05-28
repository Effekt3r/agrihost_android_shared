package za.co.mjrsolutions.shared.language

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.textfield.TextInputLayout

internal object ViewTextWalker {

    fun setViewsText(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setViewsText(view.getChildAt(i))
            }
        }
        val tag = view.tag as? String ?: return
        val translated = Dictionary.get(tag) ?: return
        try {
            when (view) {
                is TextInputLayout -> view.hint = translated
                is EditText        -> view.setText(translated)
                is TextView        -> view.text = translated
            }
        } catch (_: Exception) {
            // A single bad tag must not crash translation
        }
    }
}
