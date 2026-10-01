package com.example.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.engine.capability.DeviceCapabilities
import com.example.engine.capability.DeviceCapabilityEngine
import com.example.engine.detection.DeviceHealthScanEngine
import com.example.engine.detection.DeviceHealthScanReport
import com.example.engine.detection.ForegroundGameWatcher
import com.example.engine.diagnostics.DiagnosticsSummary
import com.example.engine.diagnostics.FeatureTestResult
import com.example.engine.diagnostics.OptimizationStatusEngine
import com.example.engine.diagnostics.TestStatus
import com.example.engine.network.DnsBenchmarkResult
import com.example.engine.network.NetworkStabilityEngine
import com.example.engine.overlay.FloatingMonitorService
import com.example.engine.performance.CpuUsageSampler
import com.example.engine.performance.FrameTimeMonitor
import com.example.engine.performance.GameFpsSampler
import com.example.engine.session.GameSessionManager
import com.example.engine.session.GameSessionState
import com.example.engine.session.SessionActiveReport
import com.example.engine.shizuku.ShizukuExecutionEngine
import com.example.engine.thermal.ThermalGuardEngine
import com.example.engine.verification.FeatureVerificationEngine
import com.example.util.PermissionManager
import com.example.util.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

data class WarningDialogData(
    val title: String,
    val description: String,
    val riskLevel: String,
    val onConfirm: () -> Unit
)

