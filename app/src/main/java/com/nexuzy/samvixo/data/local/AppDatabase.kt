package com.nexuzy.samvixo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexuzy.samvixo.data.local.dao.CallLogDao
import com.nexuzy.samvixo.data.local.dao.ChatDao
import com.nexuzy.samvixo.data.local.dao.MessageDao
import com.nexuzy.samvixo.data.local.dao.UserDao
import com.nexuzy.samvixo.data.local.entity.CallLogEntity
import com.nexuzy.samvixo.data.local.entity.ChatEntity
import com.nexuzy.samvixo.data.local.entity.MessageEntity
import com.nexuzy.samvixo.data.local.entity.UserEntity

/**
 * AppDatabase — version 2
 *
 * Migration 1 → 2:
 *   - messages table: added columns deleteAfter, isDeletedFromServer, mediaUrl, replyToMessageId
 *   - call_logs table: new table for local call history
 *
 * Uses explicit Migration(1,2) — safe for Play Store updates,
 * existing message/chat/user data is preserved.
 */
@Database(
    entities = [
        MessageEntity::class,
        ChatEntity::class,
        UserEntity::class,
        CallLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun userDao(): UserDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from v1 to v2:
         *   - Add new columns to messages (default values ensure existing rows are valid)
         *   - Create call_logs table
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to messages table
                database.execSQL("ALTER TABLE messages ADD COLUMN deleteAfter INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE messages ADD COLUMN isDeletedFromServer INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE messages ADD COLUMN mediaUrl TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE messages ADD COLUMN replyToMessageId TEXT NOT NULL DEFAULT ''")

                // Create call_logs table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS call_logs (
                        callId TEXT NOT NULL PRIMARY KEY,
                        chatId TEXT NOT NULL DEFAULT '',
                        peerId TEXT NOT NULL DEFAULT '',
                        peerName TEXT NOT NULL DEFAULT '',
                        peerAvatarUrl TEXT NOT NULL DEFAULT '',
                        isIncoming INTEGER NOT NULL DEFAULT 0,
                        isVideo INTEGER NOT NULL DEFAULT 0,
                        startTime INTEGER NOT NULL DEFAULT 0,
                        endTime INTEGER NOT NULL DEFAULT 0,
                        durationSeconds INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'COMPLETED',
                        timestamp INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Create indexes for call_logs
                database.execSQL("CREATE INDEX IF NOT EXISTS index_call_logs_chatId ON call_logs(chatId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_call_logs_timestamp ON call_logs(timestamp)")

                // Create indexes for messages (if not already present)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chatId ON messages(chatId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_status ON messages(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_timestamp ON messages(timestamp)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "samvixo_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
