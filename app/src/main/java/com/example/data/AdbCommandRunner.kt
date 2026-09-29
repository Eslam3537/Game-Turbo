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
import java.lang.reflect.Method

/**
 * Privileged Shell Command Runner utilizing Shizuku binder.
 * Features:
 * - Direct reflective invocation supporting Shizuku API 13+
 * - Concurrent stdout and stderr consumption to prevent stream deadlock
 * - 8-second execution timeout with process destroy
 * - Honest typed ExecResult and nullable string return (no fake error strings)
 */
object AdbCommandRunner {
    private const val TAG = "AdbCommandRunner"
    private const val DEFAULT_TIMEOUT_MS = 8000L

    data class ExecResult(
        val command: String,
        val success: Boolean,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean = false
    )

    private var cachedNewProcessMethod: Method? = null

    fun isAvailable(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                Log.w(TAG, "Shizuku binder not alive - service not running")
                return false
            }
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!granted) Log.w(TAG, "Shizuku permission not granted yet")
            granted
        } catch (e: Throwable) {
            Log.e(TAG, "Shizuku availability check failed: ${e.javaClass.simpleName} - ${e.message}")
            false
        }
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            if (!Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "requestPermission failed: ${e.javaClass.simpleName} - ${e.message}")
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
            Log.w(TAG, "getDeclaredMethod newProcess failed (${e.javaClass.name}: ${e.message}), trying getMethod")
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

    /**
     * Executes shell command through privileged Shizuku process.
     * Concurrently reads stdout and stderr to prevent deadlocks and enforces timeout.
     */
    suspend fun runDetailed(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ExecResult = withContext(Dispatchers.IO) {
        if (command.isBlank()) {
            return@withContext ExecResult(command, false, -1, "", "Empty command")
        }
        if (!isAvailable()) {
            return@withContext ExecResult(command, false, -1, "", "Shizuku service is unavailable or unauthorized")
        }

        var process: java.lang.Process? = null
        try {
            val method = getNewProcessMethod()
            val proc = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as? java.lang.Process ?: return@withContext ExecResult(command, false, -1, "", "Failed to spawn Shizuku process")
            process = proc

            // Read stdout and stderr concurrently
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

            if (exitCode == null) {
                Log.w(TAG, "Command timed out after ${timeoutMs}ms: $command")
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
                    timedOut = true
                )
            }

            val stdout = stdoutDeferred.await()
            val stderr = stderrDeferred.await()
            val isSuccess = exitCode == 0

            Log.d(TAG, "EXEC: [$command] -> exit=$exitCode out=[$stdout] err=[$stderr]")
            ExecResult(
                command = command,
                success = isSuccess,
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr
            )
        } catch (e: Throwable) {
            val errMessage = "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
            Log.e(TAG, "Execution failed for [$command]: $errMessage", e)
            try {
                process?.destroy()
            } catch (_: Throwable) {}
            ExecResult(
                command = command,
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = errMessage
            )
        }
    }

    /**
     * Executes command and returns trimmed stdout on success, or null on failure.
     * Does NOT return fake "ERROR: ..." strings as if they were valid values.
     */
    suspend fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        val result = runDetailed(command, timeoutMs)
        return if (result.success) result.stdout else null
    }

    suspend fun runAllDetailed(commands: List<String>): List<ExecResult> {
        val results = mutableListOf<ExecResult>()
        for (cmd in commands) {
            if (cmd.isNotBlank()) {
                results.add(runDetailed(cmd))
            }
        }
        return results
    }
}
