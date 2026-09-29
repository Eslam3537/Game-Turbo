package com.example.engine.session

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.AppPreferencesManager
import com.example.data.BoosterDao
import com.example.data.DiagnosticEventRecord
import com.example.data.GameSessionRecord
import com.example.engine.capability.DeviceCapabilityEngine
import com.example.engine.network.NetworkStabilityEngine
import com.example.engine.performance.FrameTimeMonitor
import com.example.engine.performance.GameFpsSampler
import com.example.engine.shizuku.CommandRegistry
import com.example.engine.shizuku.RollbackReport
import com.example.engine.shizuku.ShizukuExecutionEngine
import com.example.engine.thermal.ThermalGuardEngine
import com.example.engine.thermal.ThermalStatusLevel
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
    val currentRttMs: Int? = null,
    val currentTempC: Double? = null,
    val currentJitterMs: Int? = null,
    val gameFps: Int? = null,
    val appliedOptimizations: Int = 0,
    val verifiedOptimizations: Int = 0,
    val failedOptimizations: Int = 0,
    val rttSpikesDetected: Int = 0,
    val frameSpikesDetected: Int = 0,
    val thermalThrottlingDetected: Boolean = false,
    val verdict: String = "MEASURING"
)

data class SessionStartResult(
    val success: Boolean,
    val appliedCount: Int,
    val verifiedCount: Int,
    val failedCount: Int,
    val errorMessage: String? = null
)

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

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        // Wire Safe Mode thermal protection callback
        thermalEngine.onSevereThermalDetected = {
            if (prefsManager.safeModeEnabled.value && _sessionState.value == GameSessionState.RUNNING) {
                scope.launch {
                    handleSafeModeTrigger()
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
            Log.w(TAG, "Found $snapshotCount orphaned snapshots from an interrupted session. Restoring baseline...")
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
            return@withContext SessionStartResult(false, 0, 0, 0, "A session is already active")
        }

        _sessionState.value = GameSessionState.PREPARING
        sessionStartTime = System.currentTimeMillis()
        activeGamePackage = gamePackage
        activeGameName = gameName

        // 1. Establish real baselines
        networkEngine.start()
        frameMonitor.start()
        thermalEngine.startMonitoring()

        val rttInitial = networkEngine.sampleRtt()
        baselineRtt = rttInitial
        baselineJitter = networkEngine.networkProfile.value.jitterMs
        baselineTemp = thermalEngine.thermalState.value.batteryTempCelsius

        // 2. Gaming DND: set interruption filter if granted & enabled
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
                        description = "Interruption filter set to Priority to prevent notifications during match.",
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
        val commandsWithValues = CommandRegistry.getCommandsForProfile(profile, caps.maxRefreshRateHz)

        var appliedCount = 0
        var verifiedCount = 0
        var failedCount = 0
        val failureMessages = mutableListOf<String>()

        for ((cmd, targetVal) in commandsWithValues) {
            val result = shizukuEngine.applyAndVerify(cmd, gamePackage, targetVal)
            if (result.isSuccess) {
                appliedCount++
                verifiedCount++
            } else {
                failedCount++
                result.errorMessage?.let { failureMessages.add("${cmd.nameEn}: $it") }
            }
        }

        _sessionState.value = GameSessionState.VERIFYING

        // Non-negotiable rule 3: If 0 verified, the session FAILS!
        if (verifiedCount == 0) {
            Log.e(TAG, "Session activation failed: 0 commands verified")
            _sessionState.value = GameSessionState.FAILED
            // Roll back any partially applied settings
            shizukuEngine.rollbackAll(gamePackage)
            restoreDnd()
            return@withContext SessionStartResult(
                success = false,
                appliedCount = appliedCount,
                verifiedCount = 0,
                failedCount = failedCount,
                errorMessage = failureMessages.joinToString("; ").ifBlank { "No optimizations could be verified" }
            )
        }

        // Insert Session in Database
        val newSession = GameSessionRecord(
            gamePackage = gamePackage,
            gameName = gameName,
            startTime = sessionStartTime,
            profileApplied = profile,
            baselineRttMs = baselineRtt,
            baselineJitterMs = baselineJitter,
            baselineTempC = baselineTemp?.toInt(),
            appliedOptimizationsCount = appliedCount,
            verifiedOptimizationsCount = verifiedCount,
            failedOptimizationsCount = failedCount
        )
        currentSessionId = boosterDao.insertSession(newSession)
        prefsManager.setSessionActiveState(true, currentSessionId)

        boosterDao.insertEvent(
            DiagnosticEventRecord(
                sessionId = currentSessionId,
                eventType = "OPTIMIZATION",
                title = "Session Started ($profile)",
                description = "Applied $appliedCount, verified $verifiedCount settings for $gameName",
                severity = "INFO"
            )
        )

        _sessionState.value = GameSessionState.RUNNING

        // Launch monitoring loop
        startActiveMonitoringLoop()

        SessionStartResult(
            success = true,
            appliedCount = appliedCount,
            verifiedCount = verifiedCount,
            failedCount = failedCount
        )
    }

    private fun startActiveMonitoringLoop() {
        sessionJob?.cancel()
        sessionJob = scope.launch {
            while (isActive && _sessionState.value == GameSessionState.RUNNING) {
                networkEngine.sampleRtt()
                thermalEngine.refresh()
                val currentFps = GameFpsSampler.sampleGameFps(activeGamePackage)

                val net = networkEngine.networkProfile.value
                val therm = thermalEngine.thermalState.value
                val durationSec = (System.currentTimeMillis() - sessionStartTime) / 1000

                // Statistically meaningful verdict requires >= 5 samples
                val verdict = when {
                    net.samplesCount < 5 -> "COLLECTING_DATA"
                    net.avgRttMs != null && baselineRtt != null && net.avgRttMs < (baselineRtt!! - 4) -> "MEASURED_IMPROVEMENT"
                    net.avgRttMs != null && baselineRtt != null && net.avgRttMs > (baselineRtt!! + 8) -> "ELEVATED_LATENCY"
                    else -> "MEASURED_STABLE"
                }

                _activeReport.value = SessionActiveReport(
                    sessionId = currentSessionId,
                    gameName = activeGameName,
                    gamePackage = activeGamePackage,
                    durationSeconds = durationSec,
                    state = GameSessionState.RUNNING,
                    baselineRttMs = baselineRtt,
                    baselineTempC = baselineTemp,
                    baselineJitterMs = baselineJitter,
                    currentRttMs = net.currentRttMs,
                    currentTempC = therm.batteryTempCelsius,
                    currentJitterMs = net.jitterMs,
                    gameFps = currentFps,
                    appliedOptimizations = _activeReport.value?.appliedOptimizations ?: 0,
                    verifiedOptimizations = _activeReport.value?.verifiedOptimizations ?: 0,
                    failedOptimizations = _activeReport.value?.failedOptimizations ?: 0,
                    rttSpikesDetected = net.lostSamplesCount,
                    frameSpikesDetected = frameMonitor.timingStats.value.frameSpikesCount,
                    thermalThrottlingDetected = therm.isThrottlingLikely,
                    verdict = verdict
                )

                delay(2000)
            }
        }
    }

    private suspend fun handleSafeModeTrigger() = withContext(Dispatchers.IO) {
        boosterDao.insertEvent(
            DiagnosticEventRecord(
                sessionId = currentSessionId,
                eventType = "SAFE_MODE",
                title = "Thermal Protection Activated",
                description = "High thermal pressure detected. Reverting aggressive performance tweaks to protect hardware.",
                severity = "WARNING"
            )
        )
        // Rollback high-risk commands while leaving session alive
        shizukuEngine.rollbackAll(activeGamePackage)
    }

    /**
     * Ends session and performs verified rollback of all system changes.
     * MUST be awaited before UI reports restored state!
     */
    suspend fun endSession(): RollbackReport = withContext(Dispatchers.IO) {
        _sessionState.value = GameSessionState.ENDING
        sessionJob?.cancel()
        _sessionState.value = GameSessionState.RESTORING

        val rollbackReport = shizukuEngine.rollbackAll(activeGamePackage)
        restoreDnd()

        // Update session record in DB
        val durationSec = (System.currentTimeMillis() - sessionStartTime) / 1000
        val net = networkEngine.networkProfile.value
        val therm = thermalEngine.thermalState.value
        val existing = boosterDao.getSessionById(currentSessionId)

        if (existing != null) {
            val verdict = when {
                net.samplesCount < 5 -> "INSUFFICIENT_DATA"
                net.avgRttMs != null && baselineRtt != null && net.avgRttMs < (baselineRtt!! - 4) -> "MEASURED_IMPROVEMENT"
                net.avgRttMs != null && baselineRtt != null && net.avgRttMs > (baselineRtt!! + 8) -> "ELEVATED_LATENCY"
                else -> "MEASURED_STABLE"
            }
            boosterDao.updateSession(
                existing.copy(
                    endTime = System.currentTimeMillis(),
                    durationSeconds = durationSec,
                    avgRttMs = net.avgRttMs,
                    maxRttMs = net.maxRttMs,
                    avgJitterMs = net.jitterMs,
                    peakTempC = therm.batteryTempCelsius?.toInt(),
                    thermalThrottlingDetected = therm.isThrottlingLikely,
                    rollbackSuccess = rollbackReport.isFullyRestored,
                    summaryVerdict = verdict
                )
            )
            boosterDao.insertEvent(
                DiagnosticEventRecord(
                    sessionId = currentSessionId,
                    eventType = "ROLLBACK",
                    title = "Session Terminated",
                    description = "Restored ${rollbackReport.restoredCount} of ${rollbackReport.totalSnapshots} original system settings. " +
                            if (rollbackReport.failedCommands.isNotEmpty()) "Failed: ${rollbackReport.failedCommands.joinToString()}" else "All baseline settings restored.",
                    severity = if (rollbackReport.isFullyRestored) "INFO" else "WARNING"
                )
            )
        }

        prefsManager.setSessionActiveState(false, 0L)
        frameMonitor.stop()
        thermalEngine.stopMonitoring()
        networkEngine.stop()

        _activeReport.value = null
        _sessionState.value = GameSessionState.COMPLETED
        delay(300)
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
}
