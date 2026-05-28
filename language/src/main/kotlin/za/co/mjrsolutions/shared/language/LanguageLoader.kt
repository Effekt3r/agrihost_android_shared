package za.co.mjrsolutions.shared.language

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors

internal object LanguageLoader {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(
        context: Context,
        code: String,
        assetFileName: String,
        callback: LoadCallback
    ) {
        executor.execute {
            try {
                val copied = AssetCopier.copyFromAssetsIfMissing(context, code, assetFileName)
                val file = AssetCopier.getLanguageFile(context, code)
                if (!file.exists()) {
                    mainHandler.post {
                        callback.onFailure(
                            if (copied) "Language file unexpectedly missing"
                            else "Language file not found in assets: $assetFileName"
                        )
                    }
                    return@execute
                }

                val text = file.readText(Charsets.UTF_8)
                val map = parseJson(text)
                Dictionary.replace(map, code)
                mainHandler.post { callback.onSuccess() }
            } catch (e: Exception) {
                mainHandler.post { callback.onFailure(e.message ?: "Load failed") }
            }
        }
    }

    private fun parseJson(text: String): Map<String, String> {
        val obj = JSONObject(text)
        val out = HashMap<String, String>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.optString(k)
        }
        return out
    }
}
