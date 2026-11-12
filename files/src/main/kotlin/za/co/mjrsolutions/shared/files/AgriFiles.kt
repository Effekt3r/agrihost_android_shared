package za.co.mjrsolutions.shared.files

import android.app.Application
import androidx.annotation.VisibleForTesting

/**
 * Where photos and PDFs live and how they reach Firebase Storage, the same in every app (hael's
 * way): camera photos in the gallery (Pictures) through MediaStore, PDFs in the app's Documents
 * folder, copies to Storage on Wi-Fi with a console entry each, database exports to db_exports.
 *
 * Init from Application.onCreate, after AgriAudit.init (the console entries go through it).
 */
object AgriFiles {

    @Volatile
    private var config: FilesConfig? = null

    @Volatile
    private var app: Application? = null

    @JvmStatic
    fun init(application: Application, config: FilesConfig) {
        this.app = application
        this.config = config
        za.co.mjrsolutions.shared.files.internal.BackupScheduler.cancelLegacyJobs(application, config.legacyJobIds)
    }

    /** The config, or null before [init]. */
    @JvmStatic
    fun config(): FilesConfig? = config

    /** The application given to [init], for callers that have no Context at hand (mr's PDF builder). */
    @JvmStatic
    fun application(): Application? = app

    @VisibleForTesting
    internal fun resetForTest() {
        config = null
        app = null
    }
}