class BoosterViewModel(
    application: Application,
    private val repository: BoosterRepository
) : AndroidViewModel(application) {

    private val database = BoosterDatabase.getDatabase(application)
    private val boosterDao = database.boosterDao()
    val prefsManager = AppPreferencesManager(application)
    val verificationEngine = FeatureVerificationEngine(application, prefsManager)
    val shizukuEngine = ShizukuExecutionEngine(boosterDao)
    val frameMonitor = FrameTimeMonitor()
    val thermalEngine = ThermalGuardEngine(application)
    val networkEngine = NetworkStabilityEngine(application)
    val statusEngine = OptimizationStatusEngine(application, boosterDao, shizukuEngine, networkEngine)

    private val _featureTestResults = MutableStateFlow<List<FeatureTestResult>>(statusEngine.getInitialFeatureList())
    val featureTestResults: StateFlow<List<FeatureTestResult>> = _featureTestResults.asStateFlow()

    private val _isTestingAll = MutableStateFlow(false)
    val isTestingAll: StateFlow<Boolean> = _isTestingAll.asStateFlow()

    private val _diagnosticsFilter = MutableStateFlow("all")
    val diagnosticsFilter: StateFlow<String> = _diagnosticsFilter.asStateFlow()

    val sessionManager = GameSessionManager(
        context = application,
        boosterDao = boosterDao,
        prefsManager = prefsManager,
        shizukuEngine = shizukuEngine,
        frameMonitor = frameMonitor,
        thermalEngine = thermalEngine,
        networkEngine = networkEngine
    )

    // Foreground Game Auto-Detector
    private val gameWatcher = ForegroundGameWatcher(
        context = application,
        onGameForeground = { game ->
            if (!_isOptimized.value && !_isOptimizing.value) {
                viewModelScope.launch {
                    logOperation("Auto Detection", "Game in foreground: ${game.name}", "SUCCESS", "Starting Game Turbo profile")
                    triggerMainBoostInternal(getApplication(), game.name, game.packageName)
                }
            }
        },
        onGameBackground = { pkg ->
            if (_isOptimized.value && !_isOptimizing.value) {
                viewModelScope.launch {
                    logOperation("Auto Detection", "Game exited foreground: $pkg", "SUCCESS", "Reverting session baseline")
                    triggerMainBoostInternal(getApplication(), "", pkg)
                }
            }
        }
    )

    // State flows
    val verificationItems = verificationEngine.verificationItems
    val reconciliationReport = verificationEngine.reconciliationReport

    private val _capabilities = MutableStateFlow(DeviceCapabilityEngine.scan(application))
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    private val _healthScanReport = MutableStateFlow<DeviceHealthScanReport?>(null)
    val healthScanReport: StateFlow<DeviceHealthScanReport?> = _healthScanReport.asStateFlow()

    val activeSessionReport: StateFlow<SessionActiveReport?> = sessionManager.activeReport
    val sessionState: StateFlow<GameSessionState> = sessionManager.sessionState
    val allSessions: StateFlow<List<GameSessionRecord>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDiagnosticEvents: StateFlow<List<DiagnosticEventRecord>> = boosterDao.getRecentEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow("home")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _isOptimized = MutableStateFlow(prefsManager.isSessionActive.value)
    val isOptimized: StateFlow<Boolean> = _isOptimized.asStateFlow()

    private val _isNetworkOptimized = MutableStateFlow(false)
    val isNetworkOptimized: StateFlow<Boolean> = _isNetworkOptimized.asStateFlow()

    private val _shizukuActive = MutableStateFlow(AdbCommandRunner.isAvailable())
    val shizukuActive: StateFlow<Boolean> = _shizukuActive.asStateFlow()

    // Persistent preferences
    val selectedLanguage: StateFlow<String> = prefsManager.language
    val selectedDns: StateFlow<String> = prefsManager.selectedDns
    val autoDetectEnabled: StateFlow<Boolean> = prefsManager.autoDetectEnabled
    val overlayEnabled: StateFlow<Boolean> = prefsManager.overlayEnabledPref
    val themeMode: StateFlow<String> = prefsManager.themeMode
    val selectedProfile: StateFlow<String> = prefsManager.selectedProfile
    val selectedGamePackage: StateFlow<String> = prefsManager.selectedGamePkg
    val safeMode: StateFlow<Boolean> = prefsManager.safeModeEnabled
    val dndEnabled: StateFlow<Boolean> = prefsManager.dndEnabled

    private val _permissionsState = MutableStateFlow(PermissionManager.checkStatus(application))
    val permissionsState: StateFlow<PermissionStatus> = _permissionsState.asStateFlow()

    private val _showPermissionCenter = MutableStateFlow(false)
    val showPermissionCenter: StateFlow<Boolean> = _showPermissionCenter.asStateFlow()

    private val _warningDialogData = MutableStateFlow<WarningDialogData?>(null)
    val warningDialogData: StateFlow<WarningDialogData?> = _warningDialogData.asStateFlow()

    // Real-Time Telemetry Metrics (Nullable when unavailable - NO FAKE NUMBERS)
    private val _ramUsage = MutableStateFlow<Int?>(null)
    val ramUsage: StateFlow<Int?> = _ramUsage.asStateFlow()

    private val _gameFps = MutableStateFlow<Int?>(null)
    val gameFps: StateFlow<Int?> = _gameFps.asStateFlow()

    private val _appUiFps = MutableStateFlow<Int?>(null)
    val appUiFps: StateFlow<Int?> = _appUiFps.asStateFlow()

    private val _temperature = MutableStateFlow<Double?>(null)
    val temperature: StateFlow<Double?> = _temperature.asStateFlow()

    private val _cpuLoad = MutableStateFlow<Int?>(null)
    val cpuLoad: StateFlow<Int?> = _cpuLoad.asStateFlow()

    private val _currentPing = MutableStateFlow<Int?>(null)
    val currentPing: StateFlow<Int?> = _currentPing.asStateFlow()

    // Regional Pings (Genuine Socket RTT - null on timeout/loss)
    private val _pingMiddleEast = MutableStateFlow<Int?>(null)
    val pingMiddleEast: StateFlow<Int?> = _pingMiddleEast.asStateFlow()

    private val _pingEurope = MutableStateFlow<Int?>(null)
    val pingEurope: StateFlow<Int?> = _pingEurope.asStateFlow()

    private val _pingAsia = MutableStateFlow<Int?>(null)
    val pingAsia: StateFlow<Int?> = _pingAsia.asStateFlow()

    private val _pingGlobal = MutableStateFlow<Int?>(null)
    val pingGlobal: StateFlow<Int?> = _pingGlobal.asStateFlow()

    private val _jitterMe = MutableStateFlow<Int?>(null)
    val jitterMe: StateFlow<Int?> = _jitterMe.asStateFlow()

    private val _jitterEu = MutableStateFlow<Int?>(null)
    val jitterEu: StateFlow<Int?> = _jitterEu.asStateFlow()

    private val _jitterAsia = MutableStateFlow<Int?>(null)
    val jitterAsia: StateFlow<Int?> = _jitterAsia.asStateFlow()

    private val _jitterGlobal = MutableStateFlow<Int?>(null)
    val jitterGlobal: StateFlow<Int?> = _jitterGlobal.asStateFlow()

    private val _dnsBenchmarkResults = MutableStateFlow<List<DnsBenchmarkResult>>(emptyList())
    val dnsBenchmarkResults: StateFlow<List<DnsBenchmarkResult>> = _dnsBenchmarkResults.asStateFlow()

    val logsList: StateFlow<List<OptimizationLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gamesList: StateFlow<List<AddedGame>> = repository.allGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val meHistory = mutableListOf<Int>()
    private val euHistory = mutableListOf<Int>()
    private val asiaHistory = mutableListOf<Int>()
    private val globHistory = mutableListOf<Int>()

    init {
        viewModelScope.launch {
            // Check for in-place app update and log real transition
            val prevVersion = prefsManager.checkAndRecordAppUpdate(com.example.BuildConfig.VERSION_CODE)
            if (prevVersion != null) {
                logOperation(
                    name = "App Update",
                    text = "System Package Upgrade",
                    stats = "SUCCESS",
                    msg = "App updated from version $prevVersion to ${com.example.BuildConfig.VERSION_CODE}"
                )
                boosterDao.insertEvent(
                    DiagnosticEventRecord(
                        sessionId = 0L,
                        eventType = "UPDATE",
                        title = "App Updated",
                        description = "Successfully upgraded in-place from version $prevVersion to ${com.example.BuildConfig.VERSION_CODE}. User settings, database and logs preserved.",
                        severity = "INFO"
                    )
                )
            }

            // Check for interrupted session recovery on startup
            val recoveryReport = sessionManager.checkForInterruptedSession()
            if (recoveryReport != null) {
                _isOptimized.value = false
            }

            verificationEngine.reconcileStartupState()
            prepopulateDefaultGames()
            refreshPermissions()

            // If overlay was enabled and permission granted, ensure service is running
            if (prefsManager.overlayEnabledPref.value && PermissionManager.hasOverlayPermission(getApplication())) {
                FloatingMonitorService.start(getApplication())
            }

            // Start auto-detect if enabled
            if (prefsManager.autoDetectEnabled.value) {
                gameWatcher.startWatching(viewModelScope) { gamesList.value }
            }
        }

        // Start real hardware telemetry pollers
        startRealHardwareTelemetry()
        startRealRegionalPingTracker()
        frameMonitor.start()
        thermalEngine.startMonitoring()
    }

    fun reconcileNow() {
        viewModelScope.launch {
            verificationEngine.reconcileStartupState()
        }
    }

    fun setDiagnosticsFilter(filter: String) {
        _diagnosticsFilter.value = filter
    }

    fun runAllFeatureDiagnostics() {
        if (_isTestingAll.value) return
        viewModelScope.launch {
            _isTestingAll.value = true
            val initial = statusEngine.getInitialFeatureList()
            val currentList = initial.toMutableList()
            _featureTestResults.value = currentList.toList()

            for (i in currentList.indices) {
                val item = currentList[i]
                currentList[i] = item.copy(isTesting = true)
                _featureTestResults.value = currentList.toList()

                val tested = statusEngine.testSingleFeature(item.id)
                currentList[i] = tested.copy(isTesting = false)
                _featureTestResults.value = currentList.toList()
            }
            _isTestingAll.value = false
        }
    }

    fun retryFeatureDiagnostic(featureId: String) {
        viewModelScope.launch {
            val currentList = _featureTestResults.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == featureId }
            if (index != -1) {
                currentList[index] = currentList[index].copy(isTesting = true)
                _featureTestResults.value = currentList.toList()

                val tested = statusEngine.testSingleFeature(featureId)
                currentList[index] = tested.copy(isTesting = false)
                _featureTestResults.value = currentList.toList()
            }
        }
    }

    fun getFormattedExportReport(): String = statusEngine.generateExportReport(_featureTestResults.value)

    fun getSingleErrorReport(item: FeatureTestResult): String = statusEngine.generateSingleErrorReport(item)

    fun getDiagnosticsSummary(): DiagnosticsSummary = statusEngine.computeSummary(_featureTestResults.value)

    fun runDeviceHealthScan() {
        _healthScanReport.value = DeviceHealthScanEngine.executeScan(getApplication())
    }

    fun dismissHealthScan() {
        _healthScanReport.value = null
    }

    fun setScreen(screen: String) {
        _currentScreen.value = screen
    }

    fun setThemeMode(mode: String) {
        prefsManager.setThemeMode(mode)
    }

    fun setLanguage(lang: String) {
        prefsManager.setLanguage(lang)
    }

    fun setProfile(profile: String) {
        prefsManager.setSelectedProfile(profile)
    }

    fun selectGame(pkg: String) {
        prefsManager.setSelectedGame(pkg)
    }

    fun setSafeMode(enabled: Boolean) {
        prefsManager.setSafeMode(enabled)
    }

    fun setDndEnabled(enabled: Boolean) {
        prefsManager.setDndEnabled(enabled)
    }

    fun setDns(dnsHost: String) {
        prefsManager.setSelectedDns(dnsHost)
    }

    fun setAutoDetect(enabled: Boolean) {
        prefsManager.setAutoDetect(enabled)
        if (enabled) {
            gameWatcher.startWatching(viewModelScope) { gamesList.value }
        } else {
            gameWatcher.stopWatching()
        }
    }

    fun setOverlay(enabled: Boolean, context: Context) {
        if (enabled) {
            if (!PermissionManager.hasOverlayPermission(context)) {
                Toast.makeText(context, "Overlay permission required", Toast.LENGTH_SHORT).show()
                PermissionManager.openOverlaySettings(context)
                return
            }
            prefsManager.setOverlayEnabledPref(true)
            FloatingMonitorService.start(context)
        } else {
            prefsManager.setOverlayEnabledPref(false)
            FloatingMonitorService.stop(context)
        }
    }

    fun showWarning(title: String, description: String, riskLevel: String, onConfirm: () -> Unit) {
        _warningDialogData.value = WarningDialogData(title, description, riskLevel, onConfirm)
    }

    fun dismissWarning() {
        _warningDialogData.value = null
    }

    fun setPermissionCenterVisible(visible: Boolean) {
        _showPermissionCenter.value = visible
    }

    fun refreshPermissions() {
        val status = PermissionManager.checkStatus(getApplication())
        _permissionsState.value = status
        _shizukuActive.value = status.hasShizuku
    }

    fun addGame(name: String, packageName: String) = viewModelScope.launch {
        val game = AddedGame(name = name, packageName = packageName, isCustom = true)
        repository.insertGame(game)
    }

    fun deleteGame(game: AddedGame) = viewModelScope.launch {
        repository.deleteGame(game.id)
    }

    fun launchGame(context: Context, packageName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "Game not installed ($packageName)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            Toast.makeText(context, "Launch error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Executes Main Game Turbo Boost or ends active session.
     * Enforces strict real verification, zero simulation, and awaited rollback.
     */
    fun triggerMainBoost(context: Context) {
        viewModelScope.launch {
            val games = gamesList.value
            val activeGame = games.find { it.packageName == selectedGamePackage.value } ?: games.firstOrNull()
            val gName = activeGame?.name ?: "Game"
            val gPkg = activeGame?.packageName ?: selectedGamePackage.value
            triggerMainBoostInternal(context, gName, gPkg)
        }
    }

    private suspend fun triggerMainBoostInternal(context: Context, gameName: String, gamePackage: String) {
        if (_isOptimizing.value) return

        if (_isOptimized.value) {
            // End active session with complete verified rollback
            _isOptimizing.value = true
            val report = sessionManager.endSession()
            _isOptimized.value = false
            _isOptimizing.value = false

            if (report.isFullyRestored) {
                Toast.makeText(context, "Restored all ${report.restoredCount} baseline settings", Toast.LENGTH_SHORT).show()
                logOperation("Session End", "Rollback complete", "SUCCESS", "Restored ${report.restoredCount} settings")
            } else {
                val failed = report.failedCommands.joinToString()
                Toast.makeText(context, "Restored ${report.restoredCount}/${report.totalSnapshots}. Failed: $failed", Toast.LENGTH_LONG).show()
                logOperation("Session End", "Partial rollback", "FAILED", "Failed to restore: $failed")
            }
        } else {
            // Start verified Game Turbo Session
            if (!AdbCommandRunner.isAvailable()) {
                Toast.makeText(context, "Shizuku service required to apply optimizations", Toast.LENGTH_LONG).show()
                logOperation("Session Start", "Shizuku offline", "FAILED", "Cannot apply tweaks without Shizuku")
                _showPermissionCenter.value = true
                return
            }

            _isOptimizing.value = true
            val result = sessionManager.startSession(gameName, gamePackage, selectedProfile.value)
            _isOptimizing.value = false

            if (result.success) {
                _isOptimized.value = true
                Toast.makeText(context, "Applied ${result.appliedCount}, verified ${result.verifiedCount} settings", Toast.LENGTH_SHORT).show()
                logOperation("Session Start", "Profile: ${selectedProfile.value}", "SUCCESS", "Verified ${result.verifiedCount}/${result.appliedCount}")
            } else {
                _isOptimized.value = false
                Toast.makeText(context, "Optimization failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                logOperation("Session Start", "Profile: ${selectedProfile.value}", "FAILED", result.errorMessage ?: "Verification failed")
            }
        }
    }

    /**
     * Executes genuine DNS benchmark against candidate servers and selects the measured fastest.
     */
    fun triggerNetworkOptimization(context: Context) = viewModelScope.launch {
        if (_isOptimizing.value) return@launch
        _isOptimizing.value = true

        val results = networkEngine.benchmarkDnsCandidates()
        _dnsBenchmarkResults.value = results

        val fastest = results.filter { it.latencyMs != null }.minByOrNull { it.latencyMs!! }
        if (fastest != null) {
            prefsManager.setSelectedDns(fastest.host)
            logOperation("DNS Benchmark", "Tested ${results.size} providers", "SUCCESS", "Fastest: ${fastest.providerName} (${fastest.latencyMs} ms)")
            _isNetworkOptimized.value = true
            Toast.makeText(context, "Fastest DNS: ${fastest.providerName} (${fastest.latencyMs}ms)", Toast.LENGTH_SHORT).show()
        } else {
            logOperation("DNS Benchmark", "DNS probe", "FAILED", "Could not reach DNS servers")
            Toast.makeText(context, "DNS benchmark failed - network unreachable", Toast.LENGTH_SHORT).show()
        }
        _isOptimizing.value = false
    }

    /**
     * Reverts all modified settings back to system defaults.
     */
    fun resetSystemDefaults(context: Context) = viewModelScope.launch {
        if (_isOptimizing.value) return@launch
        _isOptimizing.value = true

        val report = shizukuEngine.rollbackAll(selectedGamePackage.value)
        _isOptimized.value = false
        _isNetworkOptimized.value = false
        _isOptimizing.value = false

        if (report.isFullyRestored) {
            Toast.makeText(context, "Restored all ${report.restoredCount} baseline settings", Toast.LENGTH_SHORT).show()
            logOperation("Restore Defaults", "Rollback", "SUCCESS", "Restored ${report.restoredCount} settings")
        } else {
            Toast.makeText(context, "Failed restoring: ${report.failedCommands.joinToString()}", Toast.LENGTH_LONG).show()
            logOperation("Restore Defaults", "Rollback", "FAILED", "Failed commands: ${report.failedCommands.joinToString()}")
        }
    }

    fun clearLogs() = viewModelScope.launch {
        repository.clearLogs()
    }

    private suspend fun logOperation(name: String, text: String, stats: String, msg: String) {
        val log = OptimizationLog(
            commandName = name,
            commandText = text,
            status = stats,
            responseMsg = msg
        )
        repository.insertLog(log)
    }

    private fun prepopulateDefaultGames() = viewModelScope.launch {
        repository.allGames.first().let { current ->
            if (current.isEmpty()) {
                val pubgGlobal = AddedGame(name = "PUBG Mobile (Global)", packageName = "com.tencent.ig")
                val pubgBgmi = AddedGame(name = "PUBG BGMI (India)", packageName = "com.pubg.imobile")
                val pubgKorea = AddedGame(name = "PUBG Mobile (KR)", packageName = "com.pubg.krmobile")
                val pubgNewState = AddedGame(name = "New State Mobile", packageName = "com.pubg.newstate")
                repository.insertGame(pubgGlobal)
                repository.insertGame(pubgBgmi)
                repository.insertGame(pubgKorea)
                repository.insertGame(pubgNewState)
            }
        }
    }

    /**
     * Real Hardware Telemetry Polling Loop (Zero Fake/Random Numbers).
     */
    private fun startRealHardwareTelemetry() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            while (true) {
                // 1. Genuine physical RAM usage
                _ramUsage.value = getRealRamUsage(app)

                // 2. Genuine battery temperature
                thermalEngine.refresh()
                _temperature.value = thermalEngine.thermalState.value.batteryTempCelsius

                // 3. Genuine CPU load via /proc/stat if Shizuku available
                _cpuLoad.value = CpuUsageSampler.sampleCpuUsage()

                // 4. Genuine Game FPS from SurfaceFlinger
                _gameFps.value = GameFpsSampler.sampleGameFps(app, selectedGamePackage.value)

                // 5. Genuine App UI FPS from Choreographer
                _appUiFps.value = frameMonitor.timingStats.value.appUiFps

                delay(2000)
            }
        }
    }

    private fun getRealRamUsage(context: Context): Int? {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            val totalMem = memoryInfo.totalMem.toDouble()
            val availMem = memoryInfo.availMem.toDouble()
            if (totalMem > 0) {
                (((totalMem - availMem) / totalMem) * 100).toInt()
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Real Regional Socket Ping Tracker (Zero fake multipliers or random jitter).
     */
    private fun startRealRegionalPingTracker() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val me = measureRealTcpPing("dynamodb.me-south-1.amazonaws.com")
                val eu = measureRealTcpPing("dynamodb.eu-central-1.amazonaws.com")
                val asia = measureRealTcpPing("dynamodb.ap-southeast-1.amazonaws.com")
                val glob = measureRealTcpPing("dynamodb.us-east-1.amazonaws.com")

                _pingMiddleEast.value = me
                _pingEurope.value = eu
                _pingAsia.value = asia
                _pingGlobal.value = glob
                _currentPing.value = me

                if (me != null) {
                    meHistory.add(me)
                    if (meHistory.size > 20) meHistory.removeAt(0)
                    _jitterMe.value = computeRealJitter(meHistory)
                }

                if (eu != null) {
                    euHistory.add(eu)
                    if (euHistory.size > 20) euHistory.removeAt(0)
                    _jitterEu.value = computeRealJitter(euHistory)
                }

                if (asia != null) {
                    asiaHistory.add(asia)
                    if (asiaHistory.size > 20) asiaHistory.removeAt(0)
                    _jitterAsia.value = computeRealJitter(asiaHistory)
                }

                if (glob != null) {
                    globHistory.add(glob)
                    if (globHistory.size > 20) globHistory.removeAt(0)
                    _jitterGlobal.value = computeRealJitter(globHistory)
                }

                delay(3000)
            }
        }
    }

    private fun computeRealJitter(history: List<Int>): Int? {
        if (history.size < 2) return null
        val diffs = history.zipWithNext { a, b -> abs(b - a) }
        return diffs.average().toInt()
    }

    private fun measureRealTcpPing(host: String): Int? {
        val start = System.currentTimeMillis()
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, 443), 1500)
            (System.currentTimeMillis() - start).toInt()
        } catch (_: Throwable) {
            null // Real connection failure / packet loss
        } finally {
            try { socket?.close() } catch (_: Throwable) {}
        }
    }
}

class BoosterViewModelFactory(
    private val application: Application,
    private val repository: BoosterRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BoosterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BoosterViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
