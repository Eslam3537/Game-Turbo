package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

enum class FailureReason {
    SHIZUKU_NOT_RUNNING,
    SHIZUKU_PERMISSION_DENIED,
    TIMEOUT,
    NON_ZERO_EXIT,
    EXCEPTION,
    EMPTY_COMMAND
}

enum class CommandSource {
    USER_ACTION,
    SESSION,
    REPORT,
    TELEMETRY
}

sealed class SettingValue {
    data class Present(val value: String) : SettingValue()
    object Absent : SettingValue()
    data class Error(val reason: String) : SettingValue()
}

data class ExecResult(
    val command: String,
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val mechanism: String,
    val failureReason: FailureReason? = null,
    val exceptionInfo: String? = null,
    val source: CommandSource = CommandSource.USER_ACTION
)

interface ShellExecutor {
    suspend fun runDetailed(
        command: String,
        timeoutMs: Long = 8000L,
        source: CommandSource = CommandSource.USER_ACTION
    ): ExecResult

    fun isAvailable(): Boolean
    fun getMechanismName(): String
}

/**
 * Privileged Shell Executor using Shizuku Binder API.
 * Uses getDeclaredMethod("newProcess", ...) with isAccessible = true.
 * Concurrently drains stdout & stderr via coroutines to prevent buffer deadlocks.
 * Enforces strict timeout with proc.destroyForcibly() to terminate hung processes (Fix A13).
 */
class ShizukuShellExecutor : ShellExecutor {
    private val TAG = "ShizukuShellExecutor"
    private var cachedNewProcessMethod: Method? = null

    override fun getMechanismName(): String = "Shizuku_Reflection_v13"

    override fun isAvailable(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                return false
            }
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            Log.e(TAG, "Shizuku availability check failed: ${e.javaClass.simpleName} - ${e.message}")
            false
        }
    }

    private fun getNewProcessMethod(): Method {
        cachedNewProcessMethod?.let { return it }
        val clazz = Class.forName("rikka.shizuku.Shizuku")
        val method = try {
            clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "getDeclaredMethod newProcess failed, attempting fallback getMethod", e)
            clazz.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }
        cachedNewProcessMethod = method
        return method
    }

    override suspend fun runDetailed(
        command: String,
        timeoutMs: Long,
        source: CommandSource
    ): ExecResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val trimmed = command.trim()

        if (trimmed.isEmpty()) {
            return@withContext ExecResult(
                command = command,
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "Empty command supplied",
                durationMs = 0L,
                mechanism = getMechanismName(),
                failureReason = FailureReason.EMPTY_COMMAND,
                source = source
            )
        }

        if (!Shizuku.pingBinder()) {
            return@withContext ExecResult(
                command = command,
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "Shizuku service is not running. Please start Shizuku first.",
                durationMs = System.currentTimeMillis() - startTime,
                mechanism = getMechanismName(),
                failureReason = FailureReason.SHIZUKU_NOT_RUNNING,
                source = source
            )
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return@withContext ExecResult(
                command = command,
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "Shizuku permission denied. Please grant permission in Shizuku app.",
                durationMs = System.currentTimeMillis() - startTime,
                mechanism = getMechanismName(),
                failureReason = FailureReason.SHIZUKU_PERMISSION_DENIED,
                source = source
            )
        }

        var process: java.lang.Process? = null
        try {
            val method = getNewProcessMethod()
            val proc = method.invoke(
                null,
                arrayOf("sh", "-c", trimmed),
                null,
                null
            ) as? java.lang.Process ?: throw IllegalStateException("Shizuku newProcess returned null process")
            process = proc

            // Concurrently drain stdout and stderr to prevent deadlocks
            val stdoutDeferred = async(Dispatchers.IO) {
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText().trim() }
                } catch (_: Throwable) {
                    ""
                }
            }

            val stderrDeferred = async(Dispatchers.IO) {
                try {
                    BufferedReader(InputStreamReader(proc.errorStream)).use { it.readText().trim() }
                } catch (_: Throwable) {
                    ""
                }
            }

            // Fix A13: Use process.waitFor with timeout and destroyForcibly() on timeout
            val finishedInTime = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val durationMs = System.currentTimeMillis() - startTime

            if (!finishedInTime) {
                proc.destroyForcibly()
                stdoutDeferred.cancel()
                stderrDeferred.cancel()
                return@withContext ExecResult(
                    command = command,
                    success = false,
                    exitCode = -1,
                    stdout = "",
                    stderr = "Command execution timed out after ${timeoutMs}ms and process was forcibly terminated",
                    durationMs = durationMs,
                    mechanism = getMechanismName(),
                    failureReason = FailureReason.TIMEOUT,
                    source = source
                )
            }

            val exitCode = proc.exitValue()
            val stdoutText = stdoutDeferred.await()
            val stderrText = stderrDeferred.await()

            val success = exitCode == 0
            val failureReason = if (success) null else FailureReason.NON_ZERO_EXIT

            ExecResult(
                command = command,
                success = success,
                exitCode = exitCode,
                stdout = stdoutText,
                stderr = stderrText,
                durationMs = durationMs,
                mechanism = getMechanismName(),
                failureReason = failureReason,
                source = source
            )
        } catch (e: Throwable) {
            val durationMs = System.currentTimeMillis() - startTime
            try {
                process?.destroyForcibly()
            } catch (_: Throwable) {}

            ExecResult(
                command = command,
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "Exception executing command: ${e.message}",
                durationMs = durationMs,
                mechanism = getMechanismName(),
                failureReason = FailureReason.EXCEPTION,
                exceptionInfo = e.stackTraceToString(),
                source = source
            )
        }
    }
}
