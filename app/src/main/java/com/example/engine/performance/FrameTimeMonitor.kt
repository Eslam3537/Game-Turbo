package com.example.engine.performance

import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class FrameSpikeEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val frameTimeMs: Double,
    val baselineMs: Double,
    val severity: String // MILD, MODERATE, SEVERE
)

data class FrameTimingStats(
    val appUiFps: Int? = null,
    val averageFrameTimeMs: Double? = null,
    val frameTimeVarianceMs: Double? = null,
    val frameSpikesCount: Int = 0,
    val stabilityScorePercent: Int? = null,
    val recentSpikes: List<FrameSpikeEvent> = emptyList()
)

/**
 * Measures genuine UI frame rendering rate and variance using Choreographer.
 * Accurately identified as "App UI FPS".
 */
class FrameTimeMonitor {
    private var lastFrameTimeNanos: Long = 0L
    private val frameDurationsMs = ArrayDeque<Double>(60)
    private val spikeEvents = mutableListOf<FrameSpikeEvent>()

    private val _timingStats = MutableStateFlow(FrameTimingStats())
    val timingStats: StateFlow<FrameTimingStats> = _timingStats.asStateFlow()

    private var isTracking = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isTracking) return
            if (lastFrameTimeNanos != 0L) {
                val durationMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000.0
                if (durationMs in 3.0..100.0) {
                    processFrame(durationMs)
                }
            }
            lastFrameTimeNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (isTracking) return
        isTracking = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        isTracking = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun processFrame(durationMs: Double) {
        if (frameDurationsMs.size >= 60) {
            frameDurationsMs.removeFirst()
        }
        frameDurationsMs.addLast(durationMs)

        val avg = frameDurationsMs.average()
        val fps = if (avg > 0) (1000.0 / avg).toInt().coerceIn(15, 144) else null

        val sumSq = frameDurationsMs.sumOf { (it - avg) * (it - avg) }
        val stdDev = sqrt(sumSq / frameDurationsMs.size.coerceAtLeast(1))

        if (durationMs > avg * 1.8 && durationMs > 25.0) {
            val severity = when {
                durationMs > 50.0 -> "SEVERE"
                durationMs > 35.0 -> "MODERATE"
                else -> "MILD"
            }
            val spike = FrameSpikeEvent(
                frameTimeMs = durationMs,
                baselineMs = avg,
                severity = severity
            )
            spikeEvents.add(spike)
            if (spikeEvents.size > 20) spikeEvents.removeAt(0)
        }

        val stability = (100 - (stdDev * 8).toInt() - (spikeEvents.size * 2)).coerceIn(50, 100)

        _timingStats.value = FrameTimingStats(
            appUiFps = fps,
            averageFrameTimeMs = String.format("%.1f", avg).toDoubleOrNull() ?: avg,
            frameTimeVarianceMs = String.format("%.2f", stdDev).toDoubleOrNull() ?: stdDev,
            frameSpikesCount = spikeEvents.size,
            stabilityScorePercent = stability,
            recentSpikes = spikeEvents.takeLast(5)
        )
    }

    fun reset() {
        frameDurationsMs.clear()
        spikeEvents.clear()
        _timingStats.value = FrameTimingStats()
    }
}
