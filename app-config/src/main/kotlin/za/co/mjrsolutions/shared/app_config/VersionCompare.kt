package za.co.mjrsolutions.shared.app_config

object VersionCompare {
    /**
     * Compare semver-style version strings (e.g. "8.0.30" vs "8.0.25").
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
     * Null/empty strings sort lowest.
     */
    @JvmStatic
    fun compare(v1: String?, v2: String?): Int {
        if (v1.isNullOrBlank() && v2.isNullOrBlank()) return 0
        if (v1.isNullOrBlank()) return -1
        if (v2.isNullOrBlank()) return 1

        val a = v1.split(".")
        val b = v2.split(".")
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val ai = a.getOrNull(i)?.toIntOrNull() ?: 0
            val bi = b.getOrNull(i)?.toIntOrNull() ?: 0
            if (ai != bi) return ai - bi
        }
        return 0
    }
}
