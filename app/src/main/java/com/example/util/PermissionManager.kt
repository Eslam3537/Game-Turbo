package com.example.util

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

data class PermissionStatus(
    val hasNotification: Boolean = false,
    val hasShizuku: Boolean = false,
    val isShizukuServiceRunning: Boolean = false,
    val isShizukuInstalled: Boolean = false,
    val hasOverlay: Boolean = false,
    val hasUsageStats: Boolean = false,
    val hasDnd: Boolean = false
) {
    val totalRequired: Int = 5 // Notifications, Shizuku, Overlay, UsageStats, DND

    val grantedCount: Int
        get() {
            var count = 0
            if (hasNotification) count++
            if (hasOverlay) count++
            if (hasUsageStats) count++
            if (hasDnd) count++
            if (hasShizuku) count++
            return count
        }

    val isAllCoreGranted: Boolean
        get() = hasNotification && hasOverlay && hasUsageStats && hasDnd

    val isFullyEmpowered: Boolean
        get() = isAllCoreGranted && hasShizuku
}

object PermissionManager {
    private const val TAG = "PermissionManager"
    const val SHIZUKU_REQUEST_CODE = 1001

    fun checkStatus(context: Context): PermissionStatus {
        val hasNotification = hasNotificationPermission(context)
        val hasOverlay = hasOverlayPermission(context)
        val hasUsageStats = hasUsageStatsPermission(context)
        val hasDnd = hasDndPermission(context)
        val isShizukuRunning = isShizukuRunning()
        val hasShizuku = hasShizukuPermission()
        val isShizukuInstalled = isPackageInstalled(context, "moe.shizuku.privileged.api")

        return PermissionStatus(
            hasNotification = hasNotification,
            hasShizuku = hasShizuku,
            isShizukuServiceRunning = isShizukuRunning,
            isShizukuInstalled = isShizukuInstalled,
            hasOverlay = hasOverlay,
            hasUsageStats = hasUsageStats,
            hasDnd = hasDnd
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasDndPermission(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return notificationManager.isNotificationPolicyAccessGranted
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int = SHIZUKU_REQUEST_CODE) {
        try {
            if (isShizukuRunning() && !hasShizukuPermission()) {
                if (!Shizuku.isPreV11()) {
                    Shizuku.requestPermission(requestCode)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed requesting Shizuku permission: ${e.message}")
        }
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Throwable) {}
        }
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Throwable) {}
        }
    }

    fun openDndSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {}
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
