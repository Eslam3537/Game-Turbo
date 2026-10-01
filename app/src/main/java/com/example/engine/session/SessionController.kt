package com.example.engine.session

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.data.AppPreferencesManager
import com.example.data.BoosterDatabase
import com.example.data.ExecutionRecorder
import com.example.engine.network.NetworkStabilityEngine
import com.example.engine.performance.FrameTimeMonitor
import com.example.engine.performance.TelemetryScheduler
import com.example.engine.shizuku.RollbackReport
import com.example.engine.shizuku.ShizukuExecutionEngine
import com.example.engine.thermal.ThermalGuardEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Application-level Singleton Session Controller (Fix A8, A9).
 * Survives Activity / ViewModel destruction and recreations.
 * Guarantees that active sessions, system rollbacks, and hardware monitors are never orphaned.
 */
object SessionController {
    private const val TAG = "SessionController"

    private var isInitialized = false

    lateinit var database: BoosterDatabase private set
    lateinit var prefsManager: AppPreferencesManager private set
    lateinit var shizukuEngine: ShizukuExecutionEngine private set
    lateinit var frameMonitor: FrameTimeMonitor private set
    lateinit var thermalEngine: ThermalGuardEngine private set
    lateinit var networkEngine: NetworkStabilityEngine private set
    lateinit var sessionManager: GameSessionManager private set
    lateinit var telemetryScheduler: TelemetryScheduler private set

    fun init(app: Application) {
        if (isInitialized) return
        val context = app.applicationContext

        database = BoosterDatabase.getDatabase(context)
        val dao = database.boosterDao()

        ExecutionRecorder.init(dao)

        prefsManager = AppPreferencesManager(context)
        shizukuEngine = ShizukuExecutionEngine(dao)
        frameMonitor = FrameTimeMonitor()
        thermalEngine = ThermalGuardEngine(context)
        networkEngine = NetworkStabilityEngine(context)
        telemetryScheduler = TelemetryScheduler(context)

        sessionManager = GameSessionManager(
            context = context,
            boosterDao = dao,
            prefsManager = prefsManager,
            shizukuEngine = shizukuEngine,
            frameMonitor = frameMonitor,
            thermalEngine = thermalEngine,
            networkEngine = networkEngine
        )

        isInitialized = true
        Log.i(TAG, "SessionController initialized successfully")
    }

    /**
     * Complete and verified system defaults reset (Fix A8).
     * If a session is active, ends it (awaited).
     * Otherwise executes verified rollback of all stored snapshots.
     * Always restores DND filter and cleans persisted state flags.
     */
    suspend fun resetSystemDefaults(gamePkg: String = "com.tencent.ig"): RollbackReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing complete system defaults reset")
        val report = if (sessionManager.sessionState.value == GameSessionState.RUNNING ||
            sessionManager.sessionState.value == GameSessionState.SAFE_MODE_REVERTED) {
            sessionManager.endSession()
        } else {
            shizukuEngine.rollbackAll(gamePkg)
        }

        prefsManager.setSessionActiveState(false, 0L)
        prefsManager.setPreviousDndFilter(-1)

        report
    }
}
