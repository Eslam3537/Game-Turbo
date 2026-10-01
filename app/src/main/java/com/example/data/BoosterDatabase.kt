package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * Primary persistent Room database for Game Turbo.
 * CRITICAL: fallbackToDestructiveMigration() is strictly FORBIDDEN to prevent data loss across updates.
 * Every schema version bump must include an explicit Migration object.
 */
@Database(
    entities = [
        OptimizationLog::class,
        AddedGame::class,
        GameSessionRecord::class,
        DiagnosticEventRecord::class,
        SystemSnapshotRecord::class
    ],
    version = 3,
    exportSchema = true
)
abstract class BoosterDatabase : RoomDatabase() {
    abstract fun boosterDao(): BoosterDao

    companion object {
        private const val TAG = "BoosterDatabase"
        const val DB_NAME = "pubg_booster_database"
        const val DB_BACKUP_NAME = "pubg_booster_database.bak"
        const val CURRENT_VERSION = 3

        @Volatile
        private var INSTANCE: BoosterDatabase? = null

        /**
         * Migration from version 1 to version 2:
         * Adds system_snapshots table for baseline setting restore.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Executing Room migration 1 -> 2: creating system_snapshots table")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `system_snapshots` (
                        `commandId` TEXT NOT NULL PRIMARY KEY,
                        `settingNamespace` TEXT NOT NULL,
                        `settingKey` TEXT NOT NULL,
                        `originalValue` TEXT NOT NULL,
                        `isAbsentOriginally` INTEGER NOT NULL DEFAULT 0,
                        `appliedValue` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration from version 2 to version 3:
         * Adds extraMetadata column to system_snapshots table (Fix A11).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Executing Room migration 2 -> 3: adding extraMetadata to system_snapshots")
                db.execSQL("ALTER TABLE `system_snapshots` ADD COLUMN `extraMetadata` TEXT NOT NULL DEFAULT ''")
            }
        }

        val ALL_MIGRATIONS = arrayOf<Migration>(
            MIGRATION_1_2,
            MIGRATION_2_3
        )

        /**
         * Creates a backup copy of the database file ONLY right before a schema migration is needed (Fix A1).
         * Compares on-disk version with target database version; does NOT run on every open.
         */
        fun backupDatabaseIfMigrationNeeded(context: Context, targetVersion: Int) {
            try {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (dbFile.exists() && dbFile.length() > 0) {
                    var currentDiskVersion = 0
                    android.database.sqlite.SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                    ).use { db ->
                        currentDiskVersion = db.version
                    }
                    if (currentDiskVersion in 1 until targetVersion) {
                        val backupFile = File(dbFile.parentFile, DB_BACKUP_NAME)
                        dbFile.copyTo(backupFile, overwrite = true)
                        Log.i(TAG, "Pre-migration safety backup created (from v$currentDiskVersion to v$targetVersion): ${backupFile.absolutePath}")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Pre-migration database version check notice: ${e.message}")
            }
        }

        fun getDatabase(context: Context): BoosterDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext

                // Check version and only backup if migration is genuinely pending
                backupDatabaseIfMigrationNeeded(appContext, CURRENT_VERSION)

                val instance = Room.databaseBuilder(
                    appContext,
                    BoosterDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(*ALL_MIGRATIONS)
                .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
