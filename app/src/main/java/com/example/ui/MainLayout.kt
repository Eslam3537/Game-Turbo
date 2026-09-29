package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.designsystem.*
import com.example.ui.screens.*

@Composable
fun DashboardLayout(
    viewModel: BoosterViewModel,
    onRequestPermissions: () -> Unit = {}
) {
    val currentTab by viewModel.currentScreen.collectAsState()
    val isArabic by viewModel.selectedLanguage.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val showPermissionCenter by viewModel.showPermissionCenter.collectAsState()
    val warningDialogData by viewModel.warningDialogData.collectAsState()

    val isRtl = isArabic == "ar"
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val theme = getAppleTheme(themeMode = themeMode)

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            bottomBar = {
                AppleFloatingTabBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.setScreen(it) },
                    theme = theme,
                    isRtl = isRtl
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .drawBehind {
                    if (theme.isDark) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    theme.accent.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                radius = size.width * 0.9f,
                                center = Offset(size.width * 0.85f, size.height * 0.12f)
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    theme.accentBlue.copy(alpha = 0.04f),
                                    Color.Transparent
                                ),
                                radius = size.width * 0.7f,
                                center = Offset(size.width * 0.15f, size.height * 0.85f)
                            )
                        )
                    }
                }
                .windowInsetsPadding(WindowInsets.statusBars)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(160))
                    },
                    label = "TabTransitions"
                ) { screen ->
                    when (screen) {
                        "home" -> AppleHomeScreen(
                            viewModel = viewModel,
                            theme = theme,
                            isRtl = isRtl,
                            onRequestPermissions = onRequestPermissions
                        )
                        "performance" -> ApplePerformanceScreen(
                            viewModel = viewModel,
                            theme = theme,
                            isRtl = isRtl
                        )
                        "status" -> AppleOptimizationStatusScreen(
                            viewModel = viewModel,
                            theme = theme,
                            isRtl = isRtl
                        )
                        "network" -> AppleNetworkScreen(
                            viewModel = viewModel,
                            theme = theme,
                            isRtl = isRtl
                        )
                        "thermal" -> AppleThermalScreen(
                            viewModel = viewModel,
                            theme = theme,
                            isRtl = isRtl
                        )
                        "games" -> AppleGamesScreen(
                            viewModel = viewModel,
                            theme = theme,
                            isRtl = isRtl
                        )
                        "settings" -> AppleSettingsScreen(
                            viewModel = viewModel,
                            theme = theme,
                            isRtl = isRtl
                        )
                    }
                }

                // Apple Smart Permissions Center Modal Dialog
                if (showPermissionCenter) {
                    PermissionCenterDialog(
                        viewModel = viewModel,
                        theme = theme,
                        onRequestAllPermissions = onRequestPermissions,
                        onDismiss = { viewModel.setPermissionCenterVisible(false) }
                    )
                }

                // Dangerous Action Confirmation Dialog
                warningDialogData?.let { data ->
                    AppleDangerousActionDialog(
                        title = data.title,
                        description = data.description,
                        riskLevel = data.riskLevel,
                        theme = theme,
                        isRtl = isRtl,
                        onConfirm = data.onConfirm,
                        onDismiss = { viewModel.dismissWarning() }
                    )
                }

                // In-App Update Dialog
                val updateState by com.example.engine.update.InAppUpdateManager.dialogState.collectAsState()
                if (updateState is com.example.engine.update.UpdateDialogState.Visible) {
                    com.example.ui.components.InAppUpdateDialog(
                        state = updateState as com.example.engine.update.UpdateDialogState.Visible,
                        theme = theme
                    )
                }
            }
        }
    }
}
