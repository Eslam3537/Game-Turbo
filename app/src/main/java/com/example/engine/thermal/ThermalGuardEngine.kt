package com.example.engine.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.engine.performance.HardwareThermalSampler
import kotlinx.coroutines.*
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
    val level: ThermalStatusLevel = ThermalStatusLevel.UNKNOWN,
    val batteryTempCelsius: Double? = null,
    val sensorLabel: String? = null,
    val isThrottlingConfirmed: Boolean = false,
    val thermalHeadroom: String = "UNKNOWN",
    val adviceMessageEn: String = "Awaiting thermal telemetry...",
    val adviceMessageAr: String = "بانتظار قراءة الحساسات الحرارية..."
)

/**
 * Thermal Guard Engine with Edge-Trigger and Hysteresis (Fix A7).
 * - Primary trigger: PowerManager.THERMAL_STATUS_SEVERE (real system throttling).
 * - Edge-triggered: fires once upon entering hot state; re-arms only after temperature drops 2°C below threshold for 30 seconds.
 * - Defaults start at UNKNOWN/null (Golden Rule R1).
 */
class ThermalGuardEngine(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _thermalState = MutableStateFlow(ThermalSnapshot())
    val thermalState: StateFlow<ThermalSnapshot> = _thermalState.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    var onSevereThermalTriggered: ((tempC: Double?, isPowerManagerThrottling: Boolean) -> Unit)? = null

    // Safe Mode Hysteresis state
    private var isTriggered = false
    private var coolDownStartTime = 0L
    var batteryTempThreshold: Double = 43.0 // Setting default 43°C (Fix A7)

    fun startMonitoring() {
        refresh()
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
        scope.launch {
            val sample = HardwareThermalSampler.sampleTemperature(context)
            val temp = sample?.temperatureCelsius
            val isThrottle = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && isPowerManagerCurrentlyThrottling()

            val level = when {
                temp == null -> ThermalStatusLevel.UNKNOWN
                temp >= 46.0 || isThrottle -> ThermalStatusLevel.CRITICAL
                temp >= batteryTempThreshold -> ThermalStatusLevel.HOT
                temp >= (batteryTempThreshold - 3.0) -> ThermalStatusLevel.WARM
                else -> ThermalStatusLevel.NORMAL
            }

            updateSnapshot(level, temp, sample?.sensorName, isThrottle)
            checkHysteresisAndTrigger(temp, isThrottle)
        }
    }

    private fun isPowerManagerCurrentlyThrottling(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val status = pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
            return status >= PowerManager.THERMAL_STATUS_SEVERE
        }
        return false
    }

    private fun handlePowerManagerThermalStatus(status: Int) {
        val isThrottle = status >= PowerManager.THERMAL_STATUS_SEVERE
        scope.launch {
            val sample = HardwareThermalSampler.sampleTemperature(context)
            val temp = sample?.temperatureCelsius
            val level = when {
                status >= PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatusLevel.CRITICAL
                status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatusLevel.HOT
                status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatusLevel.WARM
                status == PowerManager.THERMAL_STATUS_NONE || status == PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatusLevel.NORMAL
                else -> ThermalStatusLevel.UNKNOWN
            }

            updateSnapshot(level, temp, sample?.sensorName, isThrottle)
            checkHysteresisAndTrigger(temp, isThrottle)
        }
    }

    /**
     * Edge-trigger with hysteresis: fires once, re-arms only after temp drops 2°C below threshold for 30s.
     */
    private fun checkHysteresisAndTrigger(temp: Double?, isThrottlingConfirmed: Boolean) {
        val isHot = (temp != null && temp >= batteryTempThreshold) || isThrottlingConfirmed

        if (isHot) {
            coolDownStartTime = 0L
            if (!isTriggered) {
                isTriggered = true
                onSevereThermalTriggered?.invoke(temp, isThrottlingConfirmed)
            }
        } else {
            // Check if cooled down 2°C below threshold
            val rearmThreshold = batteryTempThreshold - 2.0
            if (temp != null && temp <= rearmThreshold && !isThrottlingConfirmed) {
                val now = System.currentTimeMillis()
                if (coolDownStartTime == 0L) {
                    coolDownStartTime = now
                } else if ((now - coolDownStartTime) >= 30000L) { // 30 seconds
                    isTriggered = false // Re-arm!
                    coolDownStartTime = 0L
                }
            } else {
                coolDownStartTime = 0L
            }
        }
    }

    private fun updateSnapshot(
        level: ThermalStatusLevel,
        tempCelsius: Double?,
        sensorLabel: String?,
        isThrottling: Boolean
    ) {
        val (headroom, adviceEn, adviceAr) = when (level) {
            ThermalStatusLevel.NORMAL -> Triple(
                "NORMAL",
                "Thermal conditions are stable.",
                "الحالة الحرارية للجهاز مستقرة وتسمح بالأداء المستمر."
            )
            ThermalStatusLevel.WARM -> Triple(
                "WARM",
                "Device warming up normally under load.",
                "حرارة الجهاز ترتفع بشكل طبيعي تحت الحمل."
            )
            ThermalStatusLevel.HOT -> Triple(
                "HOT",
                if (isThrottling) "System throttling confirmed by PowerManager." else "Battery temperature elevated (${tempCelsius?.toInt()}°C).",
                if (isThrottling) "انخفاض سرعة المعالجة مؤكد من إدارة طاقة النظام." else "حرارة البطارية مرتفعة (${tempCelsius?.toInt()}°C)."
            )
            ThermalStatusLevel.CRITICAL -> Triple(
                "CRITICAL",
                "Critical temperature! Safe mode active.",
                "درجة حرارة حرجة! تم تفعيل وضع الأمان التلقائي."
            )
            ThermalStatusLevel.UNKNOWN -> Triple(
                "UNKNOWN",
                "Thermal telemetry N/A.",
                "بيانات الحساسات الحرارية غير متوفرة."
            )
        }

        _thermalState.value = ThermalSnapshot(
            level = level,
            batteryTempCelsius = tempCelsius,
            sensorLabel = sensorLabel ?: "Battery",
            isThrottlingConfirmed = isThrottling,
            thermalHeadroom = headroom,
            adviceMessageEn = adviceEn,
            adviceMessageAr = adviceAr
        )
    }

    fun release() {
        stopMonitoring()
        scope.cancel()
    }
}
