package za.co.mjrsolutions.shared.language

internal object Dictionary {
    @Volatile private var data: Map<String, String> = emptyMap()
    @Volatile private var languageCode: String? = null

    fun replace(newData: Map<String, String>, code: String) {
        data = newData
        languageCode = code
    }

    fun get(key: String): String? = data[key]

    fun isLoaded(): Boolean = data.isNotEmpty()

    fun currentCode(): String? = languageCode
}
