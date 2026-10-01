package com.example.engine.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppPreferencesManager
import com.example.data.BoosterDao
import com.example.data.DiagnosticEventRecord
import com.example.data.GameSessionRecord
import com.example.engine.capability.DeviceCapabilityEngine
import com.example.engine.network.NetworkStabilityEngine
import com.example.engine.performance.FrameTimeMonitor
import com.example.engine.performance.SurfaceFlingerFpsEngine
import com.example.engine.shizuku.CommandRegistry
import com.example.engine.shizuku.RollbackReport
import com.example.engine.shizuku.ShizukuExecutionEngine
import com.example.engine.shizuku.VerificationLevel
import com.example.engine.thermal.ThermalGuardEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GameSessionState {
    IDLE,
    PREPARING,
    OPTIMIZING,
    VERIFYING,
    RUNNING,
    SAFE_MODE_REVERTED,
    ENDING,
    RESTORING,
    COMPLETED,
    FAILED
}

data class SessionActiveReport(
    val sessionId: Long = 0,
    val gameName: String = "",
    val gamePackage: String = "",
    val durationSeconds: Long = 0,
    val state: GameSessionState = GameSessionState.IDLE,
    val baselineRttMs: Int? = null,
    val baselineTempC: Double? = null,
    val baselineJitterMs: Int? = null,
    val baselineFpsMedian: Int? = null,
    val currentRttMs: Int? = null,
    val currentTempC: Double? = null,
    val currentSensorLabel: String? = null,
    val currentJitterMs: Int? = null,
    val gameFps: Int? = null,
    val sessionAvgFps: Int? = null,
    val sessionPeakTempC: Double? = null,
    val appliedOptimizations: Int = 0,
    val verifiedOptimizations: Int = 0,
    val failedOptimizations: Int = 0,
    val rttSpikesDetected: Int = 0,
    val frameSpikesDetected: Int = 0,
    val thermalThrottlingDetected: Boolean = false,
    val isDnsAppliedInSession: Boolean = false,
    val safeModeMessage: String? = null,
    val verdict: String = "MEASURING"
)

data class SessionStartResult(
    val success: Boolean,
    val appliedCount: Int,
    val storedCount: Int,
    val effectConfirmedCount: Int,
    val failedCount: Int,
    val errorMessage: String? = null
) {
    val verifiedCount: Int get() = storedCount + effectConfirmedCount
}

