package com.example.engine.diagnostics

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import com.example.data.AdbCommandRunner
import com.example.data.BoosterDao
import com.example.data.SystemSnapshotRecord
import com.example.engine.capability.DeviceCapabilityEngine
import com.example.util.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TestStatus(val labelAr: String, val labelEn: String) {
    SUCCESS("تم التنفيذ بنجاح", "Successfully Executed"),
    FAILED("فشل التنفيذ", "Execution Failed"),
    UNSUPPORTED("غير مدعومة على هذا الجهاز", "Unsupported on this Device"),
    PERMISSION_REQUIRED("تحتاج إلى صلاحية", "Permission Required"),
    NOT_TESTED("لم يتم الاختبار", "Not Tested")
}

data class FeatureTestResult(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val status: TestStatus = TestStatus.NOT_TESTED,
    val executedCommand: String = "—",
    val expectedResult: String = "—",
    val actualResult: String = "—",
    val errorMessage: String = "—",
    val errorCode: String = "—",
    val failureCause: String = "—",
    val requiredPermission: String = "—",
    val fixSteps: String = "—",
    val unsupportedReason: String = "—",
    val suggestedAlternative: String = "—",
    val isTesting: Boolean = false
)

data class DiagnosticsSummary(
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val unsupportedCount: Int = 0,
    val permissionRequiredCount: Int = 0,
    val notTestedCount: Int = 0,
    val totalCount: Int = 0
)

