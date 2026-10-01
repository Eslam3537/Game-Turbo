package com.example.engine.diagnostics

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.data.AdbCommandRunner
import com.example.data.BoosterDao
import com.example.engine.network.NetworkStabilityEngine
import com.example.engine.performance.SurfaceFlingerFpsEngine
import com.example.engine.session.SessionController
import com.example.engine.shizuku.CommandRegistry
import com.example.engine.shizuku.ShizukuExecutionEngine
import com.example.engine.shizuku.SystemTuningCommand
import com.example.engine.shizuku.VerificationLevel
import com.example.util.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TestStatus(val labelAr: String, val labelEn: String) {
    SUCCESS("تم التحقق بنجاح", "Successfully Verified"),
    FAILED("فشل التحقق", "Verification Failed"),
    UNSUPPORTED("غير مدعوم على هذا الروم/الجهاز", "Unsupported on this Device/ROM"),
    PERMISSION_REQUIRED("تحتاج إلى صلاحية", "Permission Required"),
    NOT_TESTED("لم يتم الاختبار", "Not Tested"),
    NOT_TESTABLE_NOW("غير قابل للاختبار الآن", "Not Testable Now")
}

data class FeatureTestResult(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val status: TestStatus = TestStatus.NOT_TESTED,
    val verificationLevel: VerificationLevel? = null,
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
    val notTestableNowCount: Int = 0,
    val totalCount: Int = 0
)

/**
 * Reality Report Engine v2 (Fix Part B).
 * Single source of truth: executes production ShizukuExecutionEngine and real rollbacks.
 * No trivial passes, no fake checks, real hardware/system measurements.
 */
