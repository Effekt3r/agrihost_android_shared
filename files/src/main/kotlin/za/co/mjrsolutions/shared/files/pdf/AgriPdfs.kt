package za.co.mjrsolutions.shared.files.pdf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import za.co.mjrsolutions.shared.files.internal.Report
import java.io.File

/**
 * Where PDFs live, the same in every app (hael's way): the app's own Documents folder
 * (getExternalFilesDir(DOCUMENTS): no permission on any Android version, read back the same way),
 * shared with viewers and mail through the app's FileProvider "{applicationId}.fileprovider"
 * (res/xml paths need external-files-path Documents/).
 */
object AgriPdfs {

    /** The app's Documents folder, or null when external storage is not available. */
    @JvmStatic
    fun dir(context: Context): File? = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)

    /** A PDF in that folder by name; whitespace in the name becomes "_" (hael's rule). */
    @JvmStatic
    fun file(context: Context, fileName: String): File? =
        dir(context)?.let { File(it, fileName.replace(Regex("\\s"), "_")) }

    /** A content URI a viewer or mail app may read, or null when the file is not there. */
    @JvmStatic
    fun uri(context: Context, file: File?): Uri? = try {
        if (file == null || !file.exists()) null
        else FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    } catch (e: Exception) {
        Report.exception(e)
        null
    }

    /** Opens the PDF in a viewer; false when it is not there or no viewer could open it. */
    @JvmStatic
    fun view(context: Context, file: File?): Boolean {
        val uri = uri(context, file) ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Report.exception(e)
            false
        }
    }
}
