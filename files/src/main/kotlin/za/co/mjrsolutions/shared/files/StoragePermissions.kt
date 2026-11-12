package za.co.mjrsolutions.shared.files

import android.Manifest
import android.os.Build

/**
 * The storage permissions an app asks for, the same in every app. Android handles them
 * differently by version, and the apps keep photos in the gallery (their own rows) and PDFs in
 * their own Documents folder:
 * - Android 9 and older: WRITE + READ_EXTERNAL_STORAGE - inserting the camera's gallery row needs
 *   write access, and reading it back read access.
 * - Android 10-12: READ_EXTERNAL_STORAGE only. The app's own rows and files need nothing; read
 *   lets it open gallery photos a previous install took (a restored database). WRITE does nothing
 *   from Android 11 and is declared up to Android 9 only, so asking for it comes back denied
 *   without a dialog - an app that treats that as a refusal would never get past its start.
 * - Android 13 and up: none. READ_EXTERNAL_STORAGE does nothing there (READ_MEDIA_* replaced it)
 *   and the app's own rows and files need no permission.
 *
 * Matches the :files manifest (WRITE maxSdkVersion 28, READ maxSdkVersion 32). Never all-files access.
 */
object StoragePermissions {

    @JvmStatic
    @JvmOverloads
    fun required(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> = when {
        sdkInt <= Build.VERSION_CODES.P -> arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        sdkInt <= Build.VERSION_CODES.S_V2 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> emptyArray()
    }
}
