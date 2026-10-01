package com.example.engine.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.example.engine.performance.ThermalSample
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class RealMonitorMetrics(
    val fps: Int? = null,
    val thermalSample: ThermalSample? = null,
    val isShizukuReady: Boolean = false,
    val errorMessage: String? = null,
    val isUpdating: Boolean = false
)

/**
 * Floating FPS and Temperature Monitor Service (Fix A2, A14).
 * - 2-second sampling interval (Fix A2).
 * - Stops sampling when screen is off (Fix A14).
 * - Displays explicit thermal sensor label: "CPU 52°C" or "Battery 38°C".
 * - Allows stale FPS for at most 3 seconds, then shows "FPS —" (N/A).
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
    private var isScreenOn = true
    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefsManager = AppPreferencesManager(applicationContext)
        startForegroundNotification()
        registerScreenStateReceiver()

        if (Settings.canDrawOverlays(this)) {
            initFloatingOverlay()
            startPeriodicSampling()
            _isOverlayRunning.value = true
        } else {
            Log.w(TAG, "Cannot draw overlays, permission denied")
            stopSelf()
        }
    }

    private fun registerScreenStateReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        Log.d(TAG, "Screen off: pausing overlay sampling")
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        Log.d(TAG, "Screen on: resuming overlay sampling")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun startForegroundNotification() {
        val channelId = "game_turbo_fps_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "HUD Floating Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live FPS and thermal status in a compact floating overlay"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Game Turbo: شاشة المراقبة العائمة")
            .setContentText("مراقبة معدل الإطارات والحرارة في الوقت الفعلي")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun initFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val savedX = prefsManager.overlayPosX.value
        val savedY = prefsManager.overlayPosY.value

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

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

        val contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvFps = TextView(this).apply {
            text = "FPS —"
            setTextColor(0xFF30D158.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(8f), 0)
        }

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(1f), dp(14f)).apply {
                setMargins(0, 0, dp(8f), 0)
            }
            setBackgroundColor(0x3394A3B8.toInt())
        }

        tvTemp = TextView(this).apply {
            text = "🌡 —°C"
            setTextColor(0xFF30D158.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(8f), 0)
        }

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
     * Starts continuous sampling loop running once every 2000ms (Fix A2).
     */
    private fun startPeriodicSampling() {
        samplingJob?.cancel()
        samplingJob = serviceScope.launch {
            while (isActive) {
                if (isScreenOn && isSampling.compareAndSet(false, true)) {
                    try {
                        val metrics = sampleLiveMetrics()
                        updateUi(metrics)
                        _liveMetrics.value = metrics
                    } catch (e: Throwable) {
                        Log.w(TAG, "Error in overlay sampling loop: ${e.message}")
                    } finally {
                        isSampling.set(false)
                    }
                }
                delay(2000) // 2-second interval (Fix A2)
            }
        }
    }

    private suspend fun sampleLiveMetrics(): RealMonitorMetrics = withContext(Dispatchers.IO) {
        val shizukuReady = AdbCommandRunner.isAvailable()

        // 1. Thermal sample with sensor priority (Skin -> CPU -> Battery)
        val thermal = HardwareThermalSampler.sampleTemperature(applicationContext)

        if (!shizukuReady) {
            return@withContext RealMonitorMetrics(
                fps = null,
                thermalSample = thermal,
                isShizukuReady = false,
                errorMessage = "يجب تفعيل Shizuku للحصول على بيانات الأداء الحقيقية"
            )
        }

        // 2. Sample Game FPS via unified SurfaceFlinger engine
        val configuredGame = prefsManager.selectedGamePkg.value
        val measuredFps = SurfaceFlingerFpsEngine.sampleFps(applicationContext, configuredGame)

        RealMonitorMetrics(
            fps = measuredFps,
            thermalSample = thermal,
            isShizukuReady = true,
            errorMessage = null,
            isUpdating = false
        )
    }

    private fun updateUi(metrics: RealMonitorMetrics) {
        if (!metrics.isShizukuReady) {
            tvStatusWarning?.text = "يجب تفعيل Shizuku للحصول على بيانات الأداء الحقيقية"
            tvStatusWarning?.visibility = View.VISIBLE
            tvFps?.text = "FPS —"
            tvFps?.setTextColor(0xFF38BDF8.toInt())
        } else {
            tvStatusWarning?.visibility = View.GONE

            val fps = metrics.fps
            if (fps != null) {
                tvFps?.text = "FPS $fps"
                when {
                    fps >= 55 -> tvFps?.setTextColor(0xFF30D158.toInt()) // Green
                    fps >= 30 -> tvFps?.setTextColor(0xFFFFD60A.toInt()) // Yellow
                    else -> tvFps?.setTextColor(0xFFFF453A.toInt())       // Red
                }
            } else {
                tvFps?.text = "FPS —"
                tvFps?.setTextColor(0xFF94A3B8.toInt())
            }
        }

        // Thermal Formatting with explicit sensor name (Fix A14)
        val thermal = metrics.thermalSample
        if (thermal != null) {
            val tempC = thermal.temperatureCelsius.toInt()
            val label = thermal.sensorName
            tvTemp?.text = "🌡 $label $tempC°C"
            when {
                tempC < 38 -> tvTemp?.setTextColor(0xFF30D158.toInt()) // Green
                tempC <= 42 -> tvTemp?.setTextColor(0xFFFFD60A.toInt()) // Yellow
                else -> tvTemp?.setTextColor(0xFFFF453A.toInt())        // Red
            }
        } else {
            tvTemp?.text = "🌡 —°C"
            tvTemp?.setTextColor(0xFF94A3B8.toInt())
        }
    }

    private fun dp(value: Float): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        samplingJob?.cancel()
        serviceScope.launch {
            SurfaceFlingerFpsEngine.resetTimeStats()
        }
        serviceScope.cancel()

        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Throwable) {}
        }
        screenReceiver = null

        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Throwable) {}
        }
        overlayView = null
        _isOverlayRunning.value = false
    }

    companion object {
        private const val TAG = "FloatingMonitorService"

        private val _isOverlayRunning = MutableStateFlow(false)
        val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

        private val _liveMetrics = MutableStateFlow(RealMonitorMetrics())
        val liveMetrics: StateFlow<RealMonitorMetrics> = _liveMetrics.asStateFlow()

        fun start(context: Context) {
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
        }
    }
}
