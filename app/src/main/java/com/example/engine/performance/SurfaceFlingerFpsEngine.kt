package com.example.engine.performance

import android.content.Context
import android.util.Log
import com.example.data.AdbCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * SurfaceFlinger Real-Time Game FPS Measurement Engine.
 *
 * Implements:
 * 1. SurfaceFlinger TimeStats (Primary): Measures actual frames presented to display.
 * 2. SurfaceFlinger Latency Timestamps (Fallback): Measures vsync frame intervals.
 * 3. Foreground App Detection: Verifies PUBG or user's selected game is active.
 *
 * Strict Rule: NEVER fabricates or returns display refresh rate as game FPS.
 * Returns null ("غير متاح") when game is not rendered or data is unavailable.
 */
object SurfaceFlingerFpsEngine {
    private const val TAG = "SurfaceFlingerFpsEngine"

    // Supported PUBG package IDs across global and regional editions
    val KNOWN_PUBG_PACKAGES = setOf(
        "com.tencent.ig",           // PUBG Mobile Global
        "com.pubg.imobile",         // BGMI (Battlegrounds Mobile India)
        "com.pubg.krmobile",        // PUBG Mobile Korea/Japan
        "com.pubg.newstate",        // NEW STATE Mobile
        "com.vng.pubgmobile",       // PUBG Mobile Vietnam
        "com.tencent.tmgp.pubgmhd"  // Game for Peace (China)
    )

    private var isTimeStatsEnabled = false
    private var lastTotalFrames = -1L
    private var lastSampleTimeMs = 0L