class GameSessionManager(
    private val context: Context,
    private val boosterDao: BoosterDao,
    private val prefsManager: AppPreferencesManager,
    private val shizukuEngine: ShizukuExecutionEngine,
    private val frameMonitor: FrameTimeMonitor,
    private val thermalEngine: ThermalGuardEngine,
    private val networkEngine: NetworkStabilityEngine
) {
    private val TAG = "GameSessionManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _sessionState = MutableStateFlow(GameSessionState.IDLE)
    val sessionState: StateFlow<GameSessionState> = _sessionState.asStateFlow()

    private val _activeReport = MutableStateFlow<SessionActiveReport?>(null)
    val activeReport: StateFlow<SessionActiveReport?> = _activeReport.asStateFlow()

    private var sessionJob: Job? = null
    private var sessionStartTime = 0L
    private var currentSessionId = 0L
    private var activeGamePackage = ""
    private var activeGameName = ""

    private var baselineRtt: Int? = null
    private var baselineJitter: Int? = null
    private var baselineTemp: Double? = null
    private var baselineFpsMedian: Int? = null

    private var isDnsAppliedInSession = false
    private var sessionPeakTemp: Double = 0.0
    private val sessionRttSamples = mutableListOf<Int>()
    private val sessionFpsSamples = mutableListOf<Int>()

    private var initialAppliedCount = 0
    private var initialVerifiedCount = 0
    private var initialFailedCount = 0

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        // Wire Safe Mode thermal protection callback with hysteresis (Fix A7)
        thermalEngine.onSevereThermalTriggered = { tempC, isThrottling ->
            if (prefsManager.safeModeEnabled.value && _sessionState.value == GameSessionState.RUNNING) {
                scope.launch {
                    handleSafeModeTrigger(tempC, isThrottling)
                }
            }
        }
    }

    /**
     * Checks if a previous session was interrupted by process death or system restart.
     */
    suspend fun checkForInterruptedSession(): RollbackReport? = withContext(Dispatchers.IO) {
        val snapshotCount = boosterDao.getSnapshotCount()
        if (snapshotCount > 0 || prefsManager.isSessionActive.value) {
            Log.w(TAG, "Found $snapshotCount snapshots from an interrupted session. Restoring baseline...")
            val report = shizukuEngine.rollbackAll()
            prefsManager.setSessionActiveState(false, 0L)
            boosterDao.insertEvent(
                DiagnosticEventRecord(
                    sessionId = 0L,
                    eventType = "ROLLBACK",
                    title = "Recovery Rollback Executed",
                    description = "Restored ${report.restoredCount} of ${report.totalSnapshots} system settings after interrupted session.",
                    severity = if (report.isFullyRestored) "INFO" else "WARNING"
                )
            )
            report
        } else {
            null
        }
    }

    /**
     * Starts a verified Game Turbo session with the selected profile.
     */
    suspend fun startSession(
        gameName: String,
        gamePackage: String,
        profile: String
    ): SessionStartResult = withContext(Dispatchers.IO) {
        if (_sessionState.value == GameSessionState.RUNNING || _sessionState.value == GameSessionState.OPTIMIZING) {
            return@withContext SessionStartResult(false, 0, 0, 0, 0, "A session is already active")
        }

        _sessionState.value = GameSessionState.PREPARING
        sessionStartTime = System.currentTimeMillis()
        activeGamePackage = gamePackage
        activeGameName = gameName
        sessionRttSamples.clear()
        sessionFpsSamples.clear()

        // 1. Establish baselines
        networkEngine.start()
        frameMonitor.start()
        thermalEngine.startMonitoring()

        val baselineSamples = mutableListOf<Int>()
        for (i in 0 until 10) {
            networkEngine.sampleRtt()?.let { baselineSamples.add(it) }
            delay(60)
        }
        baselineRtt = if (baselineSamples.isNotEmpty()) baselineSamples.sorted()[baselineSamples.size / 2] else null
        baselineJitter = networkEngine.networkProfile.value.jitterMs

        val thermalSample = thermalEngine.thermalState.value
        baselineTemp = thermalSample.batteryTempCelsius
        sessionPeakTemp = baselineTemp ?: 0.0

        // Pre-sample game FPS baseline (up to 5 samples if game already running)
        val initialFpsSamples = mutableListOf<Int>()
        for (i in 0 until 5) {
            val f = SurfaceFlingerFpsEngine.sampleFps(context, gamePackage)
            if (f != null) initialFpsSamples.add(f)
            delay(100)
        }
        baselineFpsMedian = if (initialFpsSamples.isNotEmpty()) initialFpsSamples.sorted()[initialFpsSamples.size / 2] else null

        // 2. Gaming DND
        if (prefsManager.dndEnabled.value && notificationManager?.isNotificationPolicyAccessGranted == true) {
            try {
                val currentFilter = notificationManager.currentInterruptionFilter
                prefsManager.setPreviousDndFilter(currentFilter)
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                boosterDao.insertEvent(
                    DiagnosticEventRecord(
                        sessionId = currentSessionId,
                        eventType = "DND",
                        title = "Gaming DND Filter Active",
                        description = "Interruption filter set to Priority to silence notifications during match.",
                        severity = "INFO"
                    )
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to set DND filter: ${e.message}")
            }
        }

        // 3. Execute profile commands
        _sessionState.value = GameSessionState.OPTIMIZING
        val caps = DeviceCapabilityEngine.scan(context)
        val selectedDns = prefsManager.selectedDns.value
        val commandsWithValues = CommandRegistry.getCommandsForProfile(profile, caps.maxRefreshRateHz, selectedDns)

        var appliedCount = 0
        var storedCount = 0
        var effectConfirmedCount = 0
        var failedCount = 0
        val failureMessages = mutableListOf<String>()

        isDnsAppliedInSession = false

        for ((cmd, targetVal) in commandsWithValues) {
            val result = shizukuEngine.applyAndVerify(cmd, gamePackage, targetVal)
            if (result.isSuccess) {
                appliedCount++
                if (result.verificationLevel == VerificationLevel.EFFECT_CONFIRMED) {
                    effectConfirmedCount++
                } else {
                    storedCount++
                }
                if (cmd.id == "private_dns") {
                    isDnsAppliedInSession = true
                }
            } else {
                failedCount++
                result.errorMessage?.let { failureMessages.add("${cmd.nameEn}: $it") }
            }
        }

        val totalVerified = storedCount + effectConfirmedCount

        // Rule R3: If 0 verified, the session FAILS
        if (totalVerified == 0) {
            Log.e(TAG, "Session activation failed: 0 commands verified")
            _sessionState.value = GameSessionState.FAILED
            shizukuEngine.rollbackAll(gamePackage)
            restoreDnd()
            return@withContext SessionStartResult(
                success = false,
                appliedCount = appliedCount,
                storedCount = 0,
                effectConfirmedCount = 0,
                failedCount = failedCount,
                errorMessage = failureMessages.joinToString("; ").ifBlank { "No optimizations verified" }
            )
        }

        initialAppliedCount = appliedCount
        initialVerifiedCount = totalVerified
        initialFailedCount = failedCount

        // 4. Record session in Room
        val newSession = GameSessionRecord(
            gamePackage = gamePackage,
            gameName = gameName,
            startTime = sessionStartTime,
            profileApplied = profile,
            baselineRttMs = baselineRtt,
            baselineJitterMs = baselineJitter,
            baselineTempC = baselineTemp?.toInt(),
            appliedOptimizationsCount = appliedCount,
            verifiedOptimizationsCount = totalVerified,
            failedOptimizationsCount = failedCount
        )
        currentSessionId = boosterDao.insertSession(newSession)
        prefsManager.setSessionActiveState(true, currentSessionId)

        boosterDao.insertEvent(
            DiagnosticEventRecord(
                sessionId = currentSessionId,
                eventType = "OPTIMIZATION",
                title = "Session Started ($profile)",
                description = "Applied $appliedCount, verified $totalVerified ($effectConfirmedCount confirmed effect, $storedCount stored) for $gameName",
                severity = "INFO"
            )
        )

        _sessionState.value = GameSessionState.RUNNING

        // 5. Initialize Active Report with real numbers immediately (Fix A10)
        _activeReport.value = SessionActiveReport(
            sessionId = currentSessionId,
            gameName = activeGameName,
            gamePackage = activeGamePackage,
            durationSeconds = 0,
            state = GameSessionState.RUNNING,
            baselineRttMs = baselineRtt,
            baselineTempC = baselineTemp,
            baselineJitterMs = baselineJitter,
            baselineFpsMedian = baselineFpsMedian,
            appliedOptimizations = initialAppliedCount,
            verifiedOptimizations = initialVerifiedCount,
            failedOptimizations = initialFailedCount,
            isDnsAppliedInSession = isDnsAppliedInSession
        )

        startActiveMonitoringLoop()

        SessionStartResult(
            success = true,
            appliedCount = appliedCount,
            storedCount = storedCount,
            effectConfirmedCount = effectConfirmedCount,
            failedCount = failedCount
        )
    }

    private fun startActiveMonitoringLoop() {
        sessionJob?.cancel()
        sessionJob = scope.launch {
            while (isActive && (_sessionState.value == GameSessionState.RUNNING || _sessionState.value == GameSessionState.SAFE_MODE_REVERTED)) {
                val rtt = networkEngine.sampleRtt()
                if (rtt != null) {
                    sessionRttSamples.add(rtt)
                    if (sessionRttSamples.size > 50) sessionRttSamples.removeAt(0)
                }

                thermalEngine.refresh()

                val currentFps = SurfaceFlingerFpsEngine.sampleFps(context, activeGamePackage)
                if (currentFps != null) {
                    sessionFpsSamples.add(currentFps)
                    if (sessionFpsSamples.size > 100) sessionFpsSamples.removeAt(0)
                }

                val net = networkEngine.networkProfile.value
                val therm = thermalEngine.thermalState.value

                therm.batteryTempCelsius?.let { t ->
                    if (t > sessionPeakTemp) sessionPeakTemp = t
                }

                val durationSec = (System.currentTimeMillis() - sessionStartTime) / 1000

                val avgFps = if (sessionFpsSamples.isNotEmpty()) {
                    val sorted = sessionFpsSamples.sorted()
                    sorted[sorted.size / 2]
                } else null

                // Fix A10: Meaningful verdict: FPS is primary when available; label RTT honestly
                val verdict = when {
                    sessionFpsSamples.size >= 10 && baselineFpsMedian != null -> {
                        val currentFpsMedian = avgFps ?: 0
                        val baseFps = baselineFpsMedian!!
                        when {
                            currentFpsMedian >= (baseFps + 5) -> "MEASURED_FPS_IMPROVEMENT"
                            currentFpsMedian <= (baseFps - 8) -> "FRAME_RATE_DROPPED"
                            else -> "FPS_STABLE"
                        }
                    }
                    sessionRttSamples.size >= 10 && baselineRtt != null -> {
                        val sessionMedian = sessionRttSamples.sorted()[sessionRttSamples.size / 2]
                        val baseMed = baselineRtt!!
                        val threshold = maxOf((baseMed * 0.10).toInt(), 5)
                        when {
                            baseMed - sessionMedian >= threshold -> {
                                if (isDnsAppliedInSession) "DNS_LATENCY_IMPROVEMENT" else "NETWORK_RTT_LOWER (Unrelated to OS tweaks)"
                            }
                            sessionMedian - baseMed >= threshold -> "NETWORK_RTT_ELEVATED (Carrier jitter)"
                            else -> "NETWORK_RTT_STABLE"
                        }
                    }
                    else -> "MEASURING"
                }

                _activeReport.value = _activeReport.value?.copy(
                    durationSeconds = durationSec,
                    currentRttMs = net.currentRttMs,
                    currentTempC = therm.batteryTempCelsius,
                    currentSensorLabel = therm.sensorLabel,
                    currentJitterMs = net.jitterMs,
                    gameFps = currentFps,
                    sessionAvgFps = avgFps,
                    sessionPeakTempC = sessionPeakTemp,
                    rttSpikesDetected = net.lostSamplesCount,
                    frameSpikesDetected = frameMonitor.timingStats.value.frameSpikesCount,
                    thermalThrottlingDetected = therm.isThrottlingConfirmed,
                    verdict = verdict
                )

                delay(2000)
            }
        }
    }

    /**
     * Fix A7: Safe Mode partial rollback of aggressive commands only.
     */
    private suspend fun handleSafeModeTrigger(tempC: Double?, isThrottling: Boolean) = withContext(Dispatchers.IO) {
        val triggerReason = if (isThrottling) "System Throttling Confirmed by PowerManager" else "Battery Temperature Reached ${tempC?.toInt() ?: 43}°C"

        Log.w(TAG, "Safe Mode activated: $triggerReason. Reverting aggressive tweaks.")
        val report = shizukuEngine.rollbackAggressiveCommands(activeGamePackage)

        boosterDao.insertEvent(
            DiagnosticEventRecord(
                sessionId = currentSessionId,
                eventType = "SAFE_MODE",
                title = "Safe Mode Protection Triggered",
                description = "$triggerReason. Reverted ${report.restoredCount} aggressive tweaks (refresh lock, doze, background kills) to safeguard hardware.",
                severity = "WARNING"
            )
        )

        _sessionState.value = GameSessionState.SAFE_MODE_REVERTED
        val safeMsg = "Safe Mode: aggressive tweaks reverted (${tempC?.toInt()}°C)"

        _activeReport.value = _activeReport.value?.copy(
            state = GameSessionState.SAFE_MODE_REVERTED,
            safeModeMessage = safeMsg
        )

        showSafeModeNotification(safeMsg)
    }

    private fun showSafeModeNotification(msg: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("game_turbo_safety", "Safety Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager?.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(context, "game_turbo_safety")
            .setContentTitle("Game Turbo: وضع الأمان")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            notificationManager?.notify(9001, notif)
        } catch (_: Throwable) {}
    }

    /**
     * Ends session and performs verified rollback of all system changes (Fix A8).
     */
    suspend fun endSession(): RollbackReport = withContext(Dispatchers.IO) {
        _sessionState.value = GameSessionState.ENDING
        sessionJob?.cancel()
        _sessionState.value = GameSessionState.RESTORING

        val rollbackReport = shizukuEngine.rollbackAll(activeGamePackage)
        restoreDnd()

        val durationSec = (System.currentTimeMillis() - sessionStartTime) / 1000
        val net = networkEngine.networkProfile.value
        val therm = thermalEngine.thermalState.value
        val existing = boosterDao.getSessionById(currentSessionId)

        if (existing != null) {
            val avgFps = if (sessionFpsSamples.isNotEmpty()) sessionFpsSamples.sorted()[sessionFpsSamples.size / 2] else null
            val finalVerdict = _activeReport.value?.verdict ?: "COMPLETED"

            boosterDao.updateSession(
                existing.copy(
                    endTime = System.currentTimeMillis(),
                    durationSeconds = durationSec,
                    avgRttMs = net.avgRttMs,
                    maxRttMs = net.maxRttMs,
                    avgJitterMs = net.jitterMs,
                    peakTempC = sessionPeakTemp.toInt(),
                    avgFps = avgFps,
                    thermalThrottlingDetected = therm.isThrottlingConfirmed,
                    rollbackSuccess = rollbackReport.isFullyRestored,
                    summaryVerdict = finalVerdict
                )
            )

            boosterDao.insertEvent(
                DiagnosticEventRecord(
                    sessionId = currentSessionId,
                    eventType = "ROLLBACK",
                    title = "Session Terminated & Rolled Back",
                    description = "Restored ${rollbackReport.restoredCount} of ${rollbackReport.totalSnapshots} system settings. " +
                            if (rollbackReport.failedCommands.isNotEmpty()) "Failed: ${rollbackReport.failedCommands.joinToString()}" else "All baseline settings restored.",
                    severity = if (rollbackReport.isFullyRestored) "INFO" else "WARNING"
                )
            )
        }

        prefsManager.setSessionActiveState(false, 0L)

        _activeReport.value = null
        _sessionState.value = GameSessionState.COMPLETED
        delay(200)
        _sessionState.value = GameSessionState.IDLE

        rollbackReport
    }

    private fun restoreDnd() {
        val prevFilter = prefsManager.previousDndFilter.value
        if (prevFilter != -1 && notificationManager?.isNotificationPolicyAccessGranted == true) {
            try {
                notificationManager.setInterruptionFilter(prevFilter)
                prefsManager.setPreviousDndFilter(-1)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to restore DND filter: ${e.message}")
            }
        }
    }

    fun release() {
        sessionJob?.cancel()
        scope.cancel()
    }
}
