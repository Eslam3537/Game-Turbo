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
 * Central recorder for every command executed in the app.
 * Keeps an in-memory ring buffer of the latest 100 executions and optionally persists to Room.
 */
object ExecutionRecorder {
    private const val TAG = "ExecutionRecorder"
    private const val MAX_IN_MEMORY_LOGS = 100

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var boosterDao: BoosterDao? = null

    private val _recentExecutions = MutableStateFlow<List<ExecResult>>(emptyList())
    val recentExecutions: StateFlow<List<ExecResult>> = _recentExecutions.asStateFlow()

    fun init(dao: BoosterDao) {
        boosterDao = dao
    }

    fun record(result: ExecResult) {
        val current = _recentExecutions.value.toMutableList()
        current.add(0, result)
        if (current.size > MAX_IN_MEMORY_LOGS) {
            _recentExecutions.value = current.take(MAX_IN_MEMORY_LOGS)
        } else {
            _recentExecutions.value = current
        }

        // Persist to Room
        boosterDao?.let { dao ->
            scope.launch {
                try {
                    val status = if (result.success) "SUCCESS" else "FAILED"
                    val responseMsg = if (result.success) {
                        result.stdout.ifBlank { "Exit code 0" }
                    } else {
                        val reason = result.failureReason?.name ?: "ERROR"
                        "${reason}: ${result.stderr.ifBlank { "Exit code ${result.exitCode}" }}"
                    }

                    dao.insertLog(
                        OptimizationLog(
                            commandName = result.command.take(80),
                            commandText = result.command,
                            status = status,
                            responseMsg = responseMsg,
                            executionTime = System.currentTimeMillis()
                        )
                    )
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
