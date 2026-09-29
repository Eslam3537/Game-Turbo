package com.example.engine.performance

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.example.data.AdbCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class ThermalSample(
    val temperatureCelsius: Double,
    val sensorName: String,
    val isBatteryTemperature: Boolean
)

/**
 * Real Hardware Thermal Telemetry Engine.
 *
 * Query hierarchy:
 * 1. Shizuku privileged `dumpsys thermalservice`: Real hardware thermal zones (Skin / Battery / CPU).
 * 2. Shizuku privileged `dumpsys battery`: System battery sensor (labeled in code as Battery Temperature).
 * 3. Context IntentFilter `ACTION_BATTERY_CHANGED`: Kernel battery thermistor broadcast.
 *
 * Never fabricates numbers. If no sensor is readable, returns null ("الحرارة: غير متاحة").
 */
object HardwareThermalSampler {
    private const val TAG = "HardwareThermalSampler"

    suspend fun sampleTemperature(context: Context): ThermalSample? = withContext(Dispatchers.IO) {
        // 1. Primary: dumpsys thermalservice via Shizuku
        if (AdbCommandRunner.isAvailable()) {
            val thermalServiceResult = sampleViaThermalService()
            if (thermalServiceResult != null) {
                return@withContext thermalServiceResult
            }

            // 2. Secondary: dumpsys battery via Shizuku
            val batteryServiceResult = sampleViaDumpsysBattery()
            if (batteryServiceResult != null) {
                return@withContext batteryServiceResult
            }
        }

        // 3. Fallback: Android BatteryManager broadcast (Battery Temperature)
        sampleViaBatteryBroadcast(context)
    }

    /**
     * Queries dumpsys thermalservice for active thermal sensors.
     */
    private suspend fun sampleViaThermalService(): ThermalSample? {
        try {
            val output = AdbCommandRunner.run("dumpsys thermalservice") ?: return null
            if (output.isBlank() || output.contains("Service not found", ignoreCase = true)) return null

            // Match patterns like:
            // Temperature{mValue=38.5, mType=0, mName=skin...}
            // or Type: 0 (CPU), Value: 38.5
            val pattern1 = Pattern.compile("mValue=([0-9]+\\.?[0-9]*).*?mName=([^,\\}\\s]+)")
            val matcher1 = pattern1.matcher(output)
            while (matcher1.find()) {
                val tempVal = matcher1.group(1)?.toDoubleOrNull()
                val sensor = matcher1.group(2) ?: "thermal_zone"
                if (tempVal != null && tempVal in 15.0..85.0) {
                    val isBattery = sensor.contains("bat", ignoreCase = true) || sensor.contains("bms", ignoreCase = true)
                    return ThermalSample(
                        temperatureCelsius = tempVal,
                        sensorName = sensor,
                        isBatteryTemperature = isBattery
                    )
                }
            }

            // Match pattern: Current temperatures: ... Value: 38.5
            val pattern2 = Pattern.compile("Value:\\s*([0-9]+\\.?[0-9]*)")
            val matcher2 = pattern2.matcher(output)
            while (matcher2.find()) {
                val tempVal = matcher2.group(1)?.toDoubleOrNull()
                if (tempVal != null && tempVal in 15.0..85.0) {
                    return ThermalSample(
                        temperatureCelsius = tempVal,
                        sensorName = "Hardware Thermal Sensor",
                        isBatteryTemperature = false
                    )
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "sampleViaThermalService error: ${e.message}")
        }
        return null
    }

    /**
     * Queries dumpsys battery via privileged shell.
     * Note: This measures Battery Temperature as designated in requirements.
     */
    private suspend fun sampleViaDumpsysBattery(): ThermalSample? {
        try {
            val output = AdbCommandRunner.run("dumpsys battery") ?: return null
            val pattern = Pattern.compile("temperature:\\s*([0-9]+)")
            val matcher = pattern.matcher(output)
            if (matcher.find()) {
                val raw = matcher.group(1)?.toIntOrNull()
                if (raw != null && raw > 100) {
                    val celsius = raw / 10.0
                    if (celsius in 15.0..75.0) {
                        return ThermalSample(
                            temperatureCelsius = celsius,
                            sensorName = "Battery Temperature",
                            isBatteryTemperature = true
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "sampleViaDumpsysBattery error: ${e.message}")
        }
        return null
    }

    /**
     * Fallback reading from BatteryManager ACTION_BATTERY_CHANGED broadcast.
     * Note: This measures Battery Temperature.
     */
    private fun sampleViaBatteryBroadcast(context: Context): ThermalSample? {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val raw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (raw > 100) {
                val celsius = raw / 10.0
                if (celsius in 15.0..75.0) {
                    ThermalSample(
                        temperatureCelsius = celsius,
                        sensorName = "Battery Temperature",
                        isBatteryTemperature = true
                    )
                } else null
            } else null
        } catch (e: Throwable) {
            Log.d(TAG, "sampleViaBatteryBroadcast error: ${e.message}")
            null
        }
    }
}
