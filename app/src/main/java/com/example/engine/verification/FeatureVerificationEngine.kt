package com.example.engine.verification

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.AdbCommandRunner
import com.example.data.AppPreferencesManager
import com.example.engine.shizuku.CommandRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class FeatureVerificationStatus(
    val textAr: String,
    val textEn: String
) {
    VERIFIED_ACTIVE("مفعل ومؤكد", "Verified Active"),
    VERIFIED_INACTIVE("غير مفعل", "Verified Inactive"),
    UNSUPPORTED("غير مدعوم على هذا الجهاز", "Unsupported on Device"),
    PERMISSION_REQUIRED("يلزم صلاحيات Shizuku", "Permission Required"),
    ACTIVATION_FAILED("فشل التطبيق", "Activation Failed"),
    UNKNOWN("غير مؤكد", "Not Verified")
}

data class FeatureVerificationItem(
    val featureId: String,
    val nameAr: String,
    val nameEn: String,
    val status: FeatureVerificationStatus,
    val currentValue: String,
    val expectedValue: String,
    val detailMessageAr: String,
    val detailMessageEn: String,
    val resetsAfterReboot: Boolean = true
)

data class ReconciliationReport(
    val isHyperOsDetected: Boolean,
    val deviceModel: String,
    val items: List<FeatureVerificationItem>,
    val anyDiscrepancyFound: Boolean,
    val summaryAr: String,
    val summaryEn: String
)

class FeatureVerificationEngine(
    private val context: Context,
    private val prefsManager: AppPreferencesManager
) {
    private val TAG = "FeatureVerificationEngine"
    private val _verificationItems = MutableStateFlow<Map<String, FeatureVerificationItem>>(emptyMap())
    val verificationItems: StateFlow<Map<String, FeatureVerificationItem>> = _verificationItems.asStateFlow()

    private val _reconciliationReport = MutableStateFlow<ReconciliationReport?>(null)
    val reconciliationReport: StateFlow<ReconciliationReport?> = _reconciliationReport.asStateFlow()

    fun isHyperOs(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || manufacturer.contains("poco") ||
                brand.contains("xiaomi") || brand.contains("poco") ||
                !getSystemProperty("ro.mi.os.version.name").isNullOrBlank() ||
                !getSystemProperty("ro.miui.ui.version.name").isNullOrBlank()
    }

    private fun getSystemProperty(prop: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, prop) as? String
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Executes real system read-backs, verifies active state against actual system values.
     */
    suspend fun reconcileStartupState(): ReconciliationReport = withContext(Dispatchers.IO) {
        val hyperOs = isHyperOs()
        val shizukuReady = AdbCommandRunner.isAvailable()
        val results = mutableListOf<FeatureVerificationItem>()
        var discrepancy = false

        val commands = CommandRegistry.getAllTuningCommands()
        val targetGamePkg = prefsManager.selectedGamePkg.value

        for (cmd in commands) {
            if (!shizukuReady) {
                results.add(
                    FeatureVerificationItem(
                        featureId = cmd.id,
                        nameAr = cmd.nameAr,
                        nameEn = cmd.nameEn,
                        status = FeatureVerificationStatus.PERMISSION_REQUIRED,
                        currentValue = "N/A",
                        expectedValue = cmd.defaultTargetValue,
                        detailMessageAr = "خدمة Shizuku غير متصلة للقراءة من النظام",
                        detailMessageEn = "Shizuku service offline, cannot read from system",
                        resetsAfterReboot = true
                    )
                )
                continue
            }

            // Real read-back from system
            val readValue = AdbCommandRunner.run(cmd.readCurrentCommand(targetGamePkg))?.trim()
            val expected = cmd.defaultTargetValue

            val isActive = if (readValue != null) cmd.isInModifiedState(readValue, expected, targetGamePkg) else false
            val status = when {
                readValue == null -> FeatureVerificationStatus.UNSUPPORTED
                isActive -> FeatureVerificationStatus.VERIFIED_ACTIVE
                else -> FeatureVerificationStatus.VERIFIED_INACTIVE
            }

            results.add(
                FeatureVerificationItem(
                    featureId = cmd.id,
                    nameAr = cmd.nameAr,
                    nameEn = cmd.nameEn,
                    status = status,
                    currentValue = readValue ?: "Default",
                    expectedValue = expected,
                    detailMessageAr = if (hyperOs) "يُعاد تعيينه تلقائياً عند إعادة تشغيل واجهة HyperOS" else "يُعاد تعيين الإعدادات تلقائياً عند إعادة تشغيل الهاتف",
                    detailMessageEn = "Setting resets automatically after system reboot",
                    resetsAfterReboot = true
                )
            )
        }

        // Compare with sessionActive preference
        val expectedActive = prefsManager.isSessionActive.value
        val actualActiveCount = results.count { it.status == FeatureVerificationStatus.VERIFIED_ACTIVE }
        if (expectedActive && actualActiveCount == 0) {
            discrepancy = true
            Log.w(TAG, "Reconciliation: Session was expected active, but values reset after system reboot.")
            prefsManager.setSessionActiveState(false, 0L)
        }

        val summaryAr = if (discrepancy) {
            "تمت مطابقة الحالة: أُعيدت قيم النظام إلى وضعها الافتراضي بعد إعادة التشغيل."
        } else {
            "تم التحقق من حالة النظام ومطابقتها مع الإعدادات المخزنة."
        }
        val summaryEn = if (discrepancy) {
            "State reconciled: System parameters reverted to default state after reboot."
        } else {
            "System state verified and reconciled with persistent preferences."
        }

        val report = ReconciliationReport(
            isHyperOsDetected = hyperOs,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            items = results,
            anyDiscrepancyFound = discrepancy,
            summaryAr = summaryAr,
            summaryEn = summaryEn
        )

        _verificationItems.value = results.associateBy { it.featureId }
        _reconciliationReport.value = report
        report
    }
}
