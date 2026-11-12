package za.co.mjrsolutions.shared.files.db

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.files.AgriFiles
import za.co.mjrsolutions.shared.files.FilesConfig
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AgriDbExportTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: SQLiteDatabase
    private val user = ExportUser("Willie Snr", "williesnr", "65")

    @Before
    fun setUp() {
        AgriFiles.init(app, FilesConfig("mr", "AGRIHOST_TEST_mr", { "williesnr" }, { "Willie Snr" }, "9.0.2", 9002, "za.co.x.agrihost",
            userServerIdProvider = { "65" }, dbVersionProvider = { 26 }))
        db = app.openOrCreateDatabase("agrihost_mr", 0, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS user (id INTEGER, username TEXT, password TEXT)")
        db.execSQL("DELETE FROM user")
        db.execSQL("INSERT INTO user VALUES (65, 'williesnr', 's3cret')")
    }

    @After
    fun tearDown() {
        db.close()
        AgriFiles.resetForTest()
    }

    private fun spec(scrub: List<String> = listOf("UPDATE user SET password = ''")) = DbExportSpec(
        database = { db },
        dbEntryName = "agrihost_mr.db",
        scrubSql = scrub,
        infoText = { _, u -> "Agrihost database export\nUser: ${u?.fullName}\n" },
        extraEntries = { mapOf("section_flags.txt" to "a\t1\n") }
    )

    @Test
    fun `zipHoldsDbInfoAndExtras - hael's zip layout, in order`() {
        val zip = AgriDbExport.export(app, spec(), user)
        assertTrue(zip.name.startsWith("agrihost_db_AGRIHOST_TEST_mr_Willie_Snr_65_"), zip.name)
        assertTrue(zip.name.endsWith(".zip"))
        assertEquals(File(app.filesDir, "db_export"), zip.parentFile)
        ZipFile(zip).use { z ->
            assertEquals(listOf("agrihost_mr.db", "export_info.txt", "section_flags.txt"), z.entries().toList().map { it.name })
            assertEquals("Agrihost database export\nUser: Willie Snr\n",
                z.getInputStream(z.getEntry("export_info.txt")).readBytes().toString(Charsets.UTF_8))
        }
    }

    @Test
    fun `scrubbedColumnBlank - the login password never leaves the tablet`() {
        val zip = AgriDbExport.export(app, spec(), user)
        val out = File(app.cacheDir, "unzipped.db")
        ZipFile(zip).use { z -> out.writeBytes(z.getInputStream(z.getEntry("agrihost_mr.db")).readBytes()) }
        val copy = SQLiteDatabase.openDatabase(out.path, null, SQLiteDatabase.OPEN_READONLY)
        copy.rawQuery("SELECT password FROM user", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
        }
        copy.close()
        db.rawQuery("SELECT password FROM user", null).use { c -> c.moveToFirst(); assertEquals("s3cret", c.getString(0)) }
    }

    @Test
    fun `scrubFailureFailsExport - no zip goes out with the secret in it`() {
        assertFailsWith<IOException> { AgriDbExport.export(app, spec(listOf("UPDATE nosuchtable SET x = ''")), user) }
        val left = File(app.filesDir, "db_export").listFiles { _, n -> n.endsWith(".zip") || n.endsWith(".part") }
        assertTrue(left.isNullOrEmpty())
    }

    @Test
    fun `nameNeverReused - an import backup in the same second keeps both`() {
        val a = AgriDbExport.exportBackup(app, spec(), user)
        val b = AgriDbExport.exportBackup(app, spec(), user)
        assertTrue(a.name.startsWith("agrihost_db_backup_AGRIHOST_TEST_mr_"))
        assertFalse(a.name == b.name)
        assertTrue(a.exists() && b.exists())
    }

    @Test
    fun `menuExportReplacesThePrevious`() {
        val first = AgriDbExport.export(app, spec(), user)
        val second = AgriDbExport.export(app, spec(), user)
        assertFalse(first.exists() && first != second)
        assertTrue(second.exists())
    }

    @Test
    fun `uploadedMarkerSkips - only zips not sent yet are pending`() {
        val sent = AgriDbExport.exportBackup(app, spec(), user)
        File(sent.path + ".uploaded").createNewFile()
        val waiting = AgriDbExport.exportBackup(app, spec(), user)
        assertEquals(listOf(waiting), AgriDbExport.pendingZips(app))
    }

    @Test
    fun `kindOf - the console's labels`() {
        assertEquals("export", AgriDbExport.kindOf("agrihost_db_AGRIHOST_TEST_x_1.zip"))
        assertEquals("pre_import_backup", AgriDbExport.kindOf("agrihost_db_backup_AGRIHOST_TEST_x.zip"))
        assertEquals("pre_receive_order_backup", AgriDbExport.kindOf("agrihost_db_receive_AGRIHOST_TEST_x.zip"))
        assertEquals("pre_send_backup", AgriDbExport.kindOf("agrihost_db_send_AGRIHOST_TEST_x.zip"))
    }

    @Test
    fun `logDoc - hael's db_exports document fields`() {
        val zip = AgriDbExport.export(app, spec(), user)
        val doc = AgriDbExport.logDoc(zip, "db_exports/AGRIHOST_TEST_mr/williesnr/${zip.name}")
        assertEquals(setOf("kind", "client", "applicationId", "username", "userName", "userServerId", "appVersion",
            "versionCode", "dbVersion", "fileName", "storagePath", "sizeBytes", "exportedAtMs", "device", "android",
            "createdAt"), doc.keys)
        assertEquals("AGRIHOST_TEST_mr", doc["client"])
        assertEquals("williesnr", doc["username"])
        assertEquals("Willie Snr", doc["userName"])
        assertEquals("65", doc["userServerId"])
        assertEquals(26, doc["dbVersion"])
        assertEquals(9002L, doc["versionCode"])
        assertEquals("export", doc["kind"])
    }
}
