package com.example.engine.detection

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.data.AdbCommandRunner
import com.example.engine.capability.DeviceCapabilityEngine
import com.example.util.PermissionManager

enum class HealthScanVerdict {
    READY,
    READY_WITH_WARNINGS,
    NOT_READY
}

data class HealthScanItem(
    val titleEn: String,
    val titleAr: String,
    val statusEn: String,
    val statusAr: String,
    val isHealthy: Boolean,
    val isWarning: Boolean = false
)

data class DeviceHealthScanReport(
    val verdict: HealthScanVerdict,
    val items: List<HealthScanItem>,
    val batteryPercent: Int,
    val temperatureCelsius: Double?,
    val availableRamMb: Long,
    val refreshRateHz: Float
)

object DeviceHealthScanEngine {
    fun executeScan(context: Context): DeviceHealthScanReport {
        val items = mutableListOf<HealthScanItem>()

        // 1. Battery check
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPercent = if (scale > 0) (rawLevel * 100) / scale else 80
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempCelsius = if (rawTemp > 0) rawTemp / 10.0 else null

        val batteryOk = batteryPercent >= 20
        items.add(
            HealthScanItem(
                titleEn = "Battery Level ($batteryPercent%)",
                titleAr = "مستوى شحن البطارية ($batteryPercent%)",
                statusEn = if (batteryOk) "Adequate for gaming session" else "Low battery (<20%), connect charger to prevent throttle",
                statusAr = if (batteryOk) "الشحن كافٍ لبدء جلسة اللعب" else "البطارية منخفضة (<20%)، يفضل التوصيل بالشاحن",
                isHealthy = batteryOk,
                isWarning = !batteryOk
            )
        )

        // 2. Thermal check
        val tempOk = tempCelsius != null && tempCelsius < 41.0
        val tempStr = tempCelsius?.let { String.format("%.1f", it) } ?: "N/A"
        items.add(
            HealthScanItem(
                titleEn = "Thermal Baseline ($tempStr°C)",
                titleAr = "الحرارة الأساسية ($tempStr°م)",
                statusEn = if (tempOk) "Optimal thermal operating zone" else if (tempCelsius != null) "Device is warm, thermal throttling may occur" else "Thermal sensors unavailable",
                statusAr = if (tempOk) "الحرارة في النطاق الطبيعي المستقر" else if (tempCelsius != null) "الجهاز دافئ وقد يحدث تقييد حراري" else "مستشعرات الحرارة غير متاحة",
                isHealthy = tempOk,
                isWarning = !tempOk
            )
        )

        // 3. RAM check
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        val ramOk = availMb >= 600
        items.add(
            HealthScanItem(
                titleEn = "Available Memory (${availMb} MB)",
                titleAr = "الذاكرة المتاحة (${availMb} ميجابايت)",
                statusEn = if (ramOk) "Sufficient RAM headroom" else "High memory pressure, background apps will be trimmed",
                statusAr = if (ramOk) "سعة الذاكرة كافية للعبة" else "ضغط الذاكرة مرتفع، سيتم تحرير العمليات الخاملة",
                isHealthy = ramOk,
                isWarning = !ramOk
            )
        )

        // 4. Shizuku Privileged Access Check
        val shizukuOk = AdbCommandRunner.isAvailable()
        items.add(
            HealthScanItem(
                titleEn = "Shizuku Privileged Shell",
                titleAr = "صلاحيات Shizuku المتقدمة",
                statusEn = if (shizukuOk) "Authorized & verified" else "Offline or unauthorized (Privileged shell tuning disabled)",
                statusAr = if (shizukuOk) "الخدمة متصلة ومصرح لها بالعمل" else "الخدمة غير متصلة (التعديلات المتقدمة معطلة)",
                isHealthy = shizukuOk,
                isWarning = !shizukuOk
            )
        )

        // 5. System Permissions Check
        val permStatus = PermissionManager.checkStatus(context)
        items.add(
            HealthScanItem(
                titleEn = "System Permissions (${permStatus.grantedCount}/${permStatus.totalRequired})",
                titleAr = "أذونات النظام (${permStatus.grantedCount}/${permStatus.totalRequired})",
                statusEn = if (permStatus.isAllCoreGranted) "All required permissions active" else "Some permissions pending",
                statusAr = if (permStatus.isAllCoreGranted) "كافة الأذونات الأساسية ممنوحة" else "بعض الأذونات تحتاج لمنح",
                isHealthy = permStatus.isAllCoreGranted,
                isWarning = !permStatus.isAllCoreGranted
            )
        )

        val caps = DeviceCapabilityEngine.scan(context)
        val warningsCount = items.count { it.isWarning }
        val verdict = when {
            warningsCount == 0 -> HealthScanVerdict.READY
            warningsCount <= 2 -> HealthScanVerdict.READY_WITH_WARNINGS
            else -> HealthScanVerdict.NOT_READY
        }

        return DeviceHealthScanReport(
            verdict = verdict,
            items = items,
            batteryPercent = batteryPercent,
            temperatureCelsius = tempCelsius,
            availableRamMb = availMb,
            refreshRateHz = caps.maxRefreshRateHz
        )
    }
}
