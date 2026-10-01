package com.example.engine.performance

import android.util.Log
import com.example.data.AdbCommandRunner
import com.example.data.CommandSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Genuine CPU usage sampler by reading and diffing /proc/stat tick-by-tick (Fix A2).
 * Reads /proc/stat ONCE per tick and diffs against the previous sample (0ms delay, no thread blocking).
 * Tagged as TELEMETRY so it never floods the database (Fix A1).
 */
object CpuUsageSampler {
    private const val TAG = "CpuUsageSampler"

    private data class CpuStat(val total: Long, val idle: Long)

    private var previousStat: CpuStat? = null

    private fun parseProcStat(output: String): CpuStat? {
        val firstLine = output.lines().firstOrNull { it.startsWith("cpu ") } ?: return null
        val parts = firstLine.trim().split("\\s+".toRegex())
        if (parts.size < 5) return null

        val user = parts[1].toLongOrNull() ?: 0L
        val nice = parts[2].toLongOrNull() ?: 0L
        val system = parts[3].toLongOrNull() ?: 0L
        val idle = parts[4].toLongOrNull() ?: 0L
        val iowait = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
        val irq = if (parts.size > 6) parts[6].toLongOrNull() ?: 0L else 0L
        val softirq = if (parts.size > 7) parts[7].toLongOrNull() ?: 0L else 0L
        val steal = if (parts.size > 8) parts[8].toLongOrNull() ?: 0L else 0L

        val total = user + nice + system + idle + iowait + irq + softirq + steal
        val idleTotal = idle + iowait
        return CpuStat(total = total, idle = idleTotal)
    }

    suspend fun sampleCpuUsage(): Int? = withContext(Dispatchers.IO) {
        if (!AdbCommandRunner.isAvailable()) return@withContext null

        try {
            val statOutput = AdbCommandRunner.run("cat /proc/stat", source = CommandSource.TELEMETRY)
                ?: return@withContext null
            val currentStat = parseProcStat(statOutput) ?: return@withContext null

            val prev = previousStat
            previousStat = currentStat

            if (prev == null) {
                return@withContext null // Need at least two samples to calculate rate
            }

            val diffTotal = currentStat.total - prev.total
            val diffIdle = currentStat.idle - prev.idle

            if (diffTotal <= 0L) return@withContext null

            val usage = (((diffTotal - diffIdle).toDouble() * 100.0) / diffTotal.toDouble()).toInt()
            usage.coerceIn(0, 100)
        } catch (e: Throwable) {
            Log.w(TAG, "Error sampling CPU stats: ${e.message}")
            null
        }
    }

    fun reset() {
        previousStat = null
    }
}
