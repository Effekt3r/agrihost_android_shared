package za.co.mjrsolutions.shared.files.internal

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import za.co.mjrsolutions.shared.files.db.DbExportSpec
import za.co.mjrsolutions.shared.files.db.ExportUser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs the app's database into one zip (hael's DatabaseExporter, shared): a consistent copy of
 * the live database and its write-ahead log taken under the write lock, folded into one .db,
 * login secrets removed from the copy, plus the info text and the app's extra files. Written to a
 * .part file and renamed when complete, so an upload never picks up half a zip.
 */
internal object DbZipper {

    const val INFO_ENTRY = "export_info.txt"
    const val PART_SUFFIX = ".part"

    /** Two exports at once would share a folder's temporary copy of the database. */
    private val LOCK = Any()

    fun export(context: Context, spec: DbExportSpec, user: ExportUser?, clientId: String, dirName: String,
               prefix: String, replacePrevious: Boolean): File = synchronized(LOCK) {
        val dir = File(context.filesDir, dirName)
        if (replacePrevious) deleteRecursively(dir)
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("Could not create $dir")
        // Exports run one at a time, so a .part here is from one that died part way.
        dir.listFiles { _, name -> name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }

        val now = Date()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val dbCopy = File(dir, spec.dbEntryName)
        val walCopy = File(dbCopy.path + "-wal")
        val base = prefix + clientId + "_" + fileNameTag(user) + "_" + stamp
        var zip = File(dir, "$base.zip")
        var n = 2
        while (zip.exists()) zip = File(dir, base + "_" + n++ + ".zip") // a kept backup is never overwritten
        val part = File(zip.path + PART_SUFFIX)
        try {
            copyLiveDatabase(spec.database(), dbCopy, walCopy)
            consolidate(dbCopy)
            scrub(dbCopy, spec.scrubSql)
            val extras = spec.extraEntries()
            ZipOutputStream(FileOutputStream(part)).use { out ->
                addFile(out, dbCopy)
                // Consolidating failed: SQLite replays this log when it sits beside the .db.
                if (walCopy.exists()) addFile(out, walCopy)
                addText(out, INFO_ENTRY, spec.infoText(now, user))
                extras.forEach { (name, text) -> addText(out, name, text) }
            }
            if (!part.renameTo(zip)) throw IOException("Could not finish $zip")
            return zip
        } catch (e: IOException) {
            part.delete(); zip.delete()
            throw e
        } catch (e: RuntimeException) {
            part.delete(); zip.delete()
            throw e
        } finally {
            dbCopy.delete()
            walCopy.delete()
            File(dbCopy.path + "-shm").delete()
            File(dbCopy.path + "-journal").delete()
        }
    }

    /**
     * Copies the live database file and its write-ahead log inside a transaction: the transaction
     * holds the write lock, so no save lands half way through the copy; reads carry on.
     */
    private fun copyLiveDatabase(db: SQLiteDatabase, dbCopy: File, walCopy: File) {
        val live = File(db.path)
        val liveWal = File(live.path + "-wal")
        db.beginTransaction()
        try {
            copy(live, dbCopy)
            if (liveWal.exists() && liveWal.length() > 0) copy(liveWal, walCopy)
        } finally {
            db.endTransaction() // nothing was written - this only releases the lock
        }
    }

    /** Folds a copied write-ahead log into the copy and takes it out of WAL mode (one self-contained .db). */
    private fun consolidate(dbCopy: File) {
        var copy: SQLiteDatabase? = null
        try {
            copy = SQLiteDatabase.openDatabase(dbCopy.path, null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS) { }
            copy.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
        } catch (e: Exception) {
            Report.exception(e)
        } finally {
            copy?.close()
        }
    }

    /** Runs the app's secret-removing statements on the copy with secure_delete, or fails the export. */
    private fun scrub(dbCopy: File, statements: List<String>) {
        if (statements.isEmpty()) return
        var copy: SQLiteDatabase? = null
        try {
            copy = SQLiteDatabase.openDatabase(dbCopy.path, null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS) { }
            copy.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() }
            statements.forEach { copy.execSQL(it) }
        } catch (e: RuntimeException) {
            throw IOException("Could not remove the login password from the export", e)
        } finally {
            copy?.close()
        }
    }

    /** Who sent it, readable in a chat or mail attachment list: name and server id (max 40). */
    private fun fileNameTag(user: ExportUser?): String {
        var tag = if (user == null) "" else ((user.fullName ?: "") + "_" + (user.serverId ?: ""))
            .replace(Regex("[^A-Za-z0-9]+"), "_").replace(Regex("^_+|_+$"), "")
        if (tag.length > 40) tag = tag.substring(0, 40)
        return tag.ifEmpty { "unknown" }
    }

    private fun addFile(out: ZipOutputStream, file: File) {
        out.putNextEntry(ZipEntry(file.name))
        FileInputStream(file).use { pipe(it, out) }
        out.closeEntry()
    }

    private fun addText(out: ZipOutputStream, name: String, text: String) {
        out.putNextEntry(ZipEntry(name))
        out.write(text.toByteArray(Charsets.UTF_8))
        out.closeEntry()
    }

    private fun copy(from: File, to: File) {
        FileInputStream(from).use { input -> FileOutputStream(to).use { output -> pipe(input, output) } }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
        }
    }

    fun deleteRecursively(file: File) {
        file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }
}
