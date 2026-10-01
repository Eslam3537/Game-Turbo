package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Privileged Shell Command Runner utilizing Shizuku binder via ShellExecutor.
 * - Records all commands into ExecutionRecorder with source tagging and retention rules (Fix A1)
 * - Distinguishes between isShizukuInstalled and isShizukuRunning (Fix A13)
 * - Typed ExecResult with duration, mechanism, failure reasons, and exception info
 */
object AdbCommandRunner {
    private const val TAG = "AdbCommandRunner"
    private const val DEFAULT_TIMEOUT_MS = 8000L

    var executor: ShellExecutor = ShizukuShellExecutor()

    fun isAvailable(): Boolean = executor.isAvailable()

    /**
     * Checks if the Shizuku Manager APK is installed on the device (Fix A13).
     */
    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo("moe.shizuku.privileged.api", 0) != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if the privileged Shizuku binder service is currently active and alive (Fix A13).
     */
    fun isShizukuRunning(): Boolean {
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

    suspend fun runDetailed(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        source: CommandSource = CommandSource.USER_ACTION,
        persist: Boolean = (source != CommandSource.TELEMETRY)
    ): ExecResult {
        val result = executor.runDetailed(command, timeoutMs, source)
        ExecutionRecorder.record(result, persist)
        return result
    }

    suspend fun run(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        source: CommandSource = CommandSource.USER_ACTION,
        persist: Boolean = (source != CommandSource.TELEMETRY)
    ): String? {
        val result = runDetailed(command, timeoutMs, source, persist)
        return if (result.success) result.stdout else null
    }

    suspend fun readSetting(namespace: String, key: String): SettingValue {
        val result = runDetailed("settings get $namespace $key", source = CommandSource.USER_ACTION)
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

    suspend fun runAllDetailed(
        commands: List<String>,
        source: CommandSource = CommandSource.USER_ACTION
    ): List<ExecResult> {
        val results = mutableListOf<ExecResult>()
        for (cmd in commands) {
            if (cmd.isNotBlank()) {
                results.add(runDetailed(cmd, source = source))
            }
        }
        return results
    }
}