class OptimizationStatusEngine(
    private val context: Context,
    private val boosterDao: BoosterDao
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    /**
     * Instantiates the complete list of testable features in initial NOT_TESTED state.
     */
    fun getInitialFeatureList(): List<FeatureTestResult> {
        return listOf(
            FeatureTestResult(
                id = "shizuku_adb",
                nameAr = "تنفيذ أوامر ADB أو Shizuku",
                nameEn = "Privileged ADB / Shizuku Execution",
                requiredPermission = "moe.shizuku.manager.permission.API_V23",
                fixSteps = "قم بتثبيت تطبيق Shizuku، وتشغيل الخدمة عبر خيار 'التصحيح اللاسلكي' في خيارات المطور، ثم امنح الإذن لتطبيق Game Turbo."
            ),
            FeatureTestResult(
                id = "game_mode_perf",
                nameAr = "تفعيل Game Mode Performance",
                nameEn = "Game Mode Performance Activation",
                requiredPermission = "Shizuku Shell Access",
                fixSteps = "تأكد من تشغيل Shizuku، وأن الجهاز يدعم واجهة Power HAL للأداء الثابت."
            ),
            FeatureTestResult(
                id = "dnd_gaming",
                nameAr = "تفعيل وضع عدم الإزعاج (Gaming DND)",
                nameEn = "Gaming Do Not Disturb Mode",
                requiredPermission = "android.permission.ACCESS_NOTIFICATION_POLICY",
                fixSteps = "افتح إعدادات الهاتف > التطبيقات > إمكانية الوصول الخاصة > الوصول لوضع عدم الإزعاج، وفعّل الإذن لتطبيق Game Turbo."
            ),
            FeatureTestResult(
                id = "heads_up_notifications",
                nameAr = "إيقاف الإشعارات العائمة",
                nameEn = "Disable Heads-up Floating Notifications",
                requiredPermission = "Shizuku Shell Access (WRITE_SECURE_SETTINGS)",
                fixSteps = "اربط تطبيق Shizuku بالتصحيح اللاسلكي لتتمكن من تعديل إعدادات الإشعارات العائمة على مستوى النظام."
            ),
            FeatureTestResult(
                id = "system_animation",
                nameAr = "تعديل حركة النظام (Animations Scale)",
                nameEn = "System Animation Scaling",
                requiredPermission = "Shizuku Shell Access (WRITE_SECURE_SETTINGS)",
                fixSteps = "امنح صلاحية Shizuku لتعديل مقاييس حركات النوافذ والانتقال وتقليل تأخير العرض."
            ),
            FeatureTestResult(
                id = "thermal_sensor",
                nameAr = "فحص درجة الحرارة (Thermal Sensor)",
                nameEn = "Hardware Thermal Sensor Telemetry",
                requiredPermission = "قراءة مستشعرات البطارية العامة (بدون إذن خاص)",
                fixSteps = "تأكد من عمل مستشعر حرارة البطارية بالنظام وعدم حجب بث ACTION_BATTERY_CHANGED."
            ),
            FeatureTestResult(
                id = "battery_telemetry",
                nameAr = "فحص البطارية (Battery Telemetry)",
                nameEn = "Battery Level & Health Telemetry",
                requiredPermission = "قراءة حالة البطارية العامة",
                fixSteps = "تحقق من عمل خدمة مدير البطارية (BatteryManager) بالنظام."
            ),
            FeatureTestResult(
                id = "pubg_execution",
                nameAr = "فحص تثبيت وتشغيل PUBG Mobile",
                nameEn = "PUBG Mobile Installation & Launch Check",
                requiredPermission = "queries (حزم الألعاب في Manifest)",
                fixSteps = "تأكد من تثبيت إحدى نسخ PUBG Mobile (العالمية أو الهندية أو الكورية أو New State) على الجهاز."
            ),
            FeatureTestResult(
                id = "rollback_system",
                nameAr = "إعادة الإعدادات السابقة (Rollback Engine)",
                nameEn = "Configuration Snapshot & Rollback Engine",
                requiredPermission = "Room Local Database & Shizuku",
                fixSteps = "تأكد من سلامة قاعدة بيانات Room المحلية واتصال Shizuku لتنفيذ أوامر الاستعادة."
            ),
            FeatureTestResult(
                id = "pointer_speed",
                nameAr = "سرعة المؤشر واستجابة اللمس",
                nameEn = "Pointer Speed & Touch Latency Tuning",
                requiredPermission = "Shizuku Shell Access (WRITE_SETTINGS)",
                fixSteps = "اربط تطبيق Shizuku لمنح إذن الكتابة في إعدادات النظام وتعديل سرعة استجابة المؤشر."
            ),
            FeatureTestResult(
                id = "refresh_rate",
                nameAr = "تثبيت أعلى معدل تحديث للشاشة",
                nameEn = "Peak Display Refresh Rate Lock",
                requiredPermission = "Shizuku Shell Access",
                fixSteps = "تأكد من دعم شاشة الهاتف لمعدلات تحديث تفوق 60Hz وربط Shizuku لتثبيت التردد."
            ),
            FeatureTestResult(
                id = "doze_whitelist",
                nameAr = "استثناء اللعبة من قيود البطارية (Doze)",
                nameEn = "Battery Optimization Doze Exemption",
                requiredPermission = "Shizuku Shell Access (dumpsys deviceidle)",
                fixSteps = "امنح إذن Shizuku ليتمكن التطبيق من إضافة اللعبة إلى القائمة البيضاء لنظام Doze."
            ),
            FeatureTestResult(
                id = "private_dns",
                nameAr = "مزود DNS المشفر للألعاب",
                nameEn = "Encrypted Private DNS Configuration",
                requiredPermission = "Shizuku Shell Access (WRITE_SECURE_SETTINGS)",
                fixSteps = "اربط Shizuku لتفعيل وتعيين خادم DNS المشفر للحد من تأخير حزم البيانات."
            )
        )
    }

    /**
     * Tests a single feature individually with real read-back and system checks.
     */
    suspend fun testSingleFeature(featureId: String): FeatureTestResult = withContext(Dispatchers.IO) {
        val initial = getInitialFeatureList().find { it.id == featureId }
            ?: return@withContext FeatureTestResult(
                id = featureId,
                nameAr = "ميزة غير معروفة",
                nameEn = "Unknown Feature",
                status = TestStatus.FAILED,
                errorMessage = "Feature ID not registered"
            )

        when (featureId) {
            "shizuku_adb" -> testShizukuExecution(initial)
            "game_mode_perf" -> testGameModePerformance(initial)
            "dnd_gaming" -> testDndGaming(initial)
            "heads_up_notifications" -> testHeadsUpNotifications(initial)
            "system_animation" -> testSystemAnimation(initial)
            "thermal_sensor" -> testThermalSensor(initial)
            "battery_telemetry" -> testBatteryTelemetry(initial)
            "pubg_execution" -> testPubgExecution(initial)
            "rollback_system" -> testRollbackSystem(initial)
            "pointer_speed" -> testPointerSpeed(initial)
            "refresh_rate" -> testRefreshRate(initial)
            "doze_whitelist" -> testDozeWhitelist(initial)
            "private_dns" -> testPrivateDns(initial)
            else -> initial.copy(status = TestStatus.FAILED, errorMessage = "No test implementation")
        }
    }

    /**
     * 1. Test Shizuku & Privileged Shell Execution
     */
    private suspend fun testShizukuExecution(base: FeatureTestResult): FeatureTestResult {
        val cmd = "id"
        if (!PermissionManager.isShizukuRunning()) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "Shizuku.pingBinder()",
                expectedResult = "Binder alive & communicating",
                actualResult = "Binder is null / not responding",
                errorMessage = "خدمة Shizuku غير مشغلة على الهاتف",
                errorCode = "SHIZUKU_SERVICE_DOWN",
                failureCause = "لم يتم بدء تشغيل خدمة Shizuku عبر التصحيح اللاسلكي",
                fixSteps = "افتح تطبيق Shizuku، واضغط على Start بعد تفعيل التصحيح اللاسلكي (Wireless Debugging)."
            )
        }

        if (!PermissionManager.hasShizukuPermission()) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "Shizuku.checkSelfPermission()",
                expectedResult = "PERMISSION_GRANTED (0)",
                actualResult = "PERMISSION_DENIED (-1)",
                errorMessage = "صلاحية Shizuku لم تُمنح لتطبيق Game Turbo",
                errorCode = "SHIZUKU_PERM_DENIED",
                failureCause = "المستخدم لم يوافق بعد على منح الصلاحية للتطبيق",
                fixSteps = "اضغط على زر منح الصلاحية في مركز الأذونات ووافق على طلب Shizuku."
            )
        }

        val execResult = AdbCommandRunner.runDetailed(cmd)
        return if (execResult.success && execResult.stdout.contains("uid=")) {
            base.copy(
                status = TestStatus.SUCCESS,
                executedCommand = cmd,
                expectedResult = "uid=2000(shell)",
                actualResult = execResult.stdout
            )
        } else {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = cmd,
                expectedResult = "uid=2000(shell)",
                actualResult = execResult.stdout.ifBlank { "فارغ" },
                errorMessage = execResult.stderr.ifBlank { "رمز الخطأ: ${execResult.exitCode}" },
                errorCode = "EXIT_${execResult.exitCode}",
                failureCause = "فشل تنفيذ أمر shell الداخلي عبر Shizuku",
                fixSteps = "أعد تشغيل خدمة Shizuku وتحقق من تفعيل خيارات التصحيح اللاسلكي."
            )
        }
    }

    /**
     * 2. Test Game Mode Performance
     */
    private suspend fun testGameModePerformance(base: FeatureTestResult): FeatureTestResult {
        if (!AdbCommandRunner.isAvailable()) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "cmd power set-fixed-performance-mode-enabled",
                expectedResult = "تمكين وضع الأداء الثابت",
                actualResult = "Shizuku غير متصل",
                errorMessage = "يلزم اتصال Shizuku لتنفيذ أوامر مدير الطاقة (cmd power)",
                errorCode = "SHIZUKU_REQUIRED",
                failureCause = "عدم توفر الصلاحية لتنفيذ أوامر shell",
                fixSteps = "قم بتشغيل وتوصيل تطبيق Shizuku أولاً."
            )
        }

        val applyCmd = "cmd power set-fixed-performance-mode-enabled true"
        val readCmd = "cmd power get-fixed-performance-mode-enabled"

        val applyRes = AdbCommandRunner.runDetailed(applyCmd)
        if (!applyRes.success) {
            val err = applyRes.stderr.lowercase()
            return if (err.contains("unknown command") || err.contains("not implemented") || err.contains("unsupported")) {
                base.copy(
                    status = TestStatus.UNSUPPORTED,
                    executedCommand = applyCmd,
                    expectedResult = "true",
                    actualResult = applyRes.stderr,
                    errorMessage = applyRes.stderr,
                    errorCode = "POWER_HAL_UNSUPPORTED",
                    unsupportedReason = "واجهة Power HAL على معالج هذا الهاتف لا تدعم وضع الأداء الثابت (Fixed Performance Mode).",
                    suggestedAlternative = "الاعتماد على ضبط سرعة استجابة الشاشة وحركات النظام بدلاً منها."
                )
            } else {
                base.copy(
                    status = TestStatus.FAILED,
                    executedCommand = applyCmd,
                    expectedResult = "Exit code 0",
                    actualResult = "Exit code ${applyRes.exitCode}: ${applyRes.stderr}",
                    errorMessage = applyRes.stderr,
                    errorCode = "EXIT_${applyRes.exitCode}",
                    failureCause = "رفض النظام تفعيل وضع الأداء الثابت",
                    fixSteps = "تأكد من عدم وجود قيود أمان خاصة بالشركة المصنعة (مثل MIUI/ColorOS Security)."
                )
            }
        }

        // Read-back verification
        val readRes = AdbCommandRunner.runDetailed(readCmd)
        val readVal = readRes.stdout.trim()
        val isVerified = readVal.equals("true", ignoreCase = true) || readVal.contains("1")

        // Revert immediately so test leaves no side effects
        AdbCommandRunner.runDetailed("cmd power set-fixed-performance-mode-enabled false")

        return if (isVerified) {
            base.copy(
                status = TestStatus.SUCCESS,
                executedCommand = "$applyCmd && $readCmd",
                expectedResult = "true",
                actualResult = "$readVal (تم التحقق واستعادة الوضع الافتراضي)"
            )
        } else {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "$applyCmd && $readCmd",
                expectedResult = "true",
                actualResult = readVal.ifBlank { "قيمة غير مطابقة" },
                errorMessage = "القيمة المقروءة بعد التطبيق لم تتغير إلى true",
                errorCode = "VERIFY_MISMATCH",
                failureCause = "النظام لم يقبل تثبيت نمط الأداء وظل على التردد الديناميكي",
                fixSteps = "جرب إعادة المحاولة بعد فصل الشاحن أو تخفيف الحمل."
            )
        }
    }

    /**
     * 3. Test Gaming DND
     */
    private fun testDndGaming(base: FeatureTestResult): FeatureTestResult {
        if (notificationManager == null) {
            return base.copy(
                status = TestStatus.UNSUPPORTED,
                executedCommand = "NotificationManager",
                expectedResult = "خدمة إدارة الإشعارات متاحة",
                actualResult = "خدمة NotificationManager غير متوفرة",
                errorMessage = "خدمة مدير الإشعارات مفقودة في هذا النظام",
                errorCode = "SERVICE_NULL",
                unsupportedReason = "النظام لا يتيح الوصول لخدمة NotificationManager القياسية.",
                suggestedAlternative = "كتم الصوت يدوياً أثناء اللعب."
            )
        }

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "notificationManager.isNotificationPolicyAccessGranted",
                expectedResult = "true",
                actualResult = "false",
                errorMessage = "إذن الوصول لسياسة الإشعارات غير ممنوح",
                errorCode = "POLICY_ACCESS_DENIED",
                failureCause = "لم يمنح المستخدم إذن عدم الإزعاج للتطبيق",
                fixSteps = "افتح إعدادات الهاتف > التطبيقات > إمكانية الوصول الخاصة > إذن عدم الإزعاج، وامنحه لتطبيق Game Turbo."
            )
        }

        return try {
            val prevFilter = notificationManager.currentInterruptionFilter
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            val currentFilter = notificationManager.currentInterruptionFilter
            // Restore previous filter immediately
            notificationManager.setInterruptionFilter(prevFilter)

            if (currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                base.copy(
                    status = TestStatus.SUCCESS,
                    executedCommand = "setInterruptionFilter(PRIORITY)",
                    expectedResult = "FILTER_PRIORITY (2)",
                    actualResult = "FILTER_PRIORITY (2) (تم التحقق واستعادة الفلتر السابق)"
                )
            } else {
                base.copy(
                    status = TestStatus.FAILED,
                    executedCommand = "setInterruptionFilter(PRIORITY)",
                    expectedResult = "FILTER_PRIORITY (2)",
                    actualResult = "Filter code: $currentFilter",
                    errorMessage = "لم يتغير فلتر عدم الإزعاج إلى وضع الأولوية",
                    errorCode = "FILTER_NOT_APPLIED",
                    failureCause = "واجهة النظام المصنعة تمنع تغيير الفلتر برمجياً",
                    fixSteps = "تحقق من إعدادات وضع الألعاب المدمج في واجهة الهاتف."
                )
            }
        } catch (e: Throwable) {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "setInterruptionFilter()",
                expectedResult = "نجاح الاستدعاء وتغيير الفلتر",
                actualResult = "Exception: ${e.message}",
                errorMessage = e.message ?: "خطأ غير متوقع",
                errorCode = e.javaClass.simpleName,
                failureCause = "حدث خطأ أمني أثناء محاولة تعيين فلتر عدم الإزعاج",
                fixSteps = "أعد منح إذن عدم الإزعاج للتطبيق من إعدادات الهاتف."
            )
        }
    }

    /**
     * 4. Test Disable Heads-up Notifications
     */
    private suspend fun testHeadsUpNotifications(base: FeatureTestResult): FeatureTestResult {
        if (!AdbCommandRunner.isAvailable()) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "settings put global heads_up_notifications_enabled 0",
                expectedResult = "0",
                actualResult = "Shizuku غير متصل",
                errorMessage = "يلزم اتصال Shizuku لتعديل إعدادات الإشعارات العائمة",
                errorCode = "SHIZUKU_REQUIRED",
                failureCause = "لا يمكن لتطبيق عادي كتابة إعدادات النظام العالمية بدون صلاحية Shizuku",
                fixSteps = "اربط تطبيق Shizuku وشغّل الخدمة عبر التصحيح اللاسلكي."
            )
        }

        val orig = AdbCommandRunner.run("settings get global heads_up_notifications_enabled")?.trim()
        val applyRes = AdbCommandRunner.runDetailed("settings put global heads_up_notifications_enabled 0")
        if (!applyRes.success) {
            return base.copy(
                status = TestStatus.FAILED,
                executedCommand = "settings put global heads_up_notifications_enabled 0",
                expectedResult = "Exit code 0",
                actualResult = "Exit code ${applyRes.exitCode}: ${applyRes.stderr}",
                errorMessage = applyRes.stderr,
                errorCode = "EXIT_${applyRes.exitCode}",
                failureCause = "فشل أمر ضبط الإشعارات العائمة",
                fixSteps = "تحقق من أن واجهة النظام لا تفرض حماية إضافية على مفاتيح Global Settings."
            )
        }

        val readBack = AdbCommandRunner.run("settings get global heads_up_notifications_enabled")?.trim()
        // Restore original
        if (orig != null && orig != "0" && orig != "null") {
            AdbCommandRunner.runDetailed("settings put global heads_up_notifications_enabled $orig")
        }

        return if (readBack == "0") {
            base.copy(
                status = TestStatus.SUCCESS,
                executedCommand = "settings put global heads_up_notifications_enabled 0",
                expectedResult = "0",
                actualResult = "0 (تم التحقق وقراءة النتيجة الحقيقية واستعادة الأصل)"
            )
        } else {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "settings put global heads_up_notifications_enabled 0",
                expectedResult = "0",
                actualResult = readBack ?: "null",
                errorMessage = "القيمة بعد التطبيق لم تصبح 0",
                errorCode = "READBACK_MISMATCH",
                failureCause = "تم تجاهل التعديل من قِبل نظام إشعارات الهاتف",
                fixSteps = "تأكد من تفعيل صلاحيات Shizuku بكامل الصلاحيات بدون قيود أمان OEM."
            )
        }
    }

    /**
     * 5. Test System Animation Scale
     */
    private suspend fun testSystemAnimation(base: FeatureTestResult): FeatureTestResult {
        if (!AdbCommandRunner.isAvailable()) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "settings put global window_animation_scale 0.0",
                expectedResult = "0.0",
                actualResult = "Shizuku غير متصل",
                errorMessage = "يلزم اتصال Shizuku لتعديل مقاييس الرسوم",
                errorCode = "SHIZUKU_REQUIRED",
                failureCause = "صلاحيات Shizuku مطلوبة لتعديل Global Settings",
                fixSteps = "شغّل تطبيق Shizuku واربطه عبر التصحيح اللاسلكي."
            )
        }

        val orig = AdbCommandRunner.run("settings get global window_animation_scale")?.trim()
        val applyRes = AdbCommandRunner.runDetailed("settings put global window_animation_scale 0.0")
        val readBack = AdbCommandRunner.run("settings get global window_animation_scale")?.trim()

        // Restore
        val restoreVal = if (orig.isNullOrBlank() || orig == "null") "1.0" else orig
        AdbCommandRunner.runDetailed("settings put global window_animation_scale $restoreVal")

        val num = readBack?.toFloatOrNull()
        return if (num == 0.0f) {
            base.copy(
                status = TestStatus.SUCCESS,
                executedCommand = "settings put global window_animation_scale 0.0",
                expectedResult = "0.0",
                actualResult = "$readBack (تم التحقق واستعادة القيمة الأصلية: $restoreVal)"
            )
        } else {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "settings put global window_animation_scale 0.0",
                expectedResult = "0.0",
                actualResult = readBack ?: "فارغ",
                errorMessage = applyRes.stderr.ifBlank { "القيمة المقروءة لم تتغير إلى 0.0" },
                errorCode = "ANIMATION_MISMATCH",
                failureCause = "فشل تعديل مقياس حركة النوافذ في النظام",
                fixSteps = "تحقق من تمكين خيارات المطور وسماح النظام بتعديل Animation Scales."
            )
        }
    }

    /**
     * 6. Test Hardware Thermal Sensor
     */
    private fun testThermalSensor(base: FeatureTestResult): FeatureTestResult {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (rawTemp > 0) {
                val celsius = rawTemp / 10.0
                base.copy(
                    status = TestStatus.SUCCESS,
                    executedCommand = "BatteryManager.EXTRA_TEMPERATURE",
                    expectedResult = "قراءة حرارية حقيقية (15°C - 70°C)",
                    actualResult = "${String.format(Locale.US, "%.1f", celsius)}°C (قراءة مستشعر موثقة)"
                )
            } else {
                base.copy(
                    status = TestStatus.FAILED,
                    executedCommand = "BatteryManager.EXTRA_TEMPERATURE",
                    expectedResult = "درجة حرارة موجبة",
                    actualResult = "$rawTemp",
                    errorMessage = "أرجع مستشعر البطارية قيمة غير صالحة ($rawTemp)",
                    errorCode = "TEMP_SENSOR_INVALID",
                    failureCause = "مستشعر حرارة البطارية لا يرسل بيانات عبر بث النظام",
                    fixSteps = "أعد تشغيل الجهاز للتحقق من تهيئة مستشعرات العتاد."
                )
            }
        } catch (e: Throwable) {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "ACTION_BATTERY_CHANGED",
                expectedResult = "درجة حرارة موجبة",
                actualResult = "Exception: ${e.message}",
                errorMessage = e.message ?: "فشل تسجيل مستقبل البطارية",
                errorCode = e.javaClass.simpleName,
                failureCause = "تعذر قراءة مستشعر البطارية بالنظام",
                fixSteps = "تأكد من عدم تقييد استقبال رسائل النظام من قِبل تطبيقات توفير الطاقة."
            )
        }
    }

    /**
     * 7. Test Battery Telemetry
     */
    private fun testBatteryTelemetry(base: FeatureTestResult): FeatureTestResult {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

            if (rawLevel >= 0 && scale > 0) {
                val percent = (rawLevel * 100) / scale
                base.copy(
                    status = TestStatus.SUCCESS,
                    executedCommand = "BatteryManager.EXTRA_LEVEL / EXTRA_SCALE",
                    expectedResult = "نسبة مئوية بين 0 و 100%",
                    actualResult = "$percent% (مستوى الشحن الحقيقي)"
                )
            } else {
                base.copy(
                    status = TestStatus.FAILED,
                    executedCommand = "BatteryManager.EXTRA_LEVEL",
                    expectedResult = "مستوى شحن صالح",
                    actualResult = "Level: $rawLevel, Scale: $scale",
                    errorMessage = "تعذر احتساب نسبة البطارية الحقيقية",
                    errorCode = "BATTERY_LEVEL_INVALID",
                    failureCause = "بيانات البث الخاصة بالبطارية غير مكتملة",
                    fixSteps = "أعد تشغيل الهاتف لتحديث خدمة BatteryService."
                )
            }
        } catch (e: Throwable) {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "BatteryManager",
                expectedResult = "نسبة صالحة",
                actualResult = "Exception: ${e.message}",
                errorMessage = e.message ?: "خطأ غير متوقع",
                errorCode = e.javaClass.simpleName,
                failureCause = "استثناء أثناء قراءة بيانات البطارية",
                fixSteps = "تأكد من عمل خدمات أندرويد الأساسية بشكل طبيعي."
            )
        }
    }

    /**
     * 8. Test PUBG Mobile Execution & Installation
     */
    private fun testPubgExecution(base: FeatureTestResult): FeatureTestResult {
        val candidatePackages = listOf(
            "com.tencent.ig" to "PUBG Mobile (العالمية)",
            "com.pubg.imobile" to "BGMI (الهندية)",
            "com.pubg.krmobile" to "PUBG Mobile (الكورية)",
            "com.pubg.newstate" to "NEW STATE Mobile"
        )

        val pm = context.packageManager
        for ((pkg, name) in candidatePackages) {
            try {
                val info = pm.getPackageInfo(pkg, 0)
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    val ver = info.versionName ?: "Unknown"
                    return base.copy(
                        status = TestStatus.SUCCESS,
                        executedCommand = "pm.getLaunchIntentForPackage($pkg)",
                        expectedResult = "العثور على حزمة اللعبة وقابلية التشغيل",
                        actualResult = "مثبت وجاهز للإطلاق: $name ($pkg) إصدار $ver"
                    )
                }
            } catch (_: Throwable) {}
        }

        return base.copy(
            status = TestStatus.FAILED,
            executedCommand = "PackageManager.getPackageInfo(com.tencent.ig / com.pubg.imobile)",
            expectedResult = "حزمة PUBG مثبتة في النظام",
            actualResult = "لم يتم العثور على أي حزمة من حزم PUBG الرسمية",
            errorMessage = "حزمة لعبة PUBG Mobile غير مثبتة على الهاتف",
            errorCode = "PKG_NOT_FOUND",
            failureCause = "اللعبة غير مثبتة أو مثبتة في مساحة خاصة غير قابلة للقراءة",
            fixSteps = "قم بتثبيت لعبة PUBG Mobile أو توجه إلى شاشة 'الألعاب' وأضف حزمة لعبتك المخصصة لتشغيلها."
        )
    }

    /**
     * 9. Test Snapshot & Rollback Engine
     */
    private suspend fun testRollbackSystem(base: FeatureTestResult): FeatureTestResult {
        return try {
            val testKey = "diagnostic_probe_test_key"
            val testOrig = "original_val_7"
            val testApplied = "modified_val_9"

            val snapshot = SystemSnapshotRecord(
                commandId = testKey,
                settingNamespace = "system",
                settingKey = testKey,
                originalValue = testOrig,
                appliedValue = testApplied
            )

            boosterDao.insertSnapshot(snapshot)
            val readBack = boosterDao.getSnapshot(testKey)
            boosterDao.deleteSnapshot(testKey)

            if (readBack != null && readBack.originalValue == testOrig && readBack.appliedValue == testApplied) {
                base.copy(
                    status = TestStatus.SUCCESS,
                    executedCommand = "Room.insertSnapshot() -> getSnapshot() -> deleteSnapshot()",
                    expectedResult = "حفظ واسترجاع اللقطات بدون فقدان بيانات",
                    actualResult = "قاعدة بيانات اللقطات تعمل بكفاءة تامة وتم التحقق من دورة الحفظ والحذف"
                )
            } else {
                base.copy(
                    status = TestStatus.FAILED,
                    executedCommand = "Room Database Snapshot Test",
                    expectedResult = "تطابق السجل المحفوظ",
                    actualResult = "السجل المقروء غير مطابق أو مفقود",
                    errorMessage = "فشلت قراءة اللقطة التجريبية من قاعدة بيانات Room",
                    errorCode = "ROOM_IO_MISMATCH",
                    failureCause = "حدث خطأ في مزامنة قاعدة البيانات المحلية",
                    fixSteps = "تأكد من وجود مساحة تخزين كافية على الجهاز."
                )
            }
        } catch (e: Throwable) {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "Room Database Snapshot Test",
                expectedResult = "نجاح العملية",
                actualResult = "Exception: ${e.message}",
                errorMessage = e.message ?: "خطأ في قاعدة البيانات",
                errorCode = e.javaClass.simpleName,
                failureCause = "فشل الوصول لقاعدة بيانات SQLite",
                fixSteps = "أعد تشغيل التطبيق لتحديث اتصال قاعدة البيانات."
            )
        }
    }

    /**
     * 10. Test Pointer Speed
     */
    private suspend fun testPointerSpeed(base: FeatureTestResult): FeatureTestResult {
        if (!AdbCommandRunner.isAvailable()) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "settings put system pointer_speed 7",
                expectedResult = "7",
                actualResult = "Shizuku غير متصل",
                errorMessage = "صلاحيات Shizuku مطلوبة لتعديل سرعة المؤشر",
                errorCode = "SHIZUKU_REQUIRED",
                failureCause = "لا يمكن لتطبيق عادي تعديل إعدادات النظام بدون تصريح الشل",
                fixSteps = "اربط تطبيق Shizuku لتفعيل صلاحيات التعديل في System Settings."
            )
        }

        val orig = AdbCommandRunner.run("settings get system pointer_speed")?.trim()
        val applyRes = AdbCommandRunner.runDetailed("settings put system pointer_speed 7")
        val readBack = AdbCommandRunner.run("settings get system pointer_speed")?.trim()

        // Restore
        val restoreVal = if (orig.isNullOrBlank() || orig == "null") "0" else orig
        AdbCommandRunner.runDetailed("settings put system pointer_speed $restoreVal")

        return if (readBack == "7") {
            base.copy(
                status = TestStatus.SUCCESS,
                executedCommand = "settings put system pointer_speed 7",
                expectedResult = "7",
                actualResult = "7 (تم التحقق وقراءة القيمة واستعادة الأصل: $restoreVal)"
            )
        } else {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "settings put system pointer_speed 7",
                expectedResult = "7",
                actualResult = readBack ?: "فارغ",
                errorMessage = applyRes.stderr.ifBlank { "القيمة المقروءة لم تتغير إلى 7" },
                errorCode = "POINTER_SPEED_FAILED",
                failureCause = "النظام منع تعديل سرعة المؤشر",
                fixSteps = "تأكد من منح صلاحيات Shizuku وعدم حظر كتابة إعدادات النظام."
            )
        }
    }

    /**
     * 11. Test Peak Refresh Rate
     */
    private suspend fun testRefreshRate(base: FeatureTestResult): FeatureTestResult {
        val caps = DeviceCapabilityEngine.scan(context)
        if (caps.maxRefreshRateHz <= 60.0f) {
            return base.copy(
                status = TestStatus.UNSUPPORTED,
                executedCommand = "Display.supportedModes",
                expectedResult = "تردد شاشة يفوق 60Hz",
                actualResult = "أعلى تردد مدعوم للشاشة: ${caps.maxRefreshRateHz.toInt()}Hz",
                errorMessage = "شاشة الهاتف ثابتة على تردد 60Hz فقط",
                errorCode = "DISPLAY_60HZ_ONLY",
                unsupportedReason = "عتاد شاشة هذا الهاتف لا يدعم معدلات التحديث العالية (90Hz / 120Hz / 144Hz).",
                suggestedAlternative = "التركيز على تقليل حرارة المعالج وتفريغ الذاكرة للحفاظ على ثبات 60 إطاراً في الثانية."
            )
        }

        if (!AdbCommandRunner.isAvailable()) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "settings put system peak_refresh_rate ${caps.maxRefreshRateHz}",
                expectedResult = "${caps.maxRefreshRateHz}",
                actualResult = "Shizuku غير متصل",
                errorMessage = "صلاحيات Shizuku مطلوبة لتثبيت أعلى تردد للشاشة",
                errorCode = "SHIZUKU_REQUIRED",
                failureCause = "لا يمكن لتطبيق عادي كتابة مفاتيح التردد بدون شل مرتفع الصلاحية",
                fixSteps = "شغّل Shizuku عبر التصحيح اللاسلكي وامنح الصلاحية للتطبيق."
            )
        }

        val targetRate = caps.maxRefreshRateHz.toInt().toFloat().toString()
        val orig = AdbCommandRunner.run("settings get system peak_refresh_rate")?.trim()
        val applyRes = AdbCommandRunner.runDetailed("settings put system peak_refresh_rate $targetRate")
        val readBack = AdbCommandRunner.run("settings get system peak_refresh_rate")?.trim()

        // Restore
        if (!orig.isNullOrBlank() && orig != "null") {
            AdbCommandRunner.runDetailed("settings put system peak_refresh_rate $orig")
        } else {
            AdbCommandRunner.runDetailed("settings delete system peak_refresh_rate")
        }

        val readNum = readBack?.toFloatOrNull()
        val targetNum = targetRate.toFloatOrNull()

        return if (readNum != null && targetNum != null && readNum == targetNum) {
            base.copy(
                status = TestStatus.SUCCESS,
                executedCommand = "settings put system peak_refresh_rate $targetRate",
                expectedResult = targetRate,
                actualResult = "$readBack Hz (تم التحقق وقراءة التردد الحقيقي واستعادة الأصل)"
            )
        } else {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "settings put system peak_refresh_rate $targetRate",
                expectedResult = targetRate,
                actualResult = readBack ?: "فارغ",
                errorMessage = applyRes.stderr.ifBlank { "القيمة المقروءة لم تتطابق مع $targetRate" },
                errorCode = "REFRESH_RATE_FAILED",
                failureCause = "واجهة النظام المصنعة منعت تثبيت تردد الشاشة برمجياً",
                fixSteps = "تحقق من تفعيل خيار أعلى تردد للشاشة يدوياً من إعدادات شاشة الهاتف."
            )
        }
    }

    /**
     * 12. Test Doze Whitelist
     */
    private suspend fun testDozeWhitelist(base: FeatureTestResult): FeatureTestResult {
        if (!AdbCommandRunner.isAvailable()) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "dumpsys deviceidle whitelist",
                expectedResult = "قائمة التطبيقات المستثناة من Doze",
                actualResult = "Shizuku غير متصل",
                errorMessage = "صلاحيات Shizuku مطلوبة للوصول لأوامر dumpsys deviceidle",
                errorCode = "SHIZUKU_REQUIRED",
                failureCause = "أوامر dumpsys تتطلب صلاحيات shell عبر Shizuku",
                fixSteps = "قم بتشغيل وربط تطبيق Shizuku عبر التصحيح اللاسلكي."
            )
        }

        val execRes = AdbCommandRunner.runDetailed("dumpsys deviceidle whitelist")
        return if (execRes.success && execRes.stdout.isNotBlank()) {
            base.copy(
                status = TestStatus.SUCCESS,
                executedCommand = "dumpsys deviceidle whitelist",
                expectedResult = "قائمة Doze البيضاء",
                actualResult = "أمر النظام يعمل بنجاح ومصرح به (تم استخراج ${execRes.stdout.lines().size} تطبيق مستثنى)"
            )
        } else {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = "dumpsys deviceidle whitelist",
                expectedResult = "قائمة Doze البيضاء",
                actualResult = execRes.stdout.ifBlank { "فارغ" },
                errorMessage = execRes.stderr,
                errorCode = "EXIT_${execRes.exitCode}",
                failureCause = "فشل استدعاء خدمة deviceidle",
                fixSteps = "تأكد من أن الهاتف لا يعطل خدمة dumpsys في وضع توفير الطاقة."
            )
        }
    }

    /**
     * 13. Test Private DNS
     */
    private suspend fun testPrivateDns(base: FeatureTestResult): FeatureTestResult {
        if (!AdbCommandRunner.isAvailable()) {
            return base.copy(
                status = TestStatus.PERMISSION_REQUIRED,
                executedCommand = "settings put global private_dns_mode hostname",
                expectedResult = "hostname",
                actualResult = "Shizuku غير متصل",
                errorMessage = "يلزم اتصال Shizuku لضبط إعدادات DNS المشفر",
                errorCode = "SHIZUKU_REQUIRED",
                failureCause = "تعديل Private DNS في أندرويد يحتاج إذن WRITE_SECURE_SETTINGS عبر Shizuku",
                fixSteps = "اربط تطبيق Shizuku بالتصحيح اللاسلكي."
            )
        }

        val origMode = AdbCommandRunner.run("settings get global private_dns_mode")?.trim()
        val origSpec = AdbCommandRunner.run("settings get global private_dns_specifier")?.trim()

        val applyCmd = "settings put global private_dns_mode hostname && settings put global private_dns_specifier one.one.one.one"
        val applyRes = AdbCommandRunner.runDetailed(applyCmd)

        val readMode = AdbCommandRunner.run("settings get global private_dns_mode")?.trim()
        val readSpec = AdbCommandRunner.run("settings get global private_dns_specifier")?.trim()

        // Restore
        val restoreModeCmd = if (!origMode.isNullOrBlank() && origMode != "null") "settings put global private_dns_mode $origMode" else "settings put global private_dns_mode off"
        val restoreSpecCmd = if (!origSpec.isNullOrBlank() && origSpec != "null") "settings put global private_dns_specifier $origSpec" else "settings delete global private_dns_specifier"
        AdbCommandRunner.runDetailed("$restoreModeCmd && $restoreSpecCmd")

        val isVerified = readMode == "hostname" && readSpec == "one.one.one.one"

        return if (isVerified) {
            base.copy(
                status = TestStatus.SUCCESS,
                executedCommand = applyCmd,
                expectedResult = "mode=hostname, specifier=one.one.one.one",
                actualResult = "mode=$readMode, specifier=$readSpec (تم التحقق وقراءة الإعداد واستعادة الأصل)"
            )
        } else {
            base.copy(
                status = TestStatus.FAILED,
                executedCommand = applyCmd,
                expectedResult = "mode=hostname, specifier=one.one.one.one",
                actualResult = "mode=$readMode, specifier=$readSpec",
                errorMessage = applyRes.stderr.ifBlank { "لم يتم تحديث قيم Private DNS بالشكل المطلوب" },
                errorCode = "DNS_WRITE_MISMATCH",
                failureCause = "النظام منع تعيين DNS المشفر",
                fixSteps = "تأكد من أن شبكة الإنترنت الحالية لا تحجب خوادم DNS المشفرة."
            )
        }
    }

    /**
     * Computes the real counters summary.
     */
    fun computeSummary(results: List<FeatureTestResult>): DiagnosticsSummary {
        var success = 0
        var failed = 0
        var unsupported = 0
        var permission = 0
        var notTested = 0

        for (item in results) {
            when (item.status) {
                TestStatus.SUCCESS -> success++
                TestStatus.FAILED -> failed++
                TestStatus.UNSUPPORTED -> unsupported++
                TestStatus.PERMISSION_REQUIRED -> permission++
                TestStatus.NOT_TESTED -> notTested++
            }
        }
        return DiagnosticsSummary(
            successCount = success,
            failedCount = failed,
            unsupportedCount = unsupported,
            permissionRequiredCount = permission,
            notTestedCount = notTested,
            totalCount = results.size
        )
    }

    /**
     * Formats the entire diagnostic report for "نسخ الكل" according to the user's exact template,
     * ensuring sensitive tokens and passwords are redacted.
     */
    fun generateExportReport(results: List<FeatureTestResult>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val scanTime = dateFormat.format(Date())

        val successItems = results.filter { it.status == TestStatus.SUCCESS }
        val failedItems = results.filter { it.status == TestStatus.FAILED }
        val unsupportedItems = results.filter { it.status == TestStatus.UNSUPPORTED }
        val permissionItems = results.filter { it.status == TestStatus.PERMISSION_REQUIRED }

        val sb = StringBuilder()
        sb.appendLine("اسم التطبيق: Game Turbo")
        sb.appendLine("إصدار التطبيق: 1.0")
        sb.appendLine("إصدار Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("الشركة المصنعة: ${Build.MANUFACTURER}")
        sb.appendLine("طراز الهاتف: ${Build.MODEL}")
        sb.appendLine("وقت الفحص: $scanTime")
        sb.appendLine()

        sb.appendLine("الميزات التي اشتغلت:")
        if (successItems.isEmpty()) {
            sb.appendLine("- لا توجد ميزات تم تأكيد نجاحها حتى الآن.")
        } else {
            for (item in successItems) {
                sb.appendLine("- اسم الميزة: ${item.nameAr} (${item.nameEn}): تم التنفيذ")
                sb.appendLine("  النتيجة الفعلية: ${sanitize(item.actualResult)}")
                sb.appendLine("  الأمر المستخدم: ${sanitize(item.executedCommand)}")
            }
        }
        sb.appendLine()

        sb.appendLine("الميزات التي لم تشتغل:")
        if (failedItems.isEmpty()) {
            sb.appendLine("- لا توجد ميزات فاشلة.")
        } else {
            for (item in failedItems) {
                sb.appendLine("- اسم الميزة: ${item.nameAr} (${item.nameEn})")
                sb.appendLine("  الحالة: ${item.status.labelAr}")
                sb.appendLine("  الأمر المستخدم: ${sanitize(item.executedCommand)}")
                sb.appendLine("  النتيجة المتوقعة: ${sanitize(item.expectedResult)}")
                sb.appendLine("  النتيجة الفعلية: ${sanitize(item.actualResult)}")
                sb.appendLine("  رسالة الخطأ: ${sanitize(item.errorMessage)}")
                sb.appendLine("  كود الخطأ: ${item.errorCode}")
                sb.appendLine("  سبب الفشل: ${item.failureCause}")
                sb.appendLine("  طريقة الإصلاح: ${item.fixSteps}")
            }
        }
        sb.appendLine()

        sb.appendLine("الميزات غير المدعومة:")
        if (unsupportedItems.isEmpty()) {
            sb.appendLine("- لا توجد ميزات غير مدعومة.")
        } else {
            for (item in unsupportedItems) {
                sb.appendLine("- اسم الميزة: ${item.nameAr} (${item.nameEn})")
                sb.appendLine("  سبب عدم الدعم: ${item.unsupportedReason}")
                sb.appendLine("  البديل المقترح: ${item.suggestedAlternative}")
            }
        }
        sb.appendLine()

        sb.appendLine("الصلاحيات الناقصة:")
        if (permissionItems.isEmpty()) {
            sb.appendLine("- كافة الصلاحيات المطلوبة مكتملة.")
        } else {
            for (item in permissionItems) {
                sb.appendLine("- اسم الصلاحية: ${item.requiredPermission} (لميزة: ${item.nameAr})")
                sb.appendLine("  طريقة منحها: ${item.fixSteps}")
            }
        }

        return sb.toString().trim()
    }

    /**
     * Generates error details for a single failed feature.
     */
    fun generateSingleErrorReport(item: FeatureTestResult): String {
        return """
            اسم الميزة: ${item.nameAr} (${item.nameEn})
            الحالة: ${item.status.labelAr}
            الأمر المستخدم: ${sanitize(item.executedCommand)}
            النتيجة المتوقعة: ${sanitize(item.expectedResult)}
            النتيجة الفعلية: ${sanitize(item.actualResult)}
            رسالة الخطأ: ${sanitize(item.errorMessage)}
            كود الخطأ: ${item.errorCode}
            سبب الفشل: ${item.failureCause}
            الصلاحية المطلوبة: ${item.requiredPermission}
            إصدار Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            الشركة المصنعة: ${Build.MANUFACTURER} (${Build.MODEL})
            خطوات الإصلاح: ${item.fixSteps}
        """.trimIndent()
    }

    /**
     * Sanitizes sensitive tokens, passwords, and authorization keys from text.
     */
    private fun sanitize(input: String): String {
        return input
            .replace("(?i)(password|secret|token|apikey|key)=[\\w\\d_-]+".toRegex(), "$1=REDACTED")
            .replace("(?i)bearer\\s+[\\w\\d_.-]+".toRegex(), "Bearer REDACTED")
    }
}