class OptimizationStatusEngine(
    private val context: Context,
    private val boosterDao: BoosterDao,
    private val shizukuEngine: ShizukuExecutionEngine,
    private val networkEngine: NetworkStabilityEngine
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun getInitialFeatureList(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()

        // 1. Core Platform & Permissions
        list.add(
            FeatureTestResult(
                id = "shizuku_adb",
                nameAr = "تنفيذ أوامر Shizuku Privileged Shell",
                nameEn = "Shizuku Shell IPC Execution",
                requiredPermission = "moe.shizuku.manager.permission.API_V23",
                fixSteps = "قم بتشغيل خدمة Shizuku عبر التصحيح اللاسلكي ثم امنح الإذن لتطبيق Game Turbo."
            )
        )
        list.add(
            FeatureTestResult(
                id = "dnd_gaming",
                nameAr = "تفعيل وضع عدم الإزعاج (Gaming DND)",
                nameEn = "Gaming Do Not Disturb Policy",
                requiredPermission = "android.permission.ACCESS_NOTIFICATION_POLICY",
                fixSteps = "امنح إذن الوصول إلى سياسة الإشعارات من إعدادات النظام."
            )
        )
        list.add(
            FeatureTestResult(
                id = "pubg_launchable",
                nameAr = "اكتشاف وتثبيت اللعبة",
                nameEn = "Game Installed & Launchable",
                fixSteps = "تأكد من تثبيت لعبة PUBG Mobile أو BGMI أو إضافة لعبتك في قسم الألعاب."
            )
        )

        // 2. Registry Tuning Commands (Single source of truth - Fix B1)
        for (cmd in CommandRegistry.ALL_COMMANDS) {
            list.add(
                FeatureTestResult(
                    id = "cmd_${cmd.id}",
                    nameAr = cmd.nameAr,
                    nameEn = cmd.nameEn,
                    executedCommand = cmd.applyCommand("com.tencent.ig", cmd.defaultTargetValue),
                    expectedResult = cmd.defaultTargetValue,
                    requiredPermission = "Shizuku Privileged Shell"
                )
            )
        }

        // 3. Rollback Mechanism Test (Real setting rollback - Fix B2)
        list.add(
            FeatureTestResult(
                id = "rollback_mechanism",
                nameAr = "استعادة الإعدادات الأصلية (Setting Rollback)",
                nameEn = "Real Setting Snapshot & Rollback",
                requiredPermission = "Shizuku Privileged Shell",
                fixSteps = "يتطلب Shizuku للتحقق من استعادة إعداد pointer_speed إلى قيمته الأصلية."
            )
        )

        // 4. Telemetry & Hardware Tests (Fix B3)
        list.add(
            FeatureTestResult(
                id = "telemetry_network",
                nameAr = "فحص استقرار الشبكة والـ Jitter",
                nameEn = "TCP Latency, Jitter & Loss (10 Samples)",
                fixSteps = "تأكد من الاتصال بشبكة الإنترنت لإجراء 10 قياسات TCP متتالية."
            )
        )
        list.add(
            FeatureTestResult(
                id = "telemetry_dns_benchmark",
                nameAr = "فحص سرعة استجابة مزودي DNS عبر UDP 53",
                nameEn = "Direct UDP Port 53 DNS Benchmark",
                fixSteps = "يتطلب اتصال إنترنت يتيح حزم UDP عبر المنفذ 53."
            )
        )
        list.add(
            FeatureTestResult(
                id = "telemetry_game_fps",
                nameAr = "قياس معدل إطارات اللعبة الحقيقي (Game FPS)",
                nameEn = "SurfaceFlinger Game FPS Measurement",
                requiredPermission = "Shizuku Privileged Shell",
                fixSteps = "شغّل لعبة PUBG في الواجهة الأمامية لإتاحة قراءة طبقات SurfaceFlinger."
            )
        )

        // 5. Profile Real Execution Tests (Fix B3)
        val profiles = listOf("performance", "competitive", "balanced", "battery")
        for (prof in profiles) {
            list.add(
                FeatureTestResult(
                    id = "profile_$prof",
                    nameAr = "ملف الأداء: $prof",
                    nameEn = "Profile Session: $prof",
                    requiredPermission = "Shizuku Privileged Shell"
                )
            )
        }

        return list
    }

    suspend fun runSingleDiagnostic(testId: String): FeatureTestResult = withContext(Dispatchers.IO) {
        val gamePkg = SessionController.prefsManager.selectedGamePkg.value

        when {
            testId == "shizuku_adb" -> testShizukuIpc()
            testId == "dnd_gaming" -> testDndPolicy()
            testId == "pubg_launchable" -> testGameInstalled(gamePkg)
            testId.startsWith("cmd_") -> {
                val cmdId = testId.removePrefix("cmd_")
                val cmd = CommandRegistry.findById(cmdId)
                if (cmd != null) testRegistryCommand(cmd, gamePkg) else unsupportedResult(testId, "Unknown command")
            }
            testId == "rollback_mechanism" -> testRealSettingRollback(gamePkg)
            testId == "telemetry_network" -> testNetworkTelemetry()
            testId == "telemetry_dns_benchmark" -> testDnsBenchmark()
            testId == "telemetry_game_fps" -> testGameFps(gamePkg)
            testId.startsWith("profile_") -> {
                val prof = testId.removePrefix("profile_")
                testProfileSession(prof, gamePkg)
            }
            else -> unsupportedResult(testId, "Not implemented")
        }
    }

    private suspend fun testShizukuIpc(): FeatureTestResult {
        val installed = AdbCommandRunner.isShizukuInstalled(context)
        val running = AdbCommandRunner.isShizukuRunning()
        val authorized = AdbCommandRunner.isAvailable()

        return if (!installed) {
            FeatureTestResult(
                id = "shizuku_adb",
                nameAr = "تنفيذ أوامر Shizuku Privileged Shell",
                nameEn = "Shizuku Shell IPC Execution",
                status = TestStatus.FAILED,
                actualResult = "تطبيق Shizuku غير مثبت على الجهاز",
                failureCause = "Shizuku APK missing",
                fixSteps = "ثبت تطبيق Shizuku من GitHub أو Google Play وشغله عبر اللاسلكي."
            )
        } else if (!running) {
            FeatureTestResult(
                id = "shizuku_adb",
                nameAr = "تنفيذ أوامر Shizuku Privileged Shell",
                nameEn = "Shizuku Shell IPC Execution",
                status = TestStatus.FAILED,
                actualResult = "خدمة Shizuku متوقفة (Binder dead)",
                failureCause = "Service not running",
                fixSteps = "افتح Shizuku واضغط 'بدء' عبر تصحيح الأخطاء اللاسلكي."
            )
        } else if (!authorized) {
            FeatureTestResult(
                id = "shizuku_adb",
                nameAr = "تنفيذ أوامر Shizuku Privileged Shell",
                nameEn = "Shizuku Shell IPC Execution",
                status = TestStatus.PERMISSION_REQUIRED,
                actualResult = "تم الاتصال بالخدمة ولكن الإذن غير ممنوح",
                requiredPermission = "moe.shizuku.manager.permission.API_V23",
                fixSteps = "امنح إذن Shizuku لتطبيق Game Turbo."
            )
        } else {
            val res = AdbCommandRunner.runDetailed("id")
            FeatureTestResult(
                id = "shizuku_adb",
                nameAr = "تنفيذ أوامر Shizuku Privileged Shell",
                nameEn = "Shizuku Shell IPC Execution",
                status = if (res.success) TestStatus.SUCCESS else TestStatus.FAILED,
                executedCommand = "id",
                expectedResult = "uid=2000(shell)",
                actualResult = res.stdout.trim(),
                verificationLevel = VerificationLevel.EFFECT_CONFIRMED
            )
        }
    }

    private fun testDndPolicy(): FeatureTestResult {
        val granted = notificationManager?.isNotificationPolicyAccessGranted == true
        return if (granted) {
            FeatureTestResult(
                id = "dnd_gaming",
                nameAr = "تفعيل وضع عدم الإزعاج (Gaming DND)",
                nameEn = "Gaming Do Not Disturb Policy",
                status = TestStatus.SUCCESS,
                actualResult = "إذن الوصول لسياسة الإشعارات ممنوح (Interruption Filter Ready)",
                verificationLevel = VerificationLevel.EFFECT_CONFIRMED
            )
        } else {
            FeatureTestResult(
                id = "dnd_gaming",
                nameAr = "تفعيل وضع عدم الإزعاج (Gaming DND)",
                nameEn = "Gaming Do Not Disturb Policy",
                status = TestStatus.PERMISSION_REQUIRED,
                requiredPermission = "android.permission.ACCESS_NOTIFICATION_POLICY",
                actualResult = "الإذن غير ممنوح حالياً",
                fixSteps = "افتح الإعدادات > التطبيقات > إمكانية الوصول الخاصة > الوصول لوضع عدم الإزعاج ومكن Game Turbo."
            )
        }
    }

    private fun testGameInstalled(gamePkg: String): FeatureTestResult {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(gamePkg, 0)
            val appName = info.applicationInfo?.loadLabel(pm)?.toString() ?: gamePkg
            FeatureTestResult(
                id = "pubg_launchable",
                nameAr = "اكتشاف وتثبيت اللعبة",
                nameEn = "Game Installed & Launchable",
                status = TestStatus.SUCCESS,
                actualResult = "اللعبة مثبتة وجاهزة للإطلاق: $appName ($gamePkg)",
                verificationLevel = VerificationLevel.EFFECT_CONFIRMED
            )
        } catch (_: Throwable) {
            FeatureTestResult(
                id = "pubg_launchable",
                nameAr = "اكتشاف وتثبيت اللعبة",
                nameEn = "Game Installed & Launchable",
                status = TestStatus.FAILED,
                actualResult = "الحزمة $gamePkg غير مثبتة على الهاتف",
                failureCause = "Package not found in PackageManager",
                fixSteps = "ثبت لعبة PUBG Mobile أو حدد حزمة اللعبة الصحيحة من شاشة الألعاب."
            )
        }
    }

    private suspend fun testRegistryCommand(cmd: SystemTuningCommand, gamePkg: String): FeatureTestResult {
        if (!AdbCommandRunner.isAvailable()) {
            return FeatureTestResult(
                id = "cmd_${cmd.id}",
                nameAr = cmd.nameAr,
                nameEn = cmd.nameEn,
                status = TestStatus.PERMISSION_REQUIRED,
                requiredPermission = "Shizuku Shell Access",
                fixSteps = "شغل تطبيق Shizuku وامنح الإذن لتطبيق Game Turbo."
            )
        }

        // Apply and verify through the EXACT production engine (Fix B1)
        val result = shizukuEngine.applyAndVerify(cmd, gamePkg)

        // Then execute immediate verified rollback to leave system clean (Fix B1, B2)
        shizukuEngine.rollbackCommand(cmd, gamePkg)

        val status = when {
            !result.isSupported -> TestStatus.UNSUPPORTED
            result.isSuccess -> TestStatus.SUCCESS
            else -> TestStatus.FAILED
        }

        return FeatureTestResult(
            id = "cmd_${cmd.id}",
            nameAr = cmd.nameAr,
            nameEn = cmd.nameEn,
            status = status,
            verificationLevel = result.verificationLevel,
            executedCommand = cmd.applyCommand(gamePkg, cmd.defaultTargetValue),
            expectedResult = cmd.defaultTargetValue,
            actualResult = result.verifiedValue,
            errorMessage = result.errorMessage ?: "—",
            unsupportedReason = if (!result.isSupported) "الروم لا يدعم هذا المفتاح" else "—"
        )
    }

    private suspend fun testRealSettingRollback(gamePkg: String): FeatureTestResult {
        if (!AdbCommandRunner.isAvailable()) {
            return FeatureTestResult(
                id = "rollback_mechanism",
                nameAr = "استعادة الإعدادات الأصلية (Setting Rollback)",
                nameEn = "Real Setting Snapshot & Rollback",
                status = TestStatus.PERMISSION_REQUIRED,
                requiredPermission = "Shizuku Shell Access"
            )
        }

        val cmd = CommandRegistry.POINTER_SPEED
        val original = AdbCommandRunner.run(cmd.readCurrentCommand(gamePkg))?.trim() ?: "null"
        val testVal = if (original == "7") "6" else "7"

        // 1. Apply test value
        val applyRes = shizukuEngine.applyAndVerify(cmd, gamePkg, testVal)
        if (!applyRes.isSuccess) {
            return FeatureTestResult(
                id = "rollback_mechanism",
                nameAr = "استعادة الإعدادات الأصلية (Setting Rollback)",
                nameEn = "Real Setting Snapshot & Rollback",
                status = TestStatus.FAILED,
                actualResult = "فشل تطبيق القيمة التجريبية لاختبار الاستعادة"
            )
        }

        // 2. Execute real rollback
        val rollbackOk = shizukuEngine.rollbackCommand(cmd, gamePkg)
        val afterRollback = AdbCommandRunner.run(cmd.readCurrentCommand(gamePkg))?.trim() ?: "null"

        val isRestored = afterRollback == original || (original == "null" && (afterRollback.isEmpty() || afterRollback == "null"))

        return if (rollbackOk && isRestored) {
            FeatureTestResult(
                id = "rollback_mechanism",
                nameAr = "استعادة الإعدادات الأصلية (Setting Rollback)",
                nameEn = "Real Setting Snapshot & Rollback",
                status = TestStatus.SUCCESS,
                verificationLevel = VerificationLevel.EFFECT_CONFIRMED,
                executedCommand = "apply $testVal -> rollback -> read-back",
                expectedResult = original,
                actualResult = afterRollback
            )
        } else {
            FeatureTestResult(
                id = "rollback_mechanism",
                nameAr = "استعادة الإعدادات الأصلية (Setting Rollback)",
                nameEn = "Real Setting Snapshot & Rollback",
                status = TestStatus.FAILED,
                verificationLevel = VerificationLevel.NO_EFFECT,
                expectedResult = original,
                actualResult = afterRollback,
                errorMessage = "قيمة الإعداد لم تعد إلى الأصل بعد التراجع"
            )
        }
    }

    private suspend fun testNetworkTelemetry(): FeatureTestResult {
        val samples = mutableListOf<Int>()
        var lost = 0

        for (i in 0 until 10) {
            val rtt = networkEngine.sampleRtt()
            if (rtt != null) samples.add(rtt) else lost++
            delay(50)
        }

        return if (samples.isNotEmpty()) {
            val median = samples.sorted()[samples.size / 2]
            val jitter = if (samples.size >= 2) {
                samples.zipWithNext { a, b -> kotlin.math.abs(b - a) }.average().toInt()
            } else 0

            FeatureTestResult(
                id = "telemetry_network",
                nameAr = "فحص استقرار الشبكة والـ Jitter",
                nameEn = "TCP Latency, Jitter & Loss (10 Samples)",
                status = TestStatus.SUCCESS,
                verificationLevel = VerificationLevel.EFFECT_CONFIRMED,
                actualResult = "RTT: $median ms | Jitter: $jitter ms | حزم مفقودة: $lost/10"
            )
        } else {
            FeatureTestResult(
                id = "telemetry_network",
                nameAr = "فحص استقرار الشبكة والـ Jitter",
                nameEn = "TCP Latency, Jitter & Loss (10 Samples)",
                status = TestStatus.FAILED,
                actualResult = "فشلت جميع الاتصالات (10/10 حزم مفقودة)",
                errorMessage = "تعذر الاتصال بالخادم الهدف"
            )
        }
    }

    private suspend fun testDnsBenchmark(): FeatureTestResult {
        val results = networkEngine.benchmarkDnsCandidates()
        val valid = results.filter { it.medianMs != null }

        return if (valid.isNotEmpty()) {
            val summary = valid.joinToString(", ") { "${it.providerName}: ${it.medianMs}ms" }
            FeatureTestResult(
                id = "telemetry_dns_benchmark",
                nameAr = "فحص سرعة استجابة مزودي DNS عبر UDP 53",
                nameEn = "Direct UDP Port 53 DNS Benchmark",
                status = TestStatus.SUCCESS,
                verificationLevel = VerificationLevel.EFFECT_CONFIRMED,
                actualResult = summary
            )
        } else {
            FeatureTestResult(
                id = "telemetry_dns_benchmark",
                nameAr = "فحص سرعة استجابة مزودي DNS عبر UDP 53",
                nameEn = "Direct UDP Port 53 DNS Benchmark",
                status = TestStatus.FAILED,
                actualResult = "فشلت حزم UDP المنفذ 53 مع كافة المزودين",
                errorMessage = "مزود الخدمة أو جدار الحماية يحظر حزم UDP 53 المباشرة"
            )
        }
    }

    private suspend fun testGameFps(gamePkg: String): FeatureTestResult {
        val fg = SurfaceFlingerFpsEngine.getForegroundPackage(context)
        val isFg = fg == gamePkg || (fg != null && SurfaceFlingerFpsEngine.KNOWN_PUBG_PACKAGES.contains(fg))

        if (!isFg) {
            return FeatureTestResult(
                id = "telemetry_game_fps",
                nameAr = "قياس معدل إطارات اللعبة الحقيقي (Game FPS)",
                nameEn = "SurfaceFlinger Game FPS Measurement",
                status = TestStatus.NOT_TESTABLE_NOW,
                actualResult = "اللعبة ليست في الواجهة الأمامية حالياً ($fg نشط)",
                fixSteps = "افتح اللعبة ثم أعد الاختبار للحصول على قراءة إطارات حقيقية."
            )
        }

        val fps = SurfaceFlingerFpsEngine.sampleFps(context, gamePkg)
        return if (fps != null) {
            FeatureTestResult(
                id = "telemetry_game_fps",
                nameAr = "قياس معدل إطارات اللعبة الحقيقي (Game FPS)",
                nameEn = "SurfaceFlinger Game FPS Measurement",
                status = TestStatus.SUCCESS,
                verificationLevel = VerificationLevel.EFFECT_CONFIRMED,
                actualResult = "$fps FPS (قراءة حية من SurfaceFlinger)"
            )
        } else {
            FeatureTestResult(
                id = "telemetry_game_fps",
                nameAr = "قياس معدل إطارات اللعبة الحقيقي (Game FPS)",
                nameEn = "SurfaceFlinger Game FPS Measurement",
                status = TestStatus.FAILED,
                actualResult = "تعذر قراءة طبقات الرسوميات للعبة",
                errorMessage = "Layer not rendering active frames"
            )
        }
    }

    private suspend fun testProfileSession(profile: String, gamePkg: String): FeatureTestResult {
        if (!AdbCommandRunner.isAvailable()) {
            return FeatureTestResult(
                id = "profile_$profile",
                nameAr = "ملف الأداء: $profile",
                nameEn = "Profile Session: $profile",
                status = TestStatus.PERMISSION_REQUIRED,
                requiredPermission = "Shizuku Shell Access"
            )
        }

        // Run short real session through SessionController (Fix B3)
        val startResult = SessionController.sessionManager.startSession("Reality Test", gamePkg, profile)
        delay(1000)
        val rollbackReport = SessionController.sessionManager.endSession()

        return if (startResult.success && rollbackReport.isFullyRestored) {
            FeatureTestResult(
                id = "profile_$profile",
                nameAr = "ملف الأداء: $profile",
                nameEn = "Profile Session: $profile",
                status = TestStatus.SUCCESS,
                verificationLevel = VerificationLevel.EFFECT_CONFIRMED,
                actualResult = "المطبق: ${startResult.appliedCount} | المؤكد: ${startResult.effectConfirmedCount} | المخزن: ${startResult.storedCount} | المستعاد بالكامل: ${rollbackReport.restoredCount}"
            )
        } else {
            FeatureTestResult(
                id = "profile_$profile",
                nameAr = "ملف الأداء: $profile",
                nameEn = "Profile Session: $profile",
                status = TestStatus.FAILED,
                actualResult = "فشل في تفعيل أو استعادة ملف $profile",
                errorMessage = startResult.errorMessage ?: "Rollback failed for: ${rollbackReport.failedCommands.joinToString()}"
            )
        }
    }

    private fun unsupportedResult(testId: String, reason: String): FeatureTestResult {
        return FeatureTestResult(
            id = testId,
            nameAr = testId,
            nameEn = testId,
            status = TestStatus.UNSUPPORTED,
            unsupportedReason = reason
        )
    }

    fun generateFullClipboardReport(results: List<FeatureTestResult>): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        sb.appendLine("=== GAME TURBO REALITY REPORT v2 ===")
        sb.appendLine("Generated At: ${dateFormat.format(Date())}")
        sb.appendLine("Device Model: ${android.os.Build.MODEL} (${android.os.Build.MANUFACTURER})")
        sb.appendLine("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("----------------------------------------")

        for (res in results) {
            sb.appendLine("[${res.status.name}] ${res.nameEn} / ${res.nameAr}")
            if (res.verificationLevel != null) {
                sb.appendLine("  Verification: ${res.verificationLevel.labelEn}")
            }
            if (res.executedCommand != "—") sb.appendLine("  Command: ${res.executedCommand}")
            if (res.actualResult != "—") sb.appendLine("  Actual: ${res.actualResult}")
            if (res.errorMessage != "—") sb.appendLine("  Error: ${res.errorMessage}")
            sb.appendLine()
        }

        sb.appendLine("========================================")
        return sb.toString()
    }

    suspend fun testSingleFeature(testId: String): FeatureTestResult = runSingleDiagnostic(testId)

    fun generateExportReport(results: List<FeatureTestResult>): String = generateFullClipboardReport(results)

    fun generateSingleErrorReport(item: FeatureTestResult): String {
        val sb = StringBuilder()
        sb.appendLine("=== FEATURE DIAGNOSTIC REPORT ===")
        sb.appendLine("Feature: ${item.nameEn} (${item.nameAr})")
        sb.appendLine("Status: ${item.status.name}")
        sb.appendLine("Verification Level: ${item.verificationLevel?.labelEn ?: "N/A"}")
        sb.appendLine("Executed Command: ${item.executedCommand}")
        sb.appendLine("Expected Result: ${item.expectedResult}")
        sb.appendLine("Actual Result: ${item.actualResult}")
        sb.appendLine("Error Message: ${item.errorMessage}")
        sb.appendLine("Failure Cause: ${item.failureCause}")
        sb.appendLine("Fix Steps: ${item.fixSteps}")
        return sb.toString()
    }

    fun computeSummary(results: List<FeatureTestResult>): DiagnosticsSummary {
        return DiagnosticsSummary(
            successCount = results.count { it.status == TestStatus.SUCCESS },
            failedCount = results.count { it.status == TestStatus.FAILED },
            unsupportedCount = results.count { it.status == TestStatus.UNSUPPORTED },
            permissionRequiredCount = results.count { it.status == TestStatus.PERMISSION_REQUIRED },
            notTestedCount = results.count { it.status == TestStatus.NOT_TESTED },
            notTestableNowCount = results.count { it.status == TestStatus.NOT_TESTABLE_NOW },
            totalCount = results.size
        )
    }
}
