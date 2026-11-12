package za.co.mjrsolutions.shared.files.db

import android.database.sqlite.SQLiteDatabase
import java.util.Date

/** Who an export is for: the zip's name tag and its info text. */
class ExportUser(
    @JvmField val fullName: String?,
    @JvmField val username: String?,
    @JvmField val serverId: String?
)

/**
 * What the app's database export holds (hael's DatabaseExporter, shared):
 * @param database the app's live writable database; its write lock is held while it is copied
 * @param dbEntryName the database's name inside the zip (hael: demo_agrihost_hael.db)
 * @param scrubSql statements run on the copy (with secure_delete on) to remove login secrets; if
 *        one fails, the export fails - it never goes out with them in it
 * @param infoText export_info.txt (hael's importer reads its "Key: value" lines back)
 * @param extraEntries further text files, by name (hael: section_flags.txt)
 */
class DbExportSpec @JvmOverloads constructor(
    val database: () -> SQLiteDatabase,
    val dbEntryName: String,
    val scrubSql: List<String>,
    val infoText: (Date, ExportUser?) -> String,
    val extraEntries: () -> Map<String, String> = { emptyMap() }
)
