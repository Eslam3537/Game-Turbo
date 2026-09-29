package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BoosterDao {
    // Optimization Logs
    @Query("SELECT * FROM optimization_logs ORDER BY executionTime DESC LIMIT 100")
    fun getAllLogs(): Flow<List<OptimizationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: OptimizationLog)

    @Query("DELETE FROM optimization_logs")
    suspend fun clearAllLogs()

    // Added Games
    @Query("SELECT * FROM booster_games ORDER BY addedTime DESC")
    fun getAllGames(): Flow<List<AddedGame>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: AddedGame)

    @Query("DELETE FROM booster_games WHERE id = :gameId")
    suspend fun deleteGame(gameId: Int)

    @Query("DELETE FROM booster_games")
    suspend fun clearAllGames()

    // Game Sessions
    @Query("SELECT * FROM game_sessions ORDER BY startTime DESC LIMIT 50")
    fun getAllSessions(): Flow<List<GameSessionRecord>>

    @Query("SELECT * FROM game_sessions WHERE sessionId = :id")
    suspend fun getSessionById(id: Long): GameSessionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: GameSessionRecord): Long

    @Update
    suspend fun updateSession(session: GameSessionRecord)

    // Diagnostic Events
    @Query("SELECT * FROM diagnostic_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getEventsForSession(sessionId: Long): Flow<List<DiagnosticEventRecord>>

    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC LIMIT 100")
    fun getRecentEvents(): Flow<List<DiagnosticEventRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DiagnosticEventRecord)

    // System Snapshots for Verified Rollback
    @Query("SELECT * FROM system_snapshots")
    suspend fun getAllSnapshots(): List<SystemSnapshotRecord>

    @Query("SELECT * FROM system_snapshots WHERE commandId = :commandId")
    suspend fun getSnapshot(commandId: String): SystemSnapshotRecord?

    @Query("SELECT COUNT(*) FROM system_snapshots")
    suspend fun getSnapshotCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnapshotIfNotExists(snapshot: SystemSnapshotRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SystemSnapshotRecord)

    @Query("DELETE FROM system_snapshots WHERE commandId = :commandId")
    suspend fun deleteSnapshot(commandId: String)

    @Query("DELETE FROM system_snapshots")
    suspend fun clearAllSnapshots()
}
