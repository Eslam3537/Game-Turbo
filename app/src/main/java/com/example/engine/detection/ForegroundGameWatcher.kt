package com.example.engine.detection

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.example.data.AddedGame
import com.example.util.PermissionManager
import kotlinx.coroutines.*

/**
 * Real-time Foreground Game Detector using Android UsageStatsManager.
 * Detects when a game is launched into the foreground or exited.
 */
class ForegroundGameWatcher(
    private val context: Context,
    private val onGameForeground: (AddedGame) -> Unit,
    private val onGameBackground: (String) -> Unit
) {
    private val TAG = "ForegroundGameWatcher"
    private var watchJob: Job? = null
    private var currentGamePackage: String? = null

    fun startWatching(scope: CoroutineScope, getGamesList: () -> List<AddedGame>) {
        if (watchJob != null) return
        if (!PermissionManager.hasUsageStatsPermission(context)) {
            Log.w(TAG, "Cannot start ForegroundGameWatcher: PACKAGE_USAGE_STATS permission missing")
            return
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return

        watchJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "ForegroundGameWatcher started")
            var lastCheckTime = System.currentTimeMillis() - 4000

            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    val events = usageStatsManager.queryEvents(lastCheckTime, now)
                    lastCheckTime = now

                    val event = UsageEvents.Event()
                    val registeredGames = getGamesList()

                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        val pkg = event.packageName

                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                            val matchedGame = registeredGames.find { it.packageName == pkg }
                            if (matchedGame != null && currentGamePackage != pkg) {
                                currentGamePackage = pkg
                                Log.i(TAG, "Detected game entered foreground: ${matchedGame.name} ($pkg)")
                                withContext(Dispatchers.Main) {
                                    onGameForeground(matchedGame)
                                }
                            }
                        } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                            event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                            if (currentGamePackage == pkg) {
                                // Delay slightly to see if another activity of the same package immediately resumes
                                delay(1500)
                                if (currentGamePackage == pkg) {
                                    currentGamePackage = null
                                    Log.i(TAG, "Game exited foreground: $pkg")
                                    withContext(Dispatchers.Main) {
                                        onGameBackground(pkg)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in ForegroundGameWatcher: ${e.message}")
                }
                delay(2500)
            }
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        currentGamePackage = null
        Log.d(TAG, "ForegroundGameWatcher stopped")
    }
}
