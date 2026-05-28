package za.co.mjrsolutions.shared.language

import android.content.Context
import java.io.File

internal object AssetCopier {

    private const val LANG_DIR = ".languages"

    fun getLanguageDir(context: Context): File {
        val dir = File(context.filesDir, LANG_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLanguageFile(context: Context, code: String): File {
        return File(getLanguageDir(context), "$code.json")
    }

    fun copyFromAssetsIfMissing(context: Context, code: String, assetFileName: String): Boolean {
        val target = getLanguageFile(context, code)
        if (target.exists()) return true

        return try {
            context.assets.open(assetFileName).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
