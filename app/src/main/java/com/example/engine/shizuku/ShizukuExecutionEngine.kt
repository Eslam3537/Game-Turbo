package com.example.engine.shizuku

import android.util.Log
import com.example.data.AdbCommandRunner
import com.example.data.BoosterDao
import com.example.data.CommandSource
import com.example.data.SystemSnapshotRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class CommandExecutionResult(
    val commandId: String,
    val isSupported: Boolean,
    val isSuccess: Boolean,
    val verificationLevel: VerificationLevel,
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

    // Cache settings keys per namespace to prevent repeated shell calls during probing
    private val cachedSettingsKeys = mutableMapOf<String, Set<String>>()

    private suspend fun getSettingsKeysForNamespace(namespace: String): Set<String> {
        cachedSettingsKeys[namespace]?.let { return it }
        val out = AdbCommandRunner.run("settings list $namespace", source = CommandSource.REPORT) ?: ""
        val keys = out.lines().mapNotNull { line ->
            val eqIdx = line.indexOf('=')
            if (eqIdx > 0) line.substring(0, eqIdx).trim() else null
        }.toSet()
        if (keys.isNotEmpty()) {
            cachedSettingsKeys[namespace] = keys
        }
        return keys
    }

    /**
     * Probes whether this command is genuinely supported on the current ROM (Fix A12).
     */
    suspend fun probeCommand(command: SystemTuningCommand): Boolean = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) return@withContext false

        if (command.settingNamespace in listOf("system", "global", "secure")) {
            val keys = getSettingsKeysForNamespace(command.settingNamespace)
            if (keys.contains(command.settingKey)) {
                return@withContext true
            }
            // Whitelist keys that AOSP defines and honors even if not listed in initial settings list
            val knownAospKeys = setOf(
                "pointer_speed",
                "window_animation_scale",
                "transition_animation_scale",
                "animator_duration_scale",
                "peak_refresh_rate"
            )
            return@withContext knownAospKeys.contains(command.settingKey)
        }

        // For cmd or binary commands (deviceidle, am kill-all)
        val probeRes = AdbCommandRunner.runDetailed(command.probeCommand, source = CommandSource.REPORT)
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
                verificationLevel = VerificationLevel.UNVERIFIABLE,
                originalValue = "N/A",
                appliedValue = "N/A",
                verifiedValue = "N/A",
                errorMessage = "Shizuku privileged binder is not available or unauthorized"
            )
        }

        // 1. Probe availability (Fix A12)
        val isSupported = probeCommand(command)
        if (!isSupported) {
            Log.w(TAG, "Command ${command.id} unsupported on this device environment")
            return@withContext CommandExecutionResult(
                commandId = command.id,
                isSupported = false,
                isSuccess = false,
                verificationLevel = VerificationLevel.UNVERIFIABLE,
                originalValue = "N/A",
                appliedValue = "N/A",
                verifiedValue = "UNSUPPORTED",
                errorMessage = "Setting key '${command.settingKey}' not found or unsupported on this device firmware"
            )
        }

        val targetVal = targetValueOverride ?: command.defaultTargetValue

        // 2. Read current state
        val currentRead = AdbCommandRunner.run(command.readCurrentCommand(gamePkg), source = CommandSource.USER_ACTION)?.trim() ?: ""
        val isAbsent = currentRead.isBlank() || currentRead == "null" || currentRead == "MODE=null"

        // For specific commands, format original snapshot accurately (Fix A11)
        val snapshotOriginalValue = when (command.id) {
            "doze_whitelist" -> {
                CommandRegistry.isPackageInWhitelist(currentRead, gamePkg).toString()
            }
            "ram_clean" -> {
                "" // Do not store memory dumps in snapshot table (Fix A11)
            }
            else -> currentRead
        }

        // 3. Snapshot management (Fix A11)
        if (command.id != "ram_clean") {
            val existingSnapshot = boosterDao.getSnapshot(command.id)
            if (existingSnapshot == null) {
                boosterDao.insertSnapshot(
                    SystemSnapshotRecord(
                        commandId = command.id,
                        settingNamespace = command.settingNamespace,
                        settingKey = command.settingKey,
                        originalValue = snapshotOriginalValue,
                        isAbsentOriginally = isAbsent,
                        appliedValue = targetVal
                    )
                )
                Log.d(TAG, "Snapshotted original state for [${command.id}]: '$snapshotOriginalValue' (absent=$isAbsent)")
            } else {
                // Fix A11: Check if system is still in modified state via command.isInModifiedState
                val isStillModified = command.isInModifiedState(currentRead, targetVal, gamePkg)
                if (!isStillModified) {
                    // User or system changed it outside session -> update snapshot with genuine original
                    boosterDao.insertSnapshot(
                        existingSnapshot.copy(
                            originalValue = snapshotOriginalValue,
                            isAbsentOriginally = isAbsent,
                            appliedValue = targetVal,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    Log.d(TAG, "Updated snapshot for [${command.id}] with new original: '$snapshotOriginalValue'")
                }
            }
        }

        // 4. Pre-apply measurements for effect-based verification
        var initialRefreshRate: Float? = null
        if (command.id == "peak_refresh_rate") {
            initialRefreshRate = parseActiveDisplayRefreshRate()
        }

        val memBeforeSamples = mutableListOf<Long>()
        if (command.id == "ram_clean") {
            // Fix A4: Read MemAvailable 3 times before
            for (i in 0 until 3) {
                readMemAvailableKb()?.let { memBeforeSamples.add(it) }
                delay(50)
            }
        }

        // 5. Apply change
        val applyCmd = command.applyCommand(gamePkg, targetVal)
        val execResult = AdbCommandRunner.runDetailed(applyCmd, source = CommandSource.USER_ACTION)
        if (!execResult.success) {
            Log.e(TAG, "Command execution failed: ${execResult.stderr}")
            return@withContext CommandExecutionResult(
                commandId = command.id,
                isSupported = true,
                isSuccess = false,
                verificationLevel = VerificationLevel.NO_EFFECT,
                originalValue = snapshotOriginalValue,
                appliedValue = targetVal,
                verifiedValue = "FAILED",
                errorMessage = execResult.stderr.ifBlank { "Exit code ${execResult.exitCode}" }
            )
        }

        // 6. Post-apply measurements & wait
        var finalRefreshRate: Float? = null
        val memAfterSamples = mutableListOf<Long>()

        if (command.id == "ram_clean") {
            // Fix A4: Wait 1.5s after am kill-all, then read 3 times
            delay(1500)
            for (i in 0 until 3) {
                readMemAvailableKb()?.let { memAfterSamples.add(it) }
                delay(50)
            }
        } else if (command.id == "peak_refresh_rate") {
            delay(200)
            finalRefreshRate = parseActiveDisplayRefreshRate()
        }

        val initialMemMedian = if (memBeforeSamples.isNotEmpty()) memBeforeSamples.sorted()[memBeforeSamples.size / 2] else null
        val finalMemMedian = if (memAfterSamples.isNotEmpty()) memAfterSamples.sorted()[memAfterSamples.size / 2] else null

        // 7. Read-back verification
        val verifiedRead = AdbCommandRunner.run(command.readCurrentCommand(gamePkg), source = CommandSource.USER_ACTION)?.trim() ?: ""

        val verifyCtx = VerifyContext(
            gamePkg = gamePkg,
            targetValue = targetVal,
            initialDisplayRefreshRate = initialRefreshRate,
            finalDisplayRefreshRate = finalRefreshRate,
            initialMemAvailableKb = initialMemMedian,
            finalMemAvailableKb = finalMemMedian
        )

        val level = command.verify(verifiedRead, verifyCtx)
        val isSuccess = level == VerificationLevel.STORED || level == VerificationLevel.EFFECT_CONFIRMED

        val verifyError = if (!isSuccess) {
            when (level) {
                VerificationLevel.NO_EFFECT -> {
                    if (command.id == "ram_clean") {
                        val deltaMb = if (initialMemMedian != null && finalMemMedian != null) (finalMemMedian - initialMemMedian) / 1024L else 0L
                        "Executed successfully, but cached RAM freed ($deltaMb MB) was below 50 MB threshold"
                    } else if (command.id == "peak_refresh_rate") {
                        "Setting stored, but display refresh rate did not change (not honored by this ROM)"
                    } else {
                        "Value was not retained in system settings"
                    }
                }
                VerificationLevel.UNVERIFIABLE -> "Could not verify hardware reaction"
                else -> "Verification failed"
            }
        } else null

        Log.d(TAG, "Verify [${command.id}]: level=$level, read='$verifiedRead', success=$isSuccess")

        CommandExecutionResult(
            commandId = command.id,
            isSupported = true,
            isSuccess = isSuccess,
            verificationLevel = level,
            originalValue = snapshotOriginalValue,
            appliedValue = targetVal,
            verifiedValue = verifiedRead,
            errorMessage = verifyError
        )
    }

    private suspend fun readMemAvailableKb(): Long? {
        val out = AdbCommandRunner.run("cat /proc/meminfo", source = CommandSource.REPORT) ?: return null
        val line = out.lines().firstOrNull { it.startsWith("MemAvailable:") } ?: return null
        val parts = line.split("\\s+".toRegex())
        return parts.getOrNull(1)?.toLongOrNull()
    }

    private suspend fun parseActiveDisplayRefreshRate(): Float? {
        val out = AdbCommandRunner.run("dumpsys display", source = CommandSource.REPORT) ?: return null
        // Look for mActiveModeId or refreshRate line
        val line = out.lines().firstOrNull {
            it.contains("fps", ignoreCase = true) || it.contains("refreshRate", ignoreCase = true) || it.contains("mRefreshRate", ignoreCase = true)
        } ?: return null

        val match = Regex("""(\d+\.?\d*)\s*fps""").find(line) ?: Regex("""refreshRate=(\d+\.?\d*)""").find(line)
        return match?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }

    /**
     * Rolls back a single command to its original baseline state.
     */
    suspend fun rollbackCommand(command: SystemTuningCommand, gamePkg: String = "com.tencent.ig"): Boolean = withContext(Dispatchers.IO) {
        val snapshot = boosterDao.getSnapshot(command.id) ?: return@withContext true
        val rollbackCmd = command.rollbackCommand(gamePkg, snapshot.originalValue, snapshot.isAbsentOriginally)
        val res = AdbCommandRunner.runDetailed(rollbackCmd, source = CommandSource.USER_ACTION)
        if (res.success) {
            boosterDao.deleteSnapshot(command.id)
            true
        } else {
            Log.e(TAG, "Failed to rollback [${command.id}]: ${res.stderr}")
            false
        }
    }

    /**
     * Rolls back aggressive commands only (for Safe Mode thermal protection) (Fix A7).
     */
    suspend fun rollbackAggressiveCommands(gamePkg: String = "com.tencent.ig"): RollbackReport = withContext(Dispatchers.IO) {
        val snapshots = boosterDao.getAllSnapshots()
        var restoredCount = 0
        val failed = mutableListOf<String>()

        for (snapshot in snapshots) {
            val cmd = CommandRegistry.findById(snapshot.commandId)
            if (cmd != null && cmd.isAggressive) {
                val ok = rollbackCommand(cmd, gamePkg)
                if (ok) restoredCount++ else failed.add(cmd.id)
            }
        }

        RollbackReport(
            totalSnapshots = snapshots.count { CommandRegistry.findById(it.commandId)?.isAggressive == true },
            restoredCount = restoredCount,
            failedCommands = failed,
            isFullyRestored = failed.isEmpty()
        )
    }

    /**
     * Rolls back all stored snapshots to their verified original baseline.
     */
    suspend fun rollbackAll(gamePkg: String = "com.tencent.ig"): RollbackReport = withContext(Dispatchers.IO) {
        val snapshots = boosterDao.getAllSnapshots()
        var restoredCount = 0
        val failed = mutableListOf<String>()

        for (snapshot in snapshots) {
            val cmd = CommandRegistry.findById(snapshot.commandId)
            if (cmd != null) {
                val ok = rollbackCommand(cmd, gamePkg)
                if (ok) restoredCount++ else failed.add(cmd.id)
            } else {
                // Fallback direct delete for unregistered keys
                val delCmd = if (snapshot.isAbsentOriginally) {
                    "settings delete ${snapshot.settingNamespace} ${snapshot.settingKey}"
                } else {
                    "settings put ${snapshot.settingNamespace} ${snapshot.settingKey} ${snapshot.originalValue}"
                }
                val res = AdbCommandRunner.runDetailed(delCmd, source = CommandSource.USER_ACTION)
                if (res.success) {
                    boosterDao.deleteSnapshot(snapshot.commandId)
                    restoredCount++
                } else {
                    failed.add(snapshot.commandId)
                }
            }
        }

        RollbackReport(
            totalSnapshots = snapshots.size,
            restoredCount = restoredCount,
            failedCommands = failed,
            isFullyRestored = failed.isEmpty()
        )
    }

    fun clearCache() {
        cachedSettingsKeys.clear()
    }
}
