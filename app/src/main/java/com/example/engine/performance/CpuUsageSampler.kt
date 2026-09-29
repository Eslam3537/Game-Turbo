package com.example.engine.performance

import android.util.Log
import com.example.data.AdbCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Genuine CPU usage sampler by reading and diffing /proc/stat via Shizuku.
 * Returns null ("N/A") when Shizuku is not available or stats cannot be parsed.
 */
object CpuUsageSampler {
    private const val TAG = "CpuUsageSampler"

    private data class CpuStat(val total: Long, val idle: Long)

    private fun parseProcStat(output: String): CpuStat? {
        val firstLine = output.lines().firstOrNull { it.startsWith("cpu ") } ?: return null
        val parts = firstLine.trim().split("\\s+".toRegex())
        if (parts.size < 5) return null

        // parts[0] is "cpu"
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
            val stat1Output = AdbCommandRunner.run("cat /proc/stat") ?: return@withContext null
            val stat1 = parseProcStat(stat1Output) ?: return@withContext null

            delay(350)

            val stat2Output = AdbCommandRunner.run("cat /proc/stat") ?: return@withContext null
            val stat2 = parseProcStat(stat2Output) ?: return@withContext null

            val diffTotal = stat2.total - stat1.total
            val diffIdle = stat2.idle - stat1.idle

            if (diffTotal <= 0L) return@withContext null

            val usage = (((diffTotal - diffIdle).toDouble() * 100.0) / diffTotal.toDouble()).toInt()
            usage.coerceIn(0, 100)
        } catch (e: Throwable) {
            Log.e(TAG, "Error sampling CPU stats: ${e.message}")
            null
        }
    }
}
