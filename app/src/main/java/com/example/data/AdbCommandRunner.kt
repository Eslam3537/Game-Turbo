package com.example.data

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Privileged Shell Command Runner utilizing Shizuku binder via ShellExecutor.
 * - Records all commands into ExecutionRecorder
 * - Typed ExecResult with duration, mechanism, failure reasons, and exception info
 * - readSetting(namespace, key): SettingValue helper
 * - run(command): String? returns null on failure (no fake error strings)
 */
object AdbCommandRunner {
    private const val TAG = "AdbCommandRunner"
    private const val DEFAULT_TIMEOUT_MS = 8000L

    var executor: ShellExecutor = ShizukuShellExecutor()

    fun isAvailable(): Boolean = executor.isAvailable()

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

    suspend fun runDetailed(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ExecResult {
        val result = executor.runDetailed(command, timeoutMs)
        ExecutionRecorder.record(result)
        return result
    }

    suspend fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        val result = runDetailed(command, timeoutMs)
        return if (result.success) result.stdout else null
    }

    suspend fun readSetting(namespace: String, key: String): SettingValue {
        val result = runDetailed("settings get $namespace $key")
        if (!result.success) {
            return SettingValue.Error(result.stderr.ifBlank { "Exit code ${result.exitCode}" })
        }
        val out = result.stdout.trim()
        return if (out.isEmpty() || out == "null") {
            SettingValue.Absent
        } else {
            SettingValue.Present(out)
        }
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
