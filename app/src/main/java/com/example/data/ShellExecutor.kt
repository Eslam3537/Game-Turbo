package com.example.data

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Method

enum class FailureReason {
    SHIZUKU_NOT_RUNNING,
    SHIZUKU_PERMISSION_DENIED,
    TIMEOUT,
    NON_ZERO_EXIT,
    EXCEPTION,
    EMPTY_COMMAND
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
    val exceptionInfo: String? = null
)

interface ShellExecutor {
    suspend fun runDetailed(command: String, timeoutMs: Long = 8000L): ExecResult
    fun isAvailable(): Boolean
    fun getMechanismName(): String
}

/**
 * Privileged Shell Executor using Shizuku Binder API.
 * Uses getDeclaredMethod("newProcess", ...) with isAccessible = true.
 * Concurrently drains stdout & stderr via coroutines to prevent buffer deadlocks.
 * Enforces strict 8000ms timeout and records execution time and full stack traces on exceptions.
 */
class ShizukuShellExecutor : ShellExecutor {
    private val TAG = "ShizukuShellExecutor"
    private var cachedNewProcessMethod: Method? = null

    override fun getMechanismName(): String = "Shizuku_Reflection_v13"

    override fun isAvailable(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                Log.w(TAG, "Shizuku binder is not alive")
                return false
            }
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!granted) Log.w(TAG, "Shizuku permission not granted")
            granted
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

    override suspend fun runDetailed(command: String, timeoutMs: Long): ExecResult = withContext(Dispatchers.IO) {
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
                failureReason = FailureReason.EMPTY_COMMAND
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
                failureReason = FailureReason.SHIZUKU_NOT_RUNNING
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
                failureReason = FailureReason.SHIZUKU_PERMISSION_DENIED
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
                } catch (e: Throwable) {
                    ""
                }
            }

            val stderrDeferred = async(Dispatchers.IO) {
                try {
                    BufferedReader(InputStreamReader(proc.errorStream)).use { it.readText().trim() }
                } catch (e: Throwable) {
                    ""
                }
            }

            val exitCode = withTimeoutOrNull(timeoutMs) {
                proc.waitFor()
            }

            val durationMs = System.currentTimeMillis() - startTime

            if (exitCode == null) {
                Log.w(TAG, "Command execution timed out after ${timeoutMs}ms: $command")
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        proc.destroyForcibly()
                    } else {
                        proc.destroy()
                    }
                } catch (_: Throwable) {}
                stdoutDeferred.cancel()
                stderrDeferred.cancel()

                return@withContext ExecResult(
                    command = command,
                    success = false,
                    exitCode = -1,
                    stdout = "",
                    stderr = "Command execution timed out after ${timeoutMs}ms",
                    durationMs = durationMs,
                    mechanism = getMechanismName(),
                    failureReason = FailureReason.TIMEOUT
                )
            }

            val stdout = stdoutDeferred.await()
            val stderr = stderrDeferred.await()
            val isSuccess = (exitCode == 0)

            ExecResult(
                command = command,
                success = isSuccess,
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                durationMs = durationMs,
                mechanism = getMechanismName(),
                failureReason = if (!isSuccess) FailureReason.NON_ZERO_EXIT else null
            )
        } catch (e: Throwable) {
            val durationMs = System.currentTimeMillis() - startTime
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val fullStackTrace = sw.toString()
            val errorSummary = "${e.javaClass.name}: ${e.message ?: "Unknown error"}"

            Log.e(TAG, "Exception running command [$command]: $errorSummary", e)
            try {
                process?.destroy()
            } catch (_: Throwable) {}

            ExecResult(
                command = command,
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = errorSummary,
                durationMs = durationMs,
                mechanism = getMechanismName(),
                failureReason = FailureReason.EXCEPTION,
                exceptionInfo = fullStackTrace
            )
        }
    }
}
