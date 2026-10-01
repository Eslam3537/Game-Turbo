package com.example.engine.performance

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.example.data.AdbCommandRunner
import com.example.data.CommandSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified SurfaceFlinger Real-Time Game FPS Engine (Fix A2, A14).
 * Single source of truth for Home, Overlay HUD, and Sessions.
 * Only runs SurfaceFlinger queries when target game is confirmed in the foreground.
 * Implements 3-second staleness timeout (returns null/N/A after 3 seconds of missing frames).
 */
object SurfaceFlingerFpsEngine {
    private const val TAG = "SurfaceFlingerFpsEngine"
    private const val FOREGROUND_CHECK_TTL_MS = 5000L
    private const val STALENESS_LIMIT_MS = 3000L

    val KNOWN_PUBG_PACKAGES = setOf(
        "com.tencent.ig",           // PUBG Mobile Global
        "com.pubg.imobile",         // BGMI (Battlegrounds Mobile India)
        "com.pubg.krmobile",        // PUBG Mobile Korea/Japan
        "com.pubg.newstate",        // NEW STATE Mobile
        "com.vng.pubgmobile",       // PUBG Mobile Vietnam
        "com.tencent.tmgp.pubgmhd"  // Game for Peace (China)
    )

    private var lastForegroundCheckTime = 0L
    private var lastDetectedForegroundPackage: String? = null

    private var lastValidFps: Int? = null
    private var lastValidFpsTime = 0L

    /**
     * Cheap check for active foreground package (cached for 5 seconds).
     */
    suspend fun getForegroundPackage(context: Context): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (lastDetectedForegroundPackage != null && (now - lastForegroundCheckTime) < FOREGROUND_CHECK_TTL_MS) {
            return@withContext lastDetectedForegroundPackage
        }

        var detectedPkg: String? = null

        // 1. Try UsageStatsManager
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 10000,
                now
            )
            val mostRecent = stats?.maxByOrNull { it.lastTimeUsed }
            if (mostRecent != null && (now - mostRecent.lastTimeUsed) < 15000) {
                detectedPkg = mostRecent.packageName
            }
        } catch (_: Throwable) {}

        // 2. Fallback to single dumpsys activity top-resumed if available
        if (detectedPkg == null && AdbCommandRunner.isAvailable()) {
            try {
                val out = AdbCommandRunner.run("dumpsys activity top-resumed", source = CommandSource.TELEMETRY)
                val line = out?.lines()?.firstOrNull { it.contains("top-resumed", ignoreCase = true) || it.contains("mResumedActivity", ignoreCase = true) }
                if (line != null) {
                    val match = Regex("""([a-zA-Z0-9_.]+/([a-zA-Z0-9_.]+))""").find(line)
                    detectedPkg = match?.groupValues?.getOrNull(1)?.substringBefore('/')
                }
            } catch (_: Throwable) {}
        }

        lastDetectedForegroundPackage = detectedPkg
        lastForegroundCheckTime = now
        detectedPkg
    }

    /**
     * Measures game FPS via SurfaceFlinger latency timestamps.
     * Returns null if game is not active in the foreground, without running expensive shell queries (Fix A2).
     */
    suspend fun sampleFps(context: Context, targetGamePackage: String): Int? = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) {
            return@withContext null
        }

        // Fix A2: Confirm target game is in foreground before spawning FPS shell commands
        val fg = getForegroundPackage(context)
        val isTargetActive = fg == targetGamePackage || (fg != null && KNOWN_PUBG_PACKAGES.contains(fg))

        if (!isTargetActive) {
            // Check staleness window
            val now = System.currentTimeMillis()
            return@withContext if (lastValidFps != null && (now - lastValidFpsTime) < STALENESS_LIMIT_MS) {
                lastValidFps
            } else {
                lastValidFps = null
                null
            }
        }

        val effectivePkg = if (KNOWN_PUBG_PACKAGES.contains(fg)) fg!! else targetGamePackage

        try {
            // 1. Locate SurfaceFlinger active layer for this package
            val listOutput = AdbCommandRunner.run("dumpsys SurfaceFlinger --list", source = CommandSource.TELEMETRY)
                ?: return@withContext checkStaleFps()
            val lines = listOutput.lines().map { it.trim() }

            val gameLayer = lines.firstOrNull { it.contains(effectivePkg) && it.contains("SurfaceView") }
                ?: lines.firstOrNull { it.contains(effectivePkg) }

            if (gameLayer.isNullOrBlank()) {
                return@withContext checkStaleFps()
            }

            // 2. Fetch SurfaceFlinger frame timestamps for this layer
            val latencyOutput = AdbCommandRunner.run("dumpsys SurfaceFlinger --latency \"$gameLayer\"", source = CommandSource.TELEMETRY)
                ?: return@withContext checkStaleFps()

            val latencyLines = latencyOutput.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (latencyLines.size < 5) return@withContext checkStaleFps()

            val timestamps = mutableListOf<Long>()
            for (i in 1 until latencyLines.size) {
                val parts = latencyLines[i].split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val readyTime = parts[1].toLongOrNull() ?: parts[2].toLongOrNull()
                    if (readyTime != null && readyTime > 0L && readyTime < 9223372036854775800L) {
                        timestamps.add(readyTime)
                    }
                }
            }

            if (timestamps.size < 6) return@withContext checkStaleFps()

            val recent = timestamps.takeLast(30)
            val deltaNanos = recent.last() - recent.first()
            if (deltaNanos <= 0L) return@withContext checkStaleFps()

            val measuredFps = ((recent.size - 1).toDouble() * 1_000_000_000.0 / deltaNanos.toDouble()).toInt()
            if (measuredFps in 10..144) {
                lastValidFps = measuredFps
                lastValidFpsTime = System.currentTimeMillis()
                return@withContext measuredFps
            }

            checkStaleFps()
        } catch (e: Throwable) {
            Log.w(TAG, "sampleFps notice: ${e.message}")
            checkStaleFps()
        }
    }

    private fun checkStaleFps(): Int? {
        val now = System.currentTimeMillis()
        return if (lastValidFps != null && (now - lastValidFpsTime) < STALENESS_LIMIT_MS) {
            lastValidFps
        } else {
            lastValidFps = null
            null
        }
    }

    suspend fun resetTimeStats() = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) return@withContext
        try {
            AdbCommandRunner.runDetailed("dumpsys SurfaceFlinger --timestats -disable", source = CommandSource.TELEMETRY)
        } catch (_: Throwable) {}
        lastValidFps = null
        lastValidFpsTime = 0L
    }
}
