package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        OptimizationLog::class,
        AddedGame::class,
        GameSessionRecord::class,
        DiagnosticEventRecord::class,
        SystemSnapshotRecord::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BoosterDatabase : RoomDatabase() {
    abstract fun boosterDao(): BoosterDao

    companion object {
        @Volatile
        private var INSTANCE: BoosterDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure system_snapshots table exists without wiping data
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

        fun getDatabase(context: Context): BoosterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BoosterDatabase::class.java,
                    "pubg_booster_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
