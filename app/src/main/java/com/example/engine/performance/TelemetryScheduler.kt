package com.example.engine.performance

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveTelemetry(
    val cpuUsagePercent: Int? = null,
    val gameFps: Int? = null,
    val thermalSample: ThermalSample? = null,
    val isGameForeground: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Unified Telemetry Scheduler (Fix A2, A9, A14).
 * Single coordination loop preventing duplicate shell process spawns across Home, HUD, and Sessions.
 * Implements reference counting: actively polls only when at least one screen or service is listening.
 */
class TelemetryScheduler(private val context: Context) {
    private val TAG = "TelemetryScheduler"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _telemetryFlow = MutableStateFlow(LiveTelemetry())
    val telemetryFlow: StateFlow<LiveTelemetry> = _telemetryFlow.asStateFlow()

    private val activeSubscribers = mutableSetOf<String>()
    private var pollerJob: Job? = null
    private var targetGamePackage: String = "com.tencent.ig"

    fun setTargetGamePackage(pkg: String) {
        targetGamePackage = pkg
    }

    @Synchronized
    fun start(subscriberId: String) {
        activeSubscribers.add(subscriberId)
        if (pollerJob == null || !pollerJob!!.isActive) {
            Log.d(TAG, "Starting unified telemetry polling (subscribers: ${activeSubscribers.size})")
            startPollingLoop()
        }
    }

    @Synchronized
    fun stop(subscriberId: String) {
        activeSubscribers.remove(subscriberId)
        if (activeSubscribers.isEmpty()) {
            Log.d(TAG, "Stopping unified telemetry polling (0 subscribers left)")
            pollerJob?.cancel()
            pollerJob = null
            CpuUsageSampler.reset()
            scope.launch {
                SurfaceFlingerFpsEngine.resetTimeStats()
            }
        }
    }

    private fun startPollingLoop() {
        pollerJob?.cancel()
        pollerJob = scope.launch {
            while (isActive) {
                try {
                    // 1. Single-read CPU usage diff (0ms delay)
                    val cpu = CpuUsageSampler.sampleCpuUsage()

                    // 2. Hardware Thermals with 5s internal cache
                    val thermal = HardwareThermalSampler.sampleTemperature(context)

                    // 3. Game FPS (only queried if target game is confirmed foreground)
                    val fps = SurfaceFlingerFpsEngine.sampleFps(context, targetGamePackage)
                    val fg = SurfaceFlingerFpsEngine.getForegroundPackage(context)
                    val isFg = fg == targetGamePackage || (fg != null && SurfaceFlingerFpsEngine.KNOWN_PUBG_PACKAGES.contains(fg))

                    _telemetryFlow.value = LiveTelemetry(
                        cpuUsagePercent = cpu,
                        gameFps = fps,
                        thermalSample = thermal,
                        isGameForeground = isFg,
                        lastUpdated = System.currentTimeMillis()
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Telemetry polling loop notice: ${e.message}")
                }
                delay(2000) // 2-second interval (Fix A2)
            }
        }
    }

    fun release() {
        activeSubscribers.clear()
        pollerJob?.cancel()
        pollerJob = null
        scope.cancel()
    }
}
