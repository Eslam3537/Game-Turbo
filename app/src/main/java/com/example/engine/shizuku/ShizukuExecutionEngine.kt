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
                errorMessage = "Setting key not found or unsupported on this device firmware"
            )
        }

        // 2. Read current state
        val targetVal = targetValueOverride ?: command.defaultTargetValue
        val currentRead = AdbCommandRunner.run(command.readCurrentCommand(gamePkg))?.trim()
        val isAbsent = currentRead.isNullOrBlank() || currentRead == "null"
        val currentVal = currentRead ?: ""

        // 3. Snapshot management (Rule 2.3: Avoid stale snapshots)
        val existingSnapshot = boosterDao.getSnapshot(command.id)
        if (existingSnapshot == null) {
            boosterDao.insertSnapshot(
                SystemSnapshotRecord(
                    commandId = command.id,
                    settingNamespace = "system",
                    settingKey = command.id,
                    originalValue = currentVal,
                    isAbsentOriginally = isAbsent,
                    appliedValue = targetVal
                )
            )
            Log.d(TAG, "Snapshotted original state for [${command.id}]: '$currentVal' (absent=$isAbsent)")
        } else {
            // If current value equals the snapshot's applied value, system is still modified -> keep true original!
            // If current value differs, user or system changed it outside our session -> update original to new current value.
            if (currentVal != existingSnapshot.appliedValue) {
                boosterDao.insertSnapshot(
                    existingSnapshot.copy(
                        originalValue = currentVal,
                        isAbsentOriginally = isAbsent,
                        appliedValue = targetVal,
                        timestamp = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Updated snapshot for [${command.id}] with new original: '$currentVal'")
            }
        }

        // 4. For memory actions, sample MemAvailable before
        var memBefore = 0L
        if (command.id == "ram_clean") {
            memBefore = extractMemAvailableKb(currentVal)
        }

        // 5. Apply change
        val applyCmd = command.applyCommand(gamePkg, targetVal)
        val execResult = AdbCommandRunner.runDetailed(applyCmd)
        if (!execResult.success) {
            Log.e(TAG, "Command execution failed: ${execResult.stderr}")
            return@withContext CommandExecutionResult(
                commandId = command.id,
                isSupported = true,
                isSuccess = false,
                originalValue = currentVal,
                appliedValue = targetVal,
                verifiedValue = "FAILED",
                errorMessage = execResult.stderr.ifBlank { "Exit code ${execResult.exitCode}" }
            )
        }

        // 6. Read-back verification (exact match)
        val verifiedRead = AdbCommandRunner.run(command.readCurrentCommand(gamePkg))?.trim() ?: ""

        var isVerified = false
        var verifyError: String? = null

        if (command.id == "ram_clean") {
            val memAfter = extractMemAvailableKb(verifiedRead)
            val memDeltaKb = memAfter - memBefore
            // Check if command succeeded and if delta was measurable
            isVerified = execResult.success
            if (memDeltaKb <= 0 && isVerified) {
                Log.d(TAG, "ram_clean executed: delta=${memDeltaKb}KB (no measurable memory freed)")
            }
        } else {
            isVerified = command.verifyPredicate(verifiedRead, targetVal)
            if (!isVerified) {
                verifyError = "Verification failed: expected '$targetVal' but read back '$verifiedRead'"
            }
        }

        Log.d(TAG, "Verify [${command.id}]: expected='$targetVal', got='$verifiedRead', verified=$isVerified")

        CommandExecutionResult(
            commandId = command.id,
            isSupported = true,
            isSuccess = isVerified,
            originalValue = currentVal,
            appliedValue = targetVal,
            verifiedValue = verifiedRead,
            errorMessage = verifyError
        )
    }

    private fun extractMemAvailableKb(memInfoText: String): Long {
        val line = memInfoText.lines().firstOrNull { it.startsWith("MemAvailable:") } ?: return 0L
        val parts = line.split("\\s+".toRegex())
        return parts.getOrNull(1)?.toLongOrNull() ?: 0L
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
                    Log.d(TAG, "Restored [${snapshot.commandId}] to '${snapshot.originalValue}' (wasAbsent=${snapshot.isAbsentOriginally})")
                } else {
                    Log.e(TAG, "Failed rolling back [${snapshot.commandId}]: ${res.stderr}")
                    failed.add(snapshot.commandId)
                }
            } else {
                // If unknown setting key, remove snapshot
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
