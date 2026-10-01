package com.example.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central recorder for commands executed in the app (Fix A1).
 * Keeps an in-memory ring buffer of the latest 100 executions.
 * Only persists USER_ACTION, SESSION, and REPORT commands to Room.
 * Telemetry commands stay strictly in-memory.
 * Truncates stored messages to 500 chars and enforces 500-row table retention.
 */
object ExecutionRecorder {
    private const val TAG = "ExecutionRecorder"
    private const val MAX_IN_MEMORY_LOGS = 100
    private const val MAX_FIELD_LENGTH = 500

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var boosterDao: BoosterDao? = null

    private val _recentExecutions = MutableStateFlow<List<ExecResult>>(emptyList())
    val recentExecutions: StateFlow<List<ExecResult>> = _recentExecutions.asStateFlow()

    fun init(dao: BoosterDao) {
        boosterDao = dao
        scope.launch {
            try {
                dao.trimOldLogs()
            } catch (e: Throwable) {
                Log.w(TAG, "Initial log trim notice: ${e.message}")
            }
        }
    }

    fun record(result: ExecResult, persist: Boolean = (result.source != CommandSource.TELEMETRY)) {
        val current = _recentExecutions.value.toMutableList()
        current.add(0, result)
        if (current.size > MAX_IN_MEMORY_LOGS) {
            _recentExecutions.value = current.take(MAX_IN_MEMORY_LOGS)
        } else {
            _recentExecutions.value = current
        }

        // Fix A1: Only persist to Room when persist == true (never telemetry)
        if (!persist) return

        boosterDao?.let { dao ->
            scope.launch {
                try {
                    val status = if (result.success) "SUCCESS" else "FAILED"
                    val rawMsg = if (result.success) {
                        result.stdout.ifBlank { "Exit code 0" }
                    } else {
                        val reason = result.failureReason?.name ?: "ERROR"
                        "$reason: ${result.stderr.ifBlank { "Exit code ${result.exitCode}" }}"
                    }

                    val truncatedCmd = result.command.take(MAX_FIELD_LENGTH)
                    val truncatedMsg = rawMsg.take(MAX_FIELD_LENGTH)

                    dao.insertLog(
                        OptimizationLog(
                            commandName = result.command.take(80),
                            commandText = truncatedCmd,
                            status = status,
                            responseMsg = truncatedMsg,
                            executionTime = System.currentTimeMillis()
                        )
                    )
                    dao.trimOldLogs()
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to persist execution log: ${e.message}")
                }
            }
        }
    }

    fun clear() {
        _recentExecutions.value = emptyList()
    }
}
