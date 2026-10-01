package com.example.engine.performance

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.example.data.AdbCommandRunner
import com.example.data.CommandSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ThermalSample(
    val temperatureCelsius: Double,
    val sensorName: String, // "Skin", "CPU", "Battery"
    val isBatteryTemperature: Boolean
)

/**
 * Unified Real Hardware Thermal Telemetry Engine (Fix A2, A14).
 * Enforces explicit sensor priority: Skin -> CPU -> Battery.
 * Caches dumpsys thermalservice for >= 5 seconds to eliminate continuous shell execution overhead.
 */
object HardwareThermalSampler {
    private const val TAG = "HardwareThermalSampler"
    private const val CACHE_TTL_MS = 5000L

    private var cachedSample: ThermalSample? = null
    private var lastQueryTime = 0L

    suspend fun sampleTemperature(context: Context, forceRefresh: Boolean = false): ThermalSample? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedSample != null && (now - lastQueryTime) < CACHE_TTL_MS) {
            return@withContext cachedSample
        }

        val sample = if (AdbCommandRunner.isAvailable()) {
            sampleViaThermalService() ?: sampleViaDumpsysBattery() ?: sampleViaBatteryBroadcast(context)
        } else {
            sampleViaBatteryBroadcast(context)
        }

        if (sample != null) {
            cachedSample = sample
            lastQueryTime = now
        }
        sample
    }

    /**
     * Queries dumpsys thermalservice with explicit priority: Skin -> CPU -> Battery.
     */
    private suspend fun sampleViaThermalService(): ThermalSample? {
        try {
            val output = AdbCommandRunner.run("dumpsys thermalservice", source = CommandSource.TELEMETRY) ?: return null
            if (output.isBlank() || output.contains("Service not found", ignoreCase = true)) return null

            val lines = output.lines()
            var skinSample: Double? = null
            var cpuSample: Double? = null
            var batterySample: Double? = null

            for (line in lines) {
                if (!line.contains("mValue=", ignoreCase = true)) continue

                // Extract mValue
                val valMatch = Regex("""mValue=([0-9.]+)""").find(line)
                val temp = valMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: continue
                if (temp !in 15.0..95.0) continue // sanity filter

                val lowerLine = line.lowercase()
                when {
                    lowerLine.contains("skin") && skinSample == null -> skinSample = temp
                    lowerLine.contains("cpu") && cpuSample == null -> cpuSample = temp
                    lowerLine.contains("battery") && batterySample == null -> batterySample = temp
                }
            }

            return when {
                skinSample != null -> ThermalSample(skinSample, "Skin", isBatteryTemperature = false)
                cpuSample != null -> ThermalSample(cpuSample, "CPU", isBatteryTemperature = false)
                batterySample != null -> ThermalSample(batterySample, "Battery", isBatteryTemperature = true)
                else -> null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "sampleViaThermalService notice: ${e.message}")
            return null
        }
    }

    private suspend fun sampleViaDumpsysBattery(): ThermalSample? {
        try {
            val output = AdbCommandRunner.run("dumpsys battery", source = CommandSource.TELEMETRY) ?: return null
            val line = output.lines().firstOrNull { it.trim().startsWith("temperature:") } ?: return null
            val raw = line.substringAfter("temperature:").trim().toDoubleOrNull() ?: return null
            val tempC = if (raw > 150.0) raw / 10.0 else raw
            if (tempC in 15.0..85.0) {
                return ThermalSample(tempC, "Battery", isBatteryTemperature = true)
            }
        } catch (_: Throwable) {}
        return null
    }

    fun sampleViaBatteryBroadcast(context: Context): ThermalSample? {
        try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter) ?: return null
            val tempTenths = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            if (tempTenths > 0) {
                val tempC = tempTenths.toDouble() / 10.0
                if (tempC in 15.0..85.0) {
                    return ThermalSample(tempC, "Battery", isBatteryTemperature = true)
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    fun clearCache() {
        cachedSample = null
        lastQueryTime = 0L
    }
}
