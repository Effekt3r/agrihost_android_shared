package za.co.mjrsolutions.shared.auth

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import at.favre.lib.crypto.bcrypt.BCrypt

internal object OfflineAuthStore {

    private const val DB_NAME = "agriauth.db"
    private const val DB_VERSION = 1
    private const val TABLE = "auth_users"
    private const val COL_USERNAME = "username"
    private const val COL_PASSWORD_HASH = "password_hash"
    private const val COL_SERVER_USER_ID = "server_user_id"
    private const val COL_NAME = "name"
    private const val COL_SURNAME = "surname"
    private const val COL_EMAIL = "email"
    private const val COL_LANGUAGE_CODE = "language_code"
    private const val COL_LANGUAGE_VERSION = "language_version"
    private const val COL_LAST_LOGIN_AT = "last_login_at"
    private const val OFFLINE_DAYS_LIMIT = 30L
    private const val BCRYPT_COST = 10

    private var dbHelper: DbHelper? = null

    fun init(context: Context) {
        dbHelper = DbHelper(context.applicationContext)
    }

    fun upsertUser(user: AuthUser, plainPassword: String) {
        val db = dbHelper?.writableDatabase ?: return
        val hash = BCrypt.with(BCrypt.Version.VERSION_2Y).hashToString(BCRYPT_COST, plainPassword.toCharArray())
        val values = ContentValues().apply {
            put(COL_USERNAME, user.username)
            put(COL_PASSWORD_HASH, hash)  // Store as String
            put(COL_SERVER_USER_ID, user.serverUserId)
            put(COL_NAME, user.name)
            put(COL_SURNAME, user.surname)
            put(COL_EMAIL, user.email)
            put(COL_LANGUAGE_CODE, user.languageCode)
            put(COL_LANGUAGE_VERSION, user.languageVersion)
            put(COL_LAST_LOGIN_AT, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun verifyOffline(username: String, plainPassword: String): AuthUser? {
        val db = dbHelper?.readableDatabase ?: return null
        val cursor = db.query(TABLE, null, "$COL_USERNAME = ?", arrayOf(username), null, null, null)
        cursor.use {
            if (!it.moveToFirst()) return null
            val hash = it.getString(it.getColumnIndexOrThrow(COL_PASSWORD_HASH))
            val lastLogin = it.getLong(it.getColumnIndexOrThrow(COL_LAST_LOGIN_AT))

            if (isExpired(lastLogin, System.currentTimeMillis())) return null

            val result = BCrypt.verifyer(BCrypt.Version.VERSION_2Y).verifyStrict(
                plainPassword.toCharArray(), hash.toCharArray()
            )
            if (!result.verified) return null

            return AuthUser(
                serverUserId = it.getString(it.getColumnIndexOrThrow(COL_SERVER_USER_ID)),
                username = username,
                name = it.getString(it.getColumnIndexOrThrow(COL_NAME)),
                surname = it.getString(it.getColumnIndexOrThrow(COL_SURNAME)),
                email = it.getString(it.getColumnIndexOrThrow(COL_EMAIL)),
                apiToken = "",
                specialInstructions = null,
                languageCode = it.getString(it.getColumnIndexOrThrow(COL_LANGUAGE_CODE)),
                languageVersion = it.getString(it.getColumnIndexOrThrow(COL_LANGUAGE_VERSION)),
                serverTime = null
            )
        }
    }

    fun getLastUsername(): String? {
        val db = dbHelper?.readableDatabase ?: return null
        val cursor = db.query(
            TABLE, arrayOf(COL_USERNAME), null, null, null, null,
            "$COL_LAST_LOGIN_AT DESC", "1"
        )
        cursor.use {
            return if (it.moveToFirst()) it.getString(0) else null
        }
    }

    /** True if a credential row already exists for [username]. */
    fun hasUser(username: String): Boolean {
        val db = dbHelper?.readableDatabase ?: return false
        db.query(TABLE, arrayOf(COL_USERNAME), "$COL_USERNAME = ?", arrayOf(username), null, null, null)
            .use { return it.moveToFirst() }
    }

    /**
     * Seed a credential row from a pre-computed BCrypt hash, but ONLY if no row exists
     * for this username yet. Used to migrate an existing legacy credential into agriauth.db
     * on first launch of the shared-auth build, so upgrades don't lock field users out of
     * offline login. Never overwrites a row written by a real online login (it is fresher).
     * @return true if a row was seeded; false if one already existed or the hash was blank.
     */
    fun seedWithHash(user: AuthUser, passwordHash: String): Boolean {
        if (passwordHash.isBlank()) return false
        val db = dbHelper?.writableDatabase ?: return false
        if (hasUser(user.username)) return false
        val values = ContentValues().apply {
            put(COL_USERNAME, user.username)
            put(COL_PASSWORD_HASH, passwordHash)
            put(COL_SERVER_USER_ID, user.serverUserId)
            put(COL_NAME, user.name)
            put(COL_SURNAME, user.surname)
            put(COL_EMAIL, user.email)
            put(COL_LANGUAGE_CODE, user.languageCode)
            put(COL_LANGUAGE_VERSION, user.languageVersion)
            put(COL_LAST_LOGIN_AT, System.currentTimeMillis())
        }
        return db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    /** Hash [plainPassword] with the same scheme used on online login, then seed-if-absent. */
    fun seedWithPlaintext(user: AuthUser, plainPassword: String): Boolean {
        if (plainPassword.isBlank()) return false
        if (hasUser(user.username)) return false
        val hash = BCrypt.with(BCrypt.Version.VERSION_2Y)
            .hashToString(BCRYPT_COST, plainPassword.toCharArray())
        return seedWithHash(user, hash)
    }

    fun clear() {
        dbHelper?.writableDatabase?.delete(TABLE, null, null)
    }

    internal fun isExpired(lastLoginMillis: Long, nowMillis: Long): Boolean {
        val limitMillis = OFFLINE_DAYS_LIMIT * 24 * 60 * 60 * 1000
        return (nowMillis - lastLoginMillis) > limitMillis
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE $TABLE (
                    $COL_USERNAME TEXT PRIMARY KEY,
                    $COL_PASSWORD_HASH TEXT NOT NULL,
                    $COL_SERVER_USER_ID TEXT NOT NULL,
                    $COL_NAME TEXT NOT NULL,
                    $COL_SURNAME TEXT NOT NULL,
                    $COL_EMAIL TEXT,
                    $COL_LANGUAGE_CODE TEXT,
                    $COL_LANGUAGE_VERSION TEXT,
                    $COL_LAST_LOGIN_AT INTEGER NOT NULL
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }
}
