package com.example.engine.performance

import android.util.Log
import com.example.data.AdbCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Genuine Game Frame Rate (FPS) Sampler using SurfaceFlinger latency timestamps.
 * Returns null ("N/A") when game is not active or data cannot be measured.
 */
object GameFpsSampler {
    private const val TAG = "GameFpsSampler"

    suspend fun sampleGameFps(gamePackage: String): Int? = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) return@withContext null

        try {
            // 1. Find SurfaceFlinger active layer for this game
            val listOutput = AdbCommandRunner.run("dumpsys SurfaceFlinger --list") ?: return@withContext null
            val lines = listOutput.lines().map { it.trim() }

            // Prioritize SurfaceView layer (standard for Unity / Unreal / PUBG)
            val gameLayer = lines.firstOrNull { it.contains(gamePackage) && it.contains("SurfaceView") }
                ?: lines.firstOrNull { it.contains(gamePackage) }

            if (gameLayer.isNullOrBlank()) {
                Log.d(TAG, "No active SurfaceFlinger layer found for $gamePackage")
                return@withContext null
            }

            // 2. Fetch SurfaceFlinger frame timestamps for the layer
            val latencyOutput = AdbCommandRunner.run("dumpsys SurfaceFlinger --latency \"$gameLayer\"")
                ?: return@withContext null

            val latencyLines = latencyOutput.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (latencyLines.size < 5) return@withContext null

            // The second timestamp on each line (vsync/ready timestamp)
            val timestamps = mutableListOf<Long>()
            // Skip the first line (refresh period)
            for (i in 1 until latencyLines.size) {
                val parts = latencyLines[i].split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val readyTime = parts[1].toLongOrNull() ?: parts[2].toLongOrNull()
                    if (readyTime != null && readyTime > 0L && readyTime < 9223372036854775800L) {
                        timestamps.add(readyTime)
                    }
                }
            }

            if (timestamps.size < 6) return@withContext null

            // Use the last 30 frames
            val recent = timestamps.takeLast(30)
            val deltaNanos = recent.last() - recent.first()
            if (deltaNanos <= 0L) return@withContext null

            val fps = ((recent.size - 1).toDouble() * 1_000_000_000.0 / deltaNanos.toDouble()).toInt()
            if (fps in 10..144) {
                return@withContext fps
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to sample SurfaceFlinger FPS: ${e.message}")
            null
        }
    }
}
