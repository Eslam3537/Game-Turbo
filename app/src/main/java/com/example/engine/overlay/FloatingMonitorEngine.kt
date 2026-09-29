package com.example.engine.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.data.AdbCommandRunner
import com.example.data.AppPreferencesManager
import com.example.engine.performance.HardwareThermalSampler
import com.example.engine.performance.SurfaceFlingerFpsEngine
import com.example.util.PermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class RealMonitorMetrics(
    val fps: Int? = null,
    val tempCelsius: Double? = null,
    val isShizukuReady: Boolean = false,
    val errorMessage: String? = null,
    val isUpdating: Boolean = false
)

/**
 * Floating FPS and Temperature Monitor Service.
 *
 * Displays a lightweight, non-intrusive floating HUD exclusively displaying:
 * ┌─────────────────┐
 * │ FPS 60   🌡 38°C │
 * └─────────────────┘
 *
 * Features:
 * - Shell UID 2000 execution via Shizuku.
 * - Non-root, no game modification, no code injection.
 * - TYPE_APPLICATION_OVERLAY with FLAG_NOT_FOCUSABLE so PUBG retains 100% focus.
 * - Draggable with position persistence in SharedPreferences.
 * - Real SurfaceFlinger TimeStats & Latency measurement.
 * - Real hardware thermal sensor queries.
 * - Color-coded indicators:
 *   * FPS: Green >= 55, Yellow 30-54, Red < 30
 *   * Temp: Green < 38°C, Yellow 38-42°C, Red > 42°C
 */
