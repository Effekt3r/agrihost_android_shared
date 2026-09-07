package za.co.mjrsolutions.shared.app_config

/**
 * The console-managed global document (`services/AppConfig`), shared by every product and
 * flavor. Its fields are whatever the console writes, so this is a typed view over the raw
 * map rather than a fixed schema: read what you need with a default and ignore the rest.
 */
data class GlobalConfigSnapshot(val data: Map<String, Any?>) {

    /** The stored boolean, or [default] when the key is absent or holds another type. */
    fun getBoolean(key: String, default: Boolean): Boolean = (data[key] as? Boolean) ?: default

    /** The stored string, or null when the key is absent or holds another type. */
    fun getString(key: String): String? = data[key] as? String

    /** The stored map, or null when the key is absent or holds another type. */
    fun getMap(key: String): Map<String, Any?>? = asStringMap(data[key])

    /**
     * A per-flavor sub-map, e.g. `smtpByFlavor.AGRIHOST_UAT`. Null when the parent is not a
     * map, the flavor has no entry, or the entry is not a map: every one of those means
     * "nothing configured for this flavor".
     */
    fun getFlavorMap(key: String, flavor: String): Map<String, Any?>? =
        asStringMap(getMap(key)?.get(flavor))

    private fun asStringMap(value: Any?): Map<String, Any?>? {
        val map = value as? Map<*, *> ?: return null
        val out = LinkedHashMap<String, Any?>(map.size)
        for ((k, v) in map) {
            if (k is String) out[k] = v
        }
        return out
    }

    companion object {
        @JvmField
        val EMPTY = GlobalConfigSnapshot(emptyMap())
    }
}
