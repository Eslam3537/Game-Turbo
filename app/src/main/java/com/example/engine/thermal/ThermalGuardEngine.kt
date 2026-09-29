package com.example.engine.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThermalStatusLevel {
    NORMAL,
    WARM,
    HOT,
    CRITICAL,
    UNKNOWN
}

data class ThermalSnapshot(
    val level: ThermalStatusLevel = ThermalStatusLevel.NORMAL,
    val batteryTempCelsius: Double? = null,
    val isThrottlingLikely: Boolean = false,
    val thermalHeadroom: String = "OPTIMAL",
    val adviceMessageEn: String = "Thermal conditions are optimal.",
    val adviceMessageAr: String = "الحالة الحرارية للجهاز مستقرة وتسمح بالأداء الكامل."
)

class ThermalGuardEngine(private val context: Context) {
    private val _thermalState = MutableStateFlow(ThermalSnapshot())
    val thermalState: StateFlow<ThermalSnapshot> = _thermalState.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    var onSevereThermalDetected: (() -> Unit)? = null

    fun startMonitoring() {
        refresh()
        // Android 10+ Thermal Status Listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && thermalListener == null) {
                thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                    handlePowerManagerThermalStatus(status)
                }
                try {
                    powerManager.addThermalStatusListener(ContextCompat.getMainExecutor(context), thermalListener!!)
                } catch (_: Throwable) {}
            }
        }
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            try {
                powerManager?.removeThermalStatusListener(thermalListener!!)
            } catch (_: Throwable) {}
            thermalListener = null
        }
    }

    fun refresh() {
        val temp = getBatteryTemperature()
        val level = when {
            temp == null -> ThermalStatusLevel.UNKNOWN
            temp >= 45.0 -> ThermalStatusLevel.CRITICAL
            temp >= 41.0 -> ThermalStatusLevel.HOT
            temp >= 38.0 -> ThermalStatusLevel.WARM
            else -> ThermalStatusLevel.NORMAL
        }
        val isThrottle = temp != null && temp >= 41.5
        updateSnapshot(level, temp, isThrottle)

        if (level == ThermalStatusLevel.HOT || level == ThermalStatusLevel.CRITICAL) {
            onSevereThermalDetected?.invoke()
        }
    }

    private fun handlePowerManagerThermalStatus(status: Int) {
        val (level, isThrottle) = when (status) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT -> Pair(ThermalStatusLevel.NORMAL, false)
            PowerManager.THERMAL_STATUS_MODERATE -> Pair(ThermalStatusLevel.WARM, false)
            PowerManager.THERMAL_STATUS_SEVERE -> Pair(ThermalStatusLevel.HOT, true)
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> Pair(ThermalStatusLevel.CRITICAL, true)
            else -> Pair(ThermalStatusLevel.UNKNOWN, false)
        }
        val currentTemp = getBatteryTemperature()
        updateSnapshot(level, currentTemp, isThrottle)

        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            onSevereThermalDetected?.invoke()
        }
    }

    private fun getBatteryTemperature(): Double? {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (rawTemp > 0) rawTemp / 10.0 else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun updateSnapshot(level: ThermalStatusLevel, temp: Double?, isThrottle: Boolean) {
        val tempStr = temp?.let { String.format("%.1f", it) } ?: "N/A"
        val headroom = when (level) {
            ThermalStatusLevel.NORMAL -> "OPTIMAL"
            ThermalStatusLevel.WARM -> "MODERATE"
            ThermalStatusLevel.HOT -> "LIMITED"
            ThermalStatusLevel.CRITICAL -> "CRITICAL"
            ThermalStatusLevel.UNKNOWN -> "UNAVAILABLE"
        }
        val msgEn = when (level) {
            ThermalStatusLevel.NORMAL -> "Thermal state normal ($tempStr°C). Operating at full efficiency."
            ThermalStatusLevel.WARM -> "Device warming up ($tempStr°C). Throttling mitigation ready."
            ThermalStatusLevel.HOT -> "Thermal throttling detected ($tempStr°C). Safe Mode protection active."
            ThermalStatusLevel.CRITICAL -> "Critical thermal limit reached ($tempStr°C)! Overclocking suspended."
            ThermalStatusLevel.UNKNOWN -> "Hardware thermal state unavailable."
        }
        val msgAr = when (level) {
            ThermalStatusLevel.NORMAL -> "الحرارة طبيعية ($tempStr°م). يعمل العتاد بكامل كفاءته."
            ThermalStatusLevel.WARM -> "ارتفاع طفيف في الحرارة ($tempStr°م). الحماية الحرارية جاهزة."
            ThermalStatusLevel.HOT -> "تم رصد تقييد حراري ($tempStr°م). نمط الأمان يحد من الضغط."
            ThermalStatusLevel.CRITICAL -> "الحرارة بلغت حداً حرجاً ($tempStr°م)! تم تعليق الإعدادات العنيفة."
            ThermalStatusLevel.UNKNOWN -> "بيانات الحرارة غير متاحة حالياً."
        }
        _thermalState.value = ThermalSnapshot(
            level = level,
            batteryTempCelsius = temp,
            isThrottlingLikely = isThrottle,
            thermalHeadroom = headroom,
            adviceMessageEn = msgEn,
            adviceMessageAr = msgAr
        )
    }
}
