package com.example.engine.detection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AddedGame
import com.example.engine.session.GameSessionState
import com.example.engine.session.SessionController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Foreground Service for Persistent Foreground Game Detection (Fix A15).
 * Listens in background with low-priority notification, starting sessions via SessionController.
 */
class AutoDetectGameService : Service() {
    private val TAG = "AutoDetectGameService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var watcher: ForegroundGameWatcher? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startNotification()

        watcher = ForegroundGameWatcher(
            context = applicationContext,
            onGameForeground = { game ->
                serviceScope.launch {
                    val currentState = SessionController.sessionManager.sessionState.value
                    if (currentState != GameSessionState.RUNNING && currentState != GameSessionState.OPTIMIZING) {
                        Log.i(TAG, "Auto-starting session for ${game.name} (${game.packageName})")
                        val profile = SessionController.prefsManager.selectedProfile.value
                        SessionController.sessionManager.startSession(game.name, game.packageName, profile)
                    }
                }
            },
            onGameBackground = { pkg ->
                serviceScope.launch {
                    val currentState = SessionController.sessionManager.sessionState.value
                    if (currentState == GameSessionState.RUNNING || currentState == GameSessionState.SAFE_MODE_REVERTED) {
                        Log.i(TAG, "Auto-ending session for $pkg after 10s out of foreground")
                        SessionController.sessionManager.endSession()
                    }
                }
            }
        )

        watcher?.startWatching(serviceScope) {
            val games = SessionController.database.boosterDao().getAllGames().first()
            if (games.isNotEmpty()) games else listOf(
                AddedGame(name = "PUBG Mobile", packageName = "com.tencent.ig"),
                AddedGame(name = "BGMI", packageName = "com.pubg.imobile"),
                AddedGame(name = "PUBG KR", packageName = "com.pubg.krmobile"),
                AddedGame(name = "NEW STATE", packageName = "com.pubg.newstate")
            )
        }
    }

    private fun startNotification() {
        val channelId = "game_turbo_auto_detect"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auto Game Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors foreground game launches to trigger Game Turbo profiles"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Game Turbo: الكشف التلقائي")
            .setContentText("مراقبة إطلاق الألعاب في الخلفية لتفعيل ملفات الأداء تلقائياً")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1002, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        watcher?.stopWatching()
        serviceScope.cancel()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AutoDetectGameService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutoDetectGameService::class.java)
            context.stopService(intent)
        }
    }
}