    /**
     * Initializes SurfaceFlinger TimeStats measurement session.
     */
    suspend fun enableTimeStats(): Boolean = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) return@withContext false
        try {
            val res = AdbCommandRunner.runDetailed("dumpsys SurfaceFlinger --timestats -clear -enable")
            isTimeStatsEnabled = res.success
            lastTotalFrames = -1L
            lastSampleTimeMs = System.currentTimeMillis()
            isTimeStatsEnabled
        } catch (e: Throwable) {
            Log.e(TAG, "enableTimeStats failed: ${e.message}")
            false
        }
    }

    /**
     * Disables SurfaceFlinger TimeStats upon monitor termination to save CPU cycles.
     */
    suspend fun disableTimeStats() = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) return@withContext
        try {
            AdbCommandRunner.runDetailed("dumpsys SurfaceFlinger --timestats -disable")
            isTimeStatsEnabled = false
        } catch (e: Throwable) {
            Log.e(TAG, "disableTimeStats failed: ${e.message}")
        }
    }

    /**
     * Detects the package name currently in the foreground via Shizuku shell.
     */
    suspend fun detectForegroundPackage(): String? = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) return@withContext null
        try {
            // Fast query: window focus
            val windowDump = AdbCommandRunner.run("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
            if (!windowDump.isNullOrBlank()) {
                val pkgMatcher = Pattern.compile("([a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+)").matcher(windowDump)
                while (pkgMatcher.find()) {
                    val pkg = pkgMatcher.group(1)
                    if (pkg != null && !pkg.startsWith("android") && !pkg.contains("systemui") && !pkg.contains("launcher")) {
                        return@withContext pkg
                    }
                }
            }

            // Fallback query: top resumed activity
            val actDump = AdbCommandRunner.run("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'")
            if (!actDump.isNullOrBlank()) {
                val actMatcher = Pattern.compile("([a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+)").matcher(actDump)
                while (actMatcher.find()) {
                    val pkg = actMatcher.group(1)
                    if (pkg != null && !pkg.startsWith("android") && !pkg.contains("systemui") && !pkg.contains("launcher")) {
                        return@withContext pkg
                    }
                }
            }
            null
        } catch (e: Throwable) {
            Log.d(TAG, "detectForegroundPackage error: ${e.message}")
            null
        }
    }

    /**
     * Resolves the target game package (PUBG or user-configured game).
     */
    fun resolveTargetGame(configuredGamePkg: String, foregroundPkg: String?): String {
        // If foreground package is an active PUBG edition, prioritize it
        if (foregroundPkg != null && KNOWN_PUBG_PACKAGES.contains(foregroundPkg)) {
            return foregroundPkg
        }
        // If configured game is active in foreground, use it
        if (configuredGamePkg.isNotBlank() && foregroundPkg == configuredGamePkg) {
            return configuredGamePkg
        }
        // If configured game is set, use it as fallback target
        if (configuredGamePkg.isNotBlank()) {
            return configuredGamePkg
        }
        // Default target to PUBG Mobile Global
        return "com.tencent.ig"
    }

    /**
     * Samples real FPS for the target game.
     * Tries TimeStats first; falls back to SurfaceFlinger latency if needed.
     */
    suspend fun sampleFps(targetPackage: String): Int? = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) return@withContext null

        // Strategy 1: TimeStats
        val timeStatsFps = sampleViaTimeStats(targetPackage)
        if (timeStatsFps != null && timeStatsFps in 10..144) {
            return@withContext timeStatsFps
        }

        // Strategy 2: SurfaceFlinger Frame Latency Fallback
        val latencyFps = sampleViaFrameLatency(targetPackage)
        if (latencyFps != null && latencyFps in 10..144) {
            return@withContext latencyFps
        }

        // Return null if data is unconfirmed — NEVER return screen refresh rate
        null
    }

    /**
     * Measures FPS using SurfaceFlinger --timestats
     */
    private suspend fun sampleViaTimeStats(targetPackage: String): Int? {
        try {
            if (!isTimeStatsEnabled) {
                enableTimeStats()
            }

            val dumpOutput = AdbCommandRunner.run("dumpsys SurfaceFlinger --timestats -dump")
                ?: return null

            val lines = dumpOutput.lines()
            var inTargetLayer = false
            var totalFrames: Long? = null
            var averageFps: Double? = null

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.startsWith("Layer name:") || line.startsWith("layerName =") || line.contains("Layer:")) {
                    inTargetLayer = line.contains(targetPackage, ignoreCase = true)
                }

                if (inTargetLayer) {
                    // Format 1: totalFrames = 120 or totalFrames: 120
                    if (line.contains("totalFrames", ignoreCase = true) || line.contains("totalPresentFrames", ignoreCase = true)) {
                        val num = line.filter { it.isDigit() }.toLongOrNull()
                        if (num != null) totalFrames = num
                    }
                    // Format 2: averageFPS = 59.8 or averageFPS: 60
                    if (line.contains("averageFPS", ignoreCase = true) || line.contains("frameRate", ignoreCase = true)) {
                        val parts = line.split("=", ":")
                        if (parts.size >= 2) {
                            val parsed = parts[1].trim().toDoubleOrNull()
                            if (parsed != null && parsed > 5.0) averageFps = parsed
                        }
                    }
                }
            }

            val now = System.currentTimeMillis()
            val elapsedSec = (now - lastSampleTimeMs) / 1000.0

            // If averageFps was directly reported for this layer
            if (averageFps != null && averageFps > 5.0) {
                AdbCommandRunner.run("dumpsys SurfaceFlinger --timestats -clear")
                lastSampleTimeMs = now
                return averageFps.toInt()
            }

            // Calculate delta frames over the elapsed window
            if (totalFrames != null && lastTotalFrames >= 0 && totalFrames >= lastTotalFrames && elapsedSec >= 0.7) {
                val deltaFrames = totalFrames - lastTotalFrames
                val calculatedFps = (deltaFrames / elapsedSec).toInt()
                lastTotalFrames = totalFrames
                lastSampleTimeMs = now

                // Clear sample after successful read
                AdbCommandRunner.run("dumpsys SurfaceFlinger --timestats -clear")
                lastTotalFrames = 0L

                if (calculatedFps in 10..144) {
                    return calculatedFps
                }
            } else if (totalFrames != null) {
                lastTotalFrames = totalFrames
                lastSampleTimeMs = now
            }
        } catch (e: Throwable) {
            Log.d(TAG, "sampleViaTimeStats error: ${e.message}")
        }
        return null
    }

    /**
     * Fallback: Measures FPS using SurfaceFlinger --latency <layer>
     */
    private suspend fun sampleViaFrameLatency(targetPackage: String): Int? {
        try {
            // Find game surface layer
            val listOutput = AdbCommandRunner.run("dumpsys SurfaceFlinger --list") ?: return null
            val layers = listOutput.lines().map { it.trim() }

            val gameLayer = layers.firstOrNull { it.contains(targetPackage, ignoreCase = true) && it.contains("SurfaceView", ignoreCase = true) }
                ?: layers.firstOrNull { it.contains(targetPackage, ignoreCase = true) }
                ?: return null

            val latencyOutput = AdbCommandRunner.run("dumpsys SurfaceFlinger --latency \"$gameLayer\"")
                ?: return null

            val latencyLines = latencyOutput.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (latencyLines.size < 6) return null

            // Extract presentation/vsync timestamps (column 1 or 2)
            val timestamps = mutableListOf<Long>()
            for (i in 1 until latencyLines.size) {
                val parts = latencyLines[i].split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val ts = parts[1].toLongOrNull() ?: parts[2].toLongOrNull()
                    if (ts != null && ts > 0L && ts < 9223372036854775800L) {
                        timestamps.add(ts)
                    }
                }
            }

            if (timestamps.size < 6) return null

            // Window of the last 30 frames
            val recent = timestamps.takeLast(30)
            val deltaNanos = recent.last() - recent.first()
            if (deltaNanos <= 0L) return null

            val fps = ((recent.size - 1).toDouble() * 1_000_000_000.0 / deltaNanos.toDouble()).toInt()
            if (fps in 10..144) {
                return fps
            }
        } catch (e: Throwable) {
            Log.d(TAG, "sampleViaFrameLatency error: ${e.message}")
        }
        return null
    }
}
