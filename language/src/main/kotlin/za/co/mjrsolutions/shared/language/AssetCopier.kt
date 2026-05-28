package za.co.mjrsolutions.shared.language

import android.content.Context
import java.io.File

internal object AssetCopier {

    private const val LANG_DIR = ".languages"
    private val VERSION_REGEX = """"LANGUAGE_VERSION"\s*:\s*"([^"]*)"""".toRegex()

    fun getLanguageDir(context: Context): File {
        val dir = File(context.filesDir, LANG_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLanguageFile(context: Context, code: String): File {
        return File(getLanguageDir(context), "$code.json")
    }

    /**
     * Ensure `.languages/{code}.json` is present and at least as up-to-date as the bundled asset.
     *
     * - If the cached file does not exist, copy from assets.
     * - If `LANGUAGE_VERSION` in the bundled asset is greater than the cached file's, overwrite.
     * - Otherwise leave the cached file alone.
     *
     * Returns true if the destination exists after this call.
     */
    fun copyFromAssetsIfMissing(context: Context, code: String, assetFileName: String): Boolean {
        val target = getLanguageFile(context, code)

        if (!target.exists()) {
            return copyAssetTo(context, assetFileName, target)
        }

        val bundledVersion = try {
            context.assets.open(assetFileName).bufferedReader(Charsets.UTF_8)
                .use { readVersion(it.readText()) }
        } catch (_: Exception) {
            null
        }

        val cachedVersion = try {
            readVersion(target.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }

        if (bundledVersion != null && isNewer(bundledVersion, cachedVersion)) {
            return copyAssetTo(context, assetFileName, target)
        }

        return true
    }

    private fun copyAssetTo(context: Context, assetFileName: String, target: File): Boolean {
        return try {
            context.assets.open(assetFileName).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readVersion(jsonText: String): String? {
        return VERSION_REGEX.find(jsonText)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    /** Compare LANGUAGE_VERSION strings (dotted integers; missing = "0"). True if [a] > [b]. */
    internal fun isNewer(a: String?, b: String?): Boolean {
        val aParts = (a ?: "0").split(".").map { it.toIntOrNull() ?: 0 }
        val bParts = (b ?: "0").split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(aParts.size, bParts.size)
        for (i in 0 until len) {
            val ai = aParts.getOrNull(i) ?: 0
            val bi = bParts.getOrNull(i) ?: 0
            if (ai != bi) return ai > bi
        }
        return false
    }
}