class FloatingMonitorService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var samplingJob: Job? = null
    private lateinit var prefsManager: AppPreferencesManager

    private var tvFps: TextView? = null
    private var tvTemp: TextView? = null
    private var tvStatusWarning: TextView? = null
    private var metricsContainer: LinearLayout? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val isSampling = AtomicBoolean(false)
    private var lastValidFps: Int? = null
    private var lastValidTemp: Double? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefsManager = AppPreferencesManager(applicationContext)
        startForegroundNotification()

        if (Settings.canDrawOverlays(this)) {
            initFloatingOverlay()
            startPeriodicSampling()
            _isOverlayRunning.value = true
        } else {
            Log.w(TAG, "Cannot draw overlays, permission denied")
            stopSelf()
        }
    }

    private fun startForegroundNotification() {
        val channelId = "game_turbo_fps_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Floating FPS & Temp Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active floating HUD over gameplay"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating FPS & Temp Monitor")
            .setContentText("Monitoring live SurfaceFlinger FPS & Hardware Temperature")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initFloatingOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // FLAG_NOT_FOCUSABLE is critical so PUBG remains the foreground focused application
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefsManager.overlayPosX.value.coerceAtLeast(30)
            y = prefsManager.overlayPosY.value.coerceAtLeast(80)
        }

        val density = resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()

        // Root container with sleek semi-transparent Apple glass background
        val rootLayout = FrameLayout(this).apply {
            setPadding(0, 0, 0, 0)
        }

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            val glassBg = GradientDrawable().apply {
                setColor(0xE60F172A.toInt()) // Sleek translucent dark slate
                cornerRadius = dp(14f).toFloat()
                setStroke(dp(1f), 0x3394A3B8.toInt())
            }
            background = glassBg
            elevation = dp(6f).toFloat()
        }

        // Main content row containing: FPS [value] | 🌡 [temp] | [× button]
        val contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // 1. FPS display text
        tvFps = TextView(this).apply {
            text = "FPS —"
            setTextColor(0xFF30D158.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(8f), 0)
        }

        // Vertical divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(1f), dp(14f)).apply {
                setMargins(0, 0, dp(8f), 0)
            }
            setBackgroundColor(0x3394A3B8.toInt())
        }

        // 2. Temperature display text
        tvTemp = TextView(this).apply {
            text = "🌡 —°C"
            setTextColor(0xFF30D158.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(8f), 0)
        }

        // 3. Small close button (×)
        val tvClose = TextView(this).apply {
            text = "×"
            setTextColor(0xFF94A3B8.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4f), 0, dp(2f), 0)
            setOnClickListener {
                stop(this@FloatingMonitorService)
            }
        }

        contentRow.addView(tvFps)
        contentRow.addView(divider)
        contentRow.addView(tvTemp)
        contentRow.addView(tvClose)

        metricsContainer = contentRow
        cardLayout.addView(contentRow)

        // 4. Shizuku / Error warning banner (hidden by default)
        tvStatusWarning = TextView(this).apply {
            text = "يجب تفعيل Shizuku للحصول على بيانات الأداء الحقيقية"
            setTextColor(0xFFFF9F0A.toInt())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(4f), 0, 0)
            visibility = View.GONE
        }
        cardLayout.addView(tvStatusWarning)

        // Draggable touch listener with smooth position persistence
        rootLayout.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefsManager.setOverlayPosition(params.x, params.y)
                    true
                }
                else -> false
            }
        }

        rootLayout.addView(cardLayout)
        overlayView = rootLayout
        windowManager?.addView(overlayView, params)
    }

    /**
     * Starts continuous sampling loop running once every 1000ms.
     */
    private fun startPeriodicSampling() {
        samplingJob?.cancel()
        samplingJob = serviceScope.launch {
            // Enable SurfaceFlinger TimeStats
            SurfaceFlingerFpsEngine.enableTimeStats()

            while (isActive) {
                if (isSampling.compareAndSet(false, true)) {
                    try {
                        val metrics = sampleLiveMetrics()
                        updateUi(metrics)
                        _liveMetrics.value = metrics
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error in sample loop: ${e.message}")
                    } finally {
                        isSampling.set(false)
                    }
                }
                delay(1000)
            }
        }
    }

    /**
     * Executes real hardware measurements in background.
     */
    private suspend fun sampleLiveMetrics(): RealMonitorMetrics = withContext(Dispatchers.IO) {
        val shizukuReady = AdbCommandRunner.isAvailable()

        if (!shizukuReady) {
            return@withContext RealMonitorMetrics(
                fps = null,
                tempCelsius = HardwareThermalSampler.sampleTemperature(applicationContext)?.temperatureCelsius,
                isShizukuReady = false,
                errorMessage = "يجب تفعيل Shizuku للحصول على بيانات الأداء الحقيقية"
            )
        }

        // 1. Detect foreground application package
        val fgPackage = SurfaceFlingerFpsEngine.detectForegroundPackage()
        val configuredGame = prefsManager.selectedGamePkg.value
        val targetGame = SurfaceFlingerFpsEngine.resolveTargetGame(configuredGame, fgPackage)

        // 2. Sample Game FPS via SurfaceFlinger TimeStats / Latency
        val measuredFps = SurfaceFlingerFpsEngine.sampleFps(targetGame)
        if (measuredFps != null) {
            lastValidFps = measuredFps
        }

        // 3. Sample Hardware Temperature (Thermal service or battery)
        val thermalSample = HardwareThermalSampler.sampleTemperature(applicationContext)
        val measuredTemp = thermalSample?.temperatureCelsius
        if (measuredTemp != null) {
            lastValidTemp = measuredTemp
        }

        RealMonitorMetrics(
            fps = measuredFps ?: lastValidFps,
            tempCelsius = measuredTemp ?: lastValidTemp,
            isShizukuReady = true,
            errorMessage = null,
            isUpdating = measuredFps == null && lastValidFps != null
        )
    }

    /**
     * Updates the compact floating overlay UI with strict color rules:
     * - FPS: Green >= 55, Yellow 30-54, Red < 30
     * - Temp: Green < 38°C, Yellow 38-42°C, Red > 42°C
     */
    private fun updateUi(metrics: RealMonitorMetrics) {
        if (!metrics.isShizukuReady) {
            tvStatusWarning?.text = "يجب تفعيل Shizuku للحصول على بيانات الأداء الحقيقية"
            tvStatusWarning?.visibility = View.VISIBLE
            tvFps?.text = "FPS: يحتاج صلاحية"
            tvFps?.setTextColor(0xFF38BDF8.toInt()) // Blue warning
        } else {
            tvStatusWarning?.visibility = View.GONE

            // FPS Formatting & Colors
            val fps = metrics.fps
            if (fps != null) {
                val updateDot = if (metrics.isUpdating) "● " else ""
                tvFps?.text = "FPS $updateDot$fps"
                when {
                    fps >= 55 -> tvFps?.setTextColor(0xFF30D158.toInt()) // Green
                    fps >= 30 -> tvFps?.setTextColor(0xFFFFD60A.toInt()) // Yellow
                    else -> tvFps?.setTextColor(0xFFFF453A.toInt())       // Red
                }
            } else {
                tvFps?.text = "FPS: غير متاح"
                tvFps?.setTextColor(0xFF94A3B8.toInt()) // Slate
            }
        }

        // Temperature Formatting & Colors
        val temp = metrics.tempCelsius
        if (temp != null) {
            tvTemp?.text = "🌡 ${temp.toInt()}°C"
            when {
                temp < 38.0 -> tvTemp?.setTextColor(0xFF30D158.toInt())  // Green (< 38°C)
                temp <= 42.0 -> tvTemp?.setTextColor(0xFFFFD60A.toInt()) // Yellow (38-42°C)
                else -> tvTemp?.setTextColor(0xFFFF453A.toInt())         // Red (> 42°C)
            }
        } else {
            tvTemp?.text = "🌡 غير متاحة"
            tvTemp?.setTextColor(0xFF94A3B8.toInt())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        samplingJob?.cancel()
        serviceScope.launch(Dispatchers.IO) {
            SurfaceFlingerFpsEngine.disableTimeStats()
        }
        serviceScope.cancel()

        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Throwable) {
                Log.e(TAG, "Error removing overlayView: ${e.message}")
            }
            overlayView = null
        }
        _isOverlayRunning.value = false
    }

    companion object {
        private const val TAG = "FloatingMonitorService"
        private const val NOTIFICATION_ID = 8801

        private val _isOverlayRunning = MutableStateFlow(false)
        val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

        private val _liveMetrics = MutableStateFlow(RealMonitorMetrics())
        val liveMetrics: StateFlow<RealMonitorMetrics> = _liveMetrics.asStateFlow()

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                PermissionManager.openOverlaySettings(context)
                return
            }
            val intent = Intent(context, FloatingMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingMonitorService::class.java)
            context.stopService(intent)
            _isOverlayRunning.value = false
        }
    }
}
