package za.co.mjrsolutions.shared.files.backup

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import za.co.mjrsolutions.shared.audit.AuditFormat
import za.co.mjrsolutions.shared.files.AgriFiles
import za.co.mjrsolutions.shared.files.internal.BackupDeps
import za.co.mjrsolutions.shared.files.internal.BackupPass
import za.co.mjrsolutions.shared.files.internal.BackupScheduler
import za.co.mjrsolutions.shared.files.internal.PdfCopier
import za.co.mjrsolutions.shared.files.internal.Report
import za.co.mjrsolutions.shared.files.pdf.PdfInfo
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Copies of every photo and PDF in Firebase Storage, so they can be looked up from the report
 * after the server or the tablet lost them (hael's photo backup, shared, plus PDFs).
 *
 * When it runs (Franco, 2026-11-06): copying by itself happens on Wi-Fi only - every resume of the
 * report list, new photo and new PDF asks for a pass ([requestWifiPass]), queued as a job Android
 * runs once the tablet is on unmetered Wi-Fi, app open or not; a pass that loses Wi-Fi stops and
 * waits for the next one. Mobile data only when the assessor taps the app's Sync for a report
 * ([runForReport]): that report's photos and PDFs go then.
 */
object AgriBackup {

    private const val TAG = "AgriBackup"

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "agri-backup").apply { isDaemon = true }
    }

    /** Set by every request: a Wi-Fi pass that is running laps again to pick up what came in meanwhile. */
    private val runAgain = AtomicBoolean(false)

    @VisibleForTesting
    @Volatile
    internal var depsFactory: (Context) -> BackupDeps = { BackupDeps.real(it) }

    /** Queues a full pass for Wi-Fi. Cheap and safe on the main thread. */
    @JvmStatic
    fun requestWifiPass(context: Context?) {
        if (context == null) return
        runAgain.set(true)
        BackupScheduler.scheduleWifiPass(context)
    }

    /** A Sync tap: this report's photos and PDFs go now, on mobile data too. */
    @JvmStatic
    fun runForReport(context: Context?, reportNumber: String?) {
        if (context == null || AuditFormat.isBlank(reportNumber)) return
        val app = context.applicationContext
        execute { pass(app)?.run(wifiOnly = false, onlyReport = reportNumber!!.trim()) }
    }

    /** True once a copy of this photo link is in Storage. */
    @JvmStatic
    fun isPhotoBackedUp(context: Context, photoUri: String?): Boolean {
        if (AuditFormat.isBlank(photoUri)) return false
        val value = context.applicationContext
            .getSharedPreferences(BackupPass.DONE_PREFS, Context.MODE_PRIVATE)
            .getString(za.co.mjrsolutions.shared.files.photos.GalleryPhotos.key(photoUri), null)
        return value != null && !value.startsWith(BackupPass.SKIP_PREFIX)
    }

    /**
     * A PDF was just generated: its copy is queued for Wi-Fi (or the report's next Sync). Call
     * after the document is closed. A PDF rebuilt with unchanged content is not copied again.
     */
    @JvmStatic
    fun pdfCreated(context: Context?, pdf: File?, info: PdfInfo?) {
        if (context == null || pdf == null || info == null) return
        try {
            PdfCopier.markPending(context, pdf, info, System.currentTimeMillis())
            requestWifiPass(context)
        } catch (e: Exception) {
            Report.exception(e)
        }
    }

    /** [pdfCreated] for code without a Context at hand: uses the application given to AgriFiles.init. */
    @JvmStatic
    fun pdfCreated(pdf: File?, info: PdfInfo?) {
        pdfCreated(AgriFiles.application(), pdf, info)
    }

    /**
     * A sync tap that sends every report (mr): every photo and PDF not copied yet goes now, on
     * mobile data too. (hael's Sync Photos is per report: [runForReport].)
     */
    @JvmStatic
    fun runNow(context: Context?) {
        if (context == null) return
        val app = context.applicationContext
        execute { pass(app)?.run(wifiOnly = false, onlyReport = null) }
    }

    /** The Wi-Fi job's work: full passes while on Wi-Fi until nothing new was asked for. */
    internal fun runWifiPass(context: Context, done: () -> Unit) {
        val app = context.applicationContext
        execute {
            try {
                do {
                    runAgain.set(false)
                    pass(app)?.run(wifiOnly = true, onlyReport = null)
                } while (runAgain.get() && depsFactory(app).network.isOnWifi())
            } finally {
                done()
            }
        }
    }

    private fun pass(app: Context): BackupPass? {
        val config = AgriFiles.config()
        if (config == null) {
            Log.i(TAG, "pass skipped: AgriFiles not initialised")
            return null
        }
        return BackupPass(app, config, depsFactory(app))
    }

    private fun execute(block: () -> Unit) {
        try {
            executor.execute {
                try {
                    block()
                } catch (t: Throwable) {
                    Report.exception(t)
                }
            }
        } catch (e: RuntimeException) {
            Report.exception(e)
        }
    }
}
