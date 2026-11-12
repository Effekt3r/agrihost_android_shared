package za.co.mjrsolutions.shared.audit

/**
 * What a console log entry keeps of a request or response body. A Firestore document holds at
 * most 1 MiB and one base64 photo comes close to that, so an entry with photos in its body used
 * to be lost entirely (hael 4836, mr store-image and harvest bodies). Before a body is logged:
 * - every JSON string value of [PHOTO_MIN_CHARS] or more base64 characters (a photo) becomes
 *   "[photo, N KB, not kept in this log]", hael's note for its closing photos. Signatures are far
 *   smaller and stay, as does every other value;
 * - what is still longer than [MAX_REQUEST_CHARS] is cut there, with a note saying so.
 *
 * One linear scan over the string literals - no regex, which recurses per character and
 * overflows the stack on bodies of several MB.
 */
object AuditPayloads {

    /** A base64 value this long is a photo: ~73 KB of image. Signatures are ~10-40 KB of text. */
    const val PHOTO_MIN_CHARS = 100_000

    /** Room for the entry's other fields within the 1 MiB document limit. */
    const val MAX_REQUEST_CHARS = 900_000

    @JvmStatic
    fun forLog(text: String?): String? {
        if (text.isNullOrEmpty()) return text
        return cap(stripPhotos(text))
    }

    private fun stripPhotos(text: String): String {
        if (text.length < PHOTO_MIN_CHARS) return text
        val n = text.length
        var out: StringBuilder? = null
        var copiedUpTo = 0
        var i = 0
        while (i < n) {
            if (text[i] != '"') {
                i++
                continue
            }
            val start = i + 1
            var j = start
            var base64 = true
            while (j < n) {
                val c = text[j]
                if (c == '\\') {
                    // \/ (org.json) and \n \r (Base64.DEFAULT line breaks) are base64 spellings.
                    val next = if (j + 1 < n) text[j + 1] else ' '
                    if (next != '/' && next != 'n' && next != 'r') base64 = false
                    j += 2
                    continue
                }
                if (c == '"') break
                if (!isBase64Char(c)) base64 = false
                j++
            }
            val length = j - start
            if (base64 && j < n && length >= PHOTO_MIN_CHARS) {
                val sb = out ?: StringBuilder(n / 4).also { out = it }
                sb.append(text, copiedUpTo, start)
                sb.append("[photo, ").append(Math.round(length * 3 / 4.0 / 1024.0)).append(" KB, not kept in this log]")
                copiedUpTo = j
            }
            i = j + 1
        }
        val sb = out ?: return text
        sb.append(text, copiedUpTo, n)
        return sb.toString()
    }

    private fun isBase64Char(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='

    private fun cap(text: String): String =
        if (text.length <= MAX_REQUEST_CHARS) text
        else text.substring(0, MAX_REQUEST_CHARS) + "\n[log cut at $MAX_REQUEST_CHARS of ${text.length} characters]"
}
