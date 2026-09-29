package com.example.engine.capability

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import rikka.shizuku.Shizuku

enum class CapabilityState {
    SUPPORTED,
    UNSUPPORTED,
    SUPPORTED_BUT_PERMISSION_REQUIRED,
    SUPPORTED_BUT_RESTRICTED,
    UNKNOWN
}

data class DeviceCapabilities(
    val androidVersion: String,
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val socBoard: String,
    val maxRefreshRateHz: Float,
    val supportedRefreshRates: List<Float>,
    val thermalApiState: CapabilityState,
    val shizukuExecutionState: CapabilityState,
    val systemSettingsTuningState: CapabilityState
)

object DeviceCapabilityEngine {
    fun scan(context: Context): DeviceCapabilities {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val supportedRates = defaultDisplay?.supportedModes?.map { it.refreshRate }?.distinct()?.sorted()
            ?: listOf(60.0f)
        val maxRate = supportedRates.maxOrNull() ?: 60.0f

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            CapabilityState.SUPPORTED
        } else {
            CapabilityState.UNSUPPORTED
        }

        val shizukuState = try {
            if (!Shizuku.pingBinder()) {
                CapabilityState.UNSUPPORTED
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                CapabilityState.SUPPORTED
            } else {
                CapabilityState.SUPPORTED_BUT_PERMISSION_REQUIRED
            }
        } catch (_: Throwable) {
            CapabilityState.UNSUPPORTED
        }

        val settingsState = if (shizukuState == CapabilityState.SUPPORTED) {
            CapabilityState.SUPPORTED
        } else {
            CapabilityState.SUPPORTED_BUT_RESTRICTED
        }

        return DeviceCapabilities(
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            socBoard = Build.BOARD ?: Build.HARDWARE ?: "Generic",
            maxRefreshRateHz = maxRate,
            supportedRefreshRates = supportedRates,
            thermalApiState = thermalState,
            shizukuExecutionState = shizukuState,
            systemSettingsTuningState = settingsState
        )
    }
}
