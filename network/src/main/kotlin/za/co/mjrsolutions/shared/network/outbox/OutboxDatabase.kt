package za.co.mjrsolutions.shared.network.outbox

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OutboxEntry::class], version = 1, exportSchema = false)
abstract class OutboxDatabase : RoomDatabase() {

    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile private var INSTANCE: OutboxDatabase? = null

        fun get(context: Context): OutboxDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OutboxDatabase::class.java,
                    "agri_outbox.db"
                ).build().also { INSTANCE = it }
            }

        /** Close and clear the singleton so tests that call AgriNetwork.init() get a fresh DB. */
        fun resetForTest() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
