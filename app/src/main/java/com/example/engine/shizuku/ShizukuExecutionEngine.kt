package com.example.engine.shizuku

import android.util.Log
import com.example.data.AdbCommandRunner
import com.example.data.BoosterDao
import com.example.data.SystemSnapshotRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CommandExecutionResult(
    val commandId: String,
    val isSupported: Boolean,
    val isSuccess: Boolean,
    val originalValue: String,
    val appliedValue: String,
    val verifiedValue: String,
    val errorMessage: String? = null
)

data class RollbackReport(
    val totalSnapshots: Int,
    val restoredCount: Int,
    val failedCommands: List<String>,
    val isFullyRestored: Boolean
)

class ShizukuExecutionEngine(
    private val boosterDao: BoosterDao
) {
    private val TAG = "ShizukuExecutionEngine"

    /**
     * Probes whether this command is supported on the current device.
     */
    suspend fun probeCommand(command: SystemTuningCommand): Boolean = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) return@withContext false
        val probeRes = AdbCommandRunner.runDetailed(command.probeCommand)
        probeRes.success
    }

    /**
     * Executes a command with full snapshotting, verification, and zero simulation.
     */
    suspend fun applyAndVerify(
        command: SystemTuningCommand,
        gamePkg: String,
        targetValueOverride: String? = null
    ): CommandExecutionResult = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) {
            return@withContext CommandExecutionResult(
                commandId = command.id,
                isSupported = false,
                isSuccess = false,
                originalValue = "N/A",
                appliedValue = "N/A",
                verifiedValue = "N/A",
                errorMessage = "Shizuku privileged binder is not available or unauthorized"
            )
        }

        // 1. Probe availability
        val isSupported = probeCommand(command)
        if (!isSupported) {
            Log.w(TAG, "Command ${command.id} unsupported on this device environment")
            return@withContext CommandExecutionResult(
                commandId = command.id,
                isSupported = false,
                isSuccess = false,
                originalValue = "N/A",
                appliedValue = "N/A",
                verifiedValue = "UNSUPPORTED",
                errorMessage = "Setting key not found on this device firmware"
            )
        }

        // 2. Read original state before first modification
        val targetVal = targetValueOverride ?: command.defaultTargetValue
        val originalRead = AdbCommandRunner.run(command.readCurrentCommand(gamePkg))?.trim()
        val isAbsent = originalRead.isNullOrBlank() || originalRead == "null"
        val originalVal = originalRead ?: ""

        // 3. Snapshot original state in Room (NEVER overwrite existing snapshot with modified value!)
        val existingSnapshot = boosterDao.getSnapshot(command.id)
        if (existingSnapshot == null) {
            boosterDao.insertSnapshot(
                SystemSnapshotRecord(
                    commandId = command.id,
                    settingNamespace = "system",
                    settingKey = command.id,
                    originalValue = originalVal,
                    isAbsentOriginally = isAbsent,
                    appliedValue = targetVal
                )
            )
            Log.d(TAG, "Snapshotted original state for [${command.id}]: '$originalVal' (absent=$isAbsent)")
        }

        // 4. Apply change
        val applyCmd = command.applyCommand(gamePkg, targetVal)
        val execResult = AdbCommandRunner.runDetailed(applyCmd)
        if (!execResult.success) {
            Log.e(TAG, "Command execution failed: ${execResult.stderr}")
            return@withContext CommandExecutionResult(
                commandId = command.id,
                isSupported = true,
                isSuccess = false,
                originalValue = originalVal,
                appliedValue = targetVal,
                verifiedValue = "FAILED",
                errorMessage = execResult.stderr.ifBlank { "Exit code ${execResult.exitCode}" }
            )
        }

        // 5. Read-back verification
        val verifiedRead = AdbCommandRunner.run(command.readCurrentCommand(gamePkg))?.trim() ?: ""
        val isVerified = command.verifyPredicate(verifiedRead, targetVal)
        Log.d(TAG, "Verify [${command.id}]: expected='$targetVal', got='$verifiedRead', verified=$isVerified")

        CommandExecutionResult(
            commandId = command.id,
            isSupported = true,
            isSuccess = isVerified,
            originalValue = originalVal,
            appliedValue = targetVal,
            verifiedValue = verifiedRead,
            errorMessage = if (!isVerified) "Verification failed: expected '$targetVal' but read back '$verifiedRead'" else null
        )
    }

    /**
     * Rolls back all stored snapshots to their verified original baseline.
     */
    suspend fun rollbackAll(gamePkg: String = "com.tencent.ig"): RollbackReport = withContext(Dispatchers.IO) {
        val snapshots = boosterDao.getAllSnapshots()
        var restoredCount = 0
        val failed = mutableListOf<String>()

        if (!AdbCommandRunner.isAvailable()) {
            return@withContext RollbackReport(
                totalSnapshots = snapshots.size,
                restoredCount = 0,
                failedCommands = snapshots.map { it.commandId },
                isFullyRestored = snapshots.isEmpty()
            )
        }

        for (snapshot in snapshots) {
            val cmdDef = CommandRegistry.getAllTuningCommands().find { it.id == snapshot.commandId }
            if (cmdDef != null) {
                val rollbackCmd = cmdDef.rollbackCommand(gamePkg, snapshot.originalValue, snapshot.isAbsentOriginally)
                val res = AdbCommandRunner.runDetailed(rollbackCmd)
                if (res.success) {
                    restoredCount++
                    boosterDao.deleteSnapshot(snapshot.commandId)
                    Log.d(TAG, "Restored [${snapshot.commandId}] to '${snapshot.originalValue}'")
                } else {
                    Log.e(TAG, "Failed rolling back [${snapshot.commandId}]: ${res.stderr}")
                    failed.add(snapshot.commandId)
                }
            } else {
                // Unknown command definition, remove snapshot to prevent dangling
                boosterDao.deleteSnapshot(snapshot.commandId)
            }
        }

        RollbackReport(
            totalSnapshots = snapshots.size,
            restoredCount = restoredCount,
            failedCommands = failed,
            isFullyRestored = failed.isEmpty() && restoredCount == snapshots.size
        )
    }
}
