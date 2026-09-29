package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "optimization_logs")
data class OptimizationLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val commandName: String,
    val commandText: String,
    val status: String, // SUCCESS, FAILED, ROLLBACK
    val responseMsg: String,
    val executionTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "booster_games")
data class AddedGame(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val packageName: String,
    val isCustom: Boolean = false,
    val perfProfile: String = "balanced", // balanced, performance, battery, competitive
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "game_sessions")
data class GameSessionRecord(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val gamePackage: String,
    val gameName: String,
    val startTime: Long,
    val endTime: Long = 0,
    val durationSeconds: Long = 0,
    val profileApplied: String,
    val baselineRttMs: Int? = null,
    val baselineJitterMs: Int? = null,
    val baselineTempC: Int? = null,
    val avgRttMs: Int? = null,
    val maxRttMs: Int? = null,
    val avgJitterMs: Int? = null,
    val peakTempC: Int? = null,
    val avgFps: Int? = null,
    val frameSpikesCount: Int = 0,
    val rttSpikesCount: Int = 0,
    val thermalThrottlingDetected: Boolean = false,
    val appliedOptimizationsCount: Int = 0,
    val verifiedOptimizationsCount: Int = 0,
    val failedOptimizationsCount: Int = 0,
    val rollbackSuccess: Boolean = true,
    val summaryVerdict: String = "MEASURED_STABLE" // IMPROVED, STABLE, DEGRADED, NO_SIGNIFICANT_CHANGE, INSUFFICIENT_DATA
)

@Entity(tableName = "diagnostic_events")
data class DiagnosticEventRecord(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val sessionId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // THERMAL, NETWORK_SPIKE, OPTIMIZATION, ROLLBACK, SAFE_MODE, DND
    val title: String,
    val description: String,
    val severity: String = "INFO" // INFO, WARNING, CRITICAL
)

@Entity(tableName = "system_snapshots")
data class SystemSnapshotRecord(
    @PrimaryKey val commandId: String,
    val settingNamespace: String, // "system", "global", "secure", "cmd"
    val settingKey: String,
    val originalValue: String,
    val isAbsentOriginally: Boolean = false,
    val appliedValue: String,
    val timestamp: Long = System.currentTimeMillis()
)
