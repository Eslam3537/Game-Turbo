package com.example.engine.detection

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.data.AddedGame
import com.example.util.PermissionManager
import kotlinx.coroutines.*

/**
 * Real-time Foreground Game Detector using Android UsageStatsManager (Fix A15).
 * Listens for AppOpsManager permission changes and delays game exit by 10s to prevent accidental session termination.
 */
class ForegroundGameWatcher(
    private val context: Context,
    private val onGameForeground: (AddedGame) -> Unit,
    private val onGameBackground: (String) -> Unit
) {
    private val TAG = "ForegroundGameWatcher"
    private var watchJob: Job? = null
    private var currentGamePackage: String? = null
    private var appOpsListener: AppOpsManager.OnOpChangedListener? = null

    fun startWatching(scope: CoroutineScope, getGamesList: suspend () -> List<AddedGame>) {
        if (watchJob != null) return

        registerAppOpsListener(scope, getGamesList)

        if (!PermissionManager.hasUsageStatsPermission(context)) {
            Log.w(TAG, "Cannot start ForegroundGameWatcher: PACKAGE_USAGE_STATS permission missing")
            return
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return

        watchJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "ForegroundGameWatcher started")
            var lastCheckTime = System.currentTimeMillis() - 4000
            var exitCandidatePackage: String? = null
            var exitCandidateTimestamp = 0L

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
                            if (matchedGame != null) {
                                exitCandidatePackage = null // Cancel pending exit if game resumed
                                if (currentGamePackage != pkg) {
                                    currentGamePackage = pkg
                                    Log.i(TAG, "Detected game entered foreground: ${matchedGame.name} ($pkg)")
                                    withContext(Dispatchers.Main) {
                                        onGameForeground(matchedGame)
                                    }
                                }
                            }
                        } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                            event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                            if (currentGamePackage == pkg && exitCandidatePackage != pkg) {
                                exitCandidatePackage = pkg
                                exitCandidateTimestamp = now
                            }
                        }
                    }

                    // Fix A15: End session only after game has been out of foreground for 10 seconds
                    if (exitCandidatePackage != null && (now - exitCandidateTimestamp) >= 10000L) {
                        val exitingPkg = exitCandidatePackage!!
                        exitCandidatePackage = null
                        if (currentGamePackage == exitingPkg) {
                            currentGamePackage = null
                            Log.i(TAG, "Game confirmed out of foreground for 10s: $exitingPkg")
                            withContext(Dispatchers.Main) {
                                onGameBackground(exitingPkg)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Error in ForegroundGameWatcher: ${e.message}")
                }
                delay(2000)
            }
        }
    }

    private fun registerAppOpsListener(scope: CoroutineScope, getGamesList: suspend () -> List<AddedGame>) {
        if (appOpsListener != null) return
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return

        appOpsListener = AppOpsManager.OnOpChangedListener { op, pkg ->
            if (op == AppOpsManager.OPSTR_GET_USAGE_STATS && pkg == context.packageName) {
                if (PermissionManager.hasUsageStatsPermission(context) && watchJob == null) {
                    Log.i(TAG, "Usage access granted dynamically. Starting game watcher.")
                    startWatching(scope, getGamesList)
                }
            }
        }

        try {
            appOps.startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS, context.packageName, appOpsListener!!)
        } catch (_: Throwable) {}
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        currentGamePackage = null
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        appOpsListener?.let {
            try { appOps?.stopWatchingMode(it) } catch (_: Throwable) {}
        }
        appOpsListener = null
        Log.d(TAG, "ForegroundGameWatcher stopped")
    }
}
