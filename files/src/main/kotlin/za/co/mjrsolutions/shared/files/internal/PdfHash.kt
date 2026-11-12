package za.co.mjrsolutions.shared.files.internal

import java.security.MessageDigest

/**
 * A PDF's content fingerprint: SHA-256 over the file with the parts iText changes on every build
 * left out - the /CreationDate and /ModDate of the document info and the trailer's /ID. So a PDF
 * rebuilt from unchanged data (vrugte rebuilds every report's PDF on each main-screen resume, hael
 * before every view) has the same hash and is not copied again; any change in what it shows does.
 */
internal object PdfHash {

    private val CREATION = Regex("/CreationDate\\s*\\(D:[^)]*\\)")
    private val MODIFIED = Regex("/ModDate\\s*\\(D:[^)]*\\)")
    private val ID = Regex("/ID\\s*\\[\\s*<[0-9A-Fa-f]*>\\s*<[0-9A-Fa-f]*>\\s*\\]")

    fun of(bytes: ByteArray): String {
        // ISO-8859-1 maps every byte to one char and back, so binary streams survive untouched.
        var text = String(bytes, Charsets.ISO_8859_1)
        text = CREATION.replace(text, "/CreationDate()")
        text = MODIFIED.replace(text, "/ModDate()")
        text = ID.replace(text, "/ID[]")
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.ISO_8859_1))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
