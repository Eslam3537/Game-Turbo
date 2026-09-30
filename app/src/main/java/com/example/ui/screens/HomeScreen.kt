package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BoosterViewModel
import com.example.ui.designsystem.*

@Composable
fun AppleHomeScreen(
    viewModel: BoosterViewModel,
    theme: AppleThemeTokens,
    isRtl: Boolean,
    onRequestPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val isOptimized by viewModel.isOptimized.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()
    val shizukuActive by viewModel.shizukuActive.collectAsState()
    val permissionsStatus by viewModel.permissionsState.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val overlayEnabled by viewModel.overlayEnabled.collectAsState()
    val safeMode by viewModel.safeMode.collectAsState()
    val autoDetectEnabled by viewModel.autoDetectEnabled.collectAsState()

    // Real telemetry (Nullable when unavailable)
    val ramUsage by viewModel.ramUsage.collectAsState()
    val gameFps by viewModel.gameFps.collectAsState()
    val appUiFps by viewModel.appUiFps.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val currentPing by viewModel.currentPing.collectAsState()
    val pingMe by viewModel.pingMiddleEast.collectAsState()

    val gamesList by viewModel.gamesList.collectAsState()
    val selectedPkg by viewModel.selectedGamePackage.collectAsState()
    val activeSessionReport by viewModel.activeSessionReport.collectAsState()
    val healthScanReport by viewModel.healthScanReport.collectAsState()

    val activeGame = gamesList.firstOrNull { it.packageName == selectedPkg }
        ?: gamesList.firstOrNull()

    val tempLevel = when {
        temperature == null -> if (isRtl) "غير متاح" else "Unavailable"
        temperature!! >= 45.0 -> if (isRtl) "حرج (تنبيه)" else "Critical"
        temperature!! >= 41.0 -> if (isRtl) "مرتفع (تقييد)" else "High"
        temperature!! >= 38.0 -> if (isRtl) "دافئ" else "Warm"
        else -> if (isRtl) "طبيعي ومستقر" else "Normal"
    }

    val tempColor = when {
        temperature == null -> theme.textSecondary
        temperature!! >= 45.0 -> theme.danger
        temperature!! >= 41.0 -> theme.warning
        temperature!! >= 38.0 -> theme.warning
        else -> theme.accent
    }

    val profileDisplayName = when (selectedProfile) {
        "performance" -> if (isRtl) "أداء فائق" else "Performance"
        "battery" -> if (isRtl) "توفير الطاقة" else "Battery Saver"
        "competitive" -> if (isRtl) "تنافسي احترافي" else "Competitive"
        else -> if (isRtl) "متوازن" else "Balanced"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppleSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AppleSpacing.m),
        contentPadding = PaddingValues(top = AppleSpacing.m, bottom = 110.dp)
    ) {
        // 1. Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Game Turbo",
                            style = AppleTypography.displayMedium,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Surface(
                            shape = RoundedCornerShape(AppleRadius.pill),
                            color = theme.accent.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.accent.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "v${com.example.BuildConfig.VERSION_NAME}",
                                color = theme.accent,
                                style = AppleTypography.footnote,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isRtl) "نظام تحسين الأداء ومراقبة العتاد الحقيقي" else "Hardware Telemetry & Performance OS",
                        style = AppleTypography.footnote,
                        color = theme.textSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.xs)
                ) {
                    // Permissions Pill Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppleRadius.pill))
                            .background(
                                if (permissionsStatus.isFullyEmpowered) theme.accent.copy(alpha = 0.12f)
                                else theme.glassSubtle
                            )
                            .border(
                                1.dp,
                                if (permissionsStatus.isFullyEmpowered) theme.accent.copy(alpha = 0.35f)
                                else theme.divider,
                                RoundedCornerShape(AppleRadius.pill)
                            )
                            .clickable { viewModel.setPermissionCenterVisible(true) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (permissionsStatus.isFullyEmpowered) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (permissionsStatus.isFullyEmpowered) theme.accent else theme.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (permissionsStatus.isFullyEmpowered) {
                                    if (isRtl) "الأذونات كاملة" else "Permissions ✓"
                                } else {
                                    "${permissionsStatus.grantedCount}/${permissionsStatus.totalRequired}"
                                },
                                style = AppleTypography.caption,
                                fontWeight = FontWeight.Bold,
                                color = if (permissionsStatus.isFullyEmpowered) theme.accent else theme.textPrimary
                            )
                        }
                    }

                    // Theme Toggle Button
                    val currentThemeMode by viewModel.themeMode.collectAsState()
                    IconButton(
                        onClick = {
                            val next = if (currentThemeMode == "dark") "light" else "dark"
                            viewModel.setThemeMode(next)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.glassSubtle)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (theme.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme",
                            tint = theme.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Missing permissions notification banner
        if (!permissionsStatus.isFullyEmpowered) {
            item {
                AppleGlassSurface(
                    level = GlassLevel.Standard,
                    theme = theme,
                    cornerRadius = AppleRadius.medium,
                    onClick = { viewModel.setPermissionCenterVisible(true) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppleSpacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.s)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(theme.accentBlue.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = theme.accentBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRtl) "يلزم استكمال الصلاحيات" else "System Permissions Needed",
                                style = AppleTypography.titleSmall,
                                color = theme.textPrimary
                            )
                            Text(
                                text = if (isRtl) "متبقي ${permissionsStatus.totalRequired - permissionsStatus.grantedCount} أذونات للحصول على الأداء الكامل"
                                else "${permissionsStatus.totalRequired - permissionsStatus.grantedCount} permissions remaining for full optimization",
                                style = AppleTypography.footnote,
                                color = theme.textSecondary
                            )
                        }
                        Button(
                            onClick = {
                                onRequestPermissions()
                                viewModel.setPermissionCenterVisible(true)
                            },
                            shape = RoundedCornerShape(AppleRadius.small),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (isRtl) "منح" else "Grant",
                                style = AppleTypography.caption,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. HERO STATUS COMPONENT
        item {
            AppleHeroStatus(
                isOptimized = isOptimized,
                isOptimizing = isOptimizing,
                profileName = profileDisplayName,
                theme = theme,
                isRtl = isRtl,
                shizukuActive = shizukuActive,
                onToggle = {
                    viewModel.triggerMainBoost(context)
                }
            )
        }

        // Active Session Telemetry Card
        activeSessionReport?.let { report ->
            item {
                ActiveSessionTelemetryCard(
                    report = report,
                    theme = theme,
                    isRtl = isRtl,
                    onEndSession = { viewModel.triggerMainBoost(context) }
                )
            }
        }

        // 3. SELECTED GAME CARD
        if (activeGame != null) {
            item {
                AppleGlassSurface(
                    level = GlassLevel.Standard,
                    theme = theme,
                    cornerRadius = AppleRadius.large,
                    onClick = { viewModel.setScreen("games") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppleSpacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.m)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(AppleRadius.medium))
                                .background(theme.surfaceElevated)
                                .border(1.dp, theme.divider, RoundedCornerShape(AppleRadius.medium)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = activeGame.name,
                                tint = theme.accent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = activeGame.name,
                                    style = AppleTypography.titleSmall,
                                    color = theme.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AppleRadius.pill))
                                        .background(theme.accent.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = profileDisplayName,
                                        style = AppleTypography.caption,
                                        color = theme.accent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = activeGame.packageName,
                                style = AppleTypography.footnote,
                                color = theme.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Pre-game Health Diagnostic Button
                            OutlinedButton(
                                onClick = { viewModel.runDeviceHealthScan() },
                                shape = RoundedCornerShape(AppleRadius.medium),
                                border = BorderStroke(1.dp, theme.accentBlue.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = "Scan",
                                    tint = theme.accentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            // Launch Game
                            Button(
                                onClick = {
                                    viewModel.launchGame(context, activeGame.packageName)
                                },
                                shape = RoundedCornerShape(AppleRadius.medium),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isRtl) "تشغيل" else "Launch",
                                        style = AppleTypography.caption,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. REAL PERFORMANCE METRICS (2x2 Grid)
        item {
            AppleSectionHeader(
                title = if (isRtl) "القياسات المباشرة الحقيقية" else "Live Hardware Telemetry",
                theme = theme
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(AppleSpacing.s)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.s)
                ) {
                    // Game FPS (SurfaceFlinger) or App UI FPS
                    val fpsDisplay = gameFps?.toString() ?: appUiFps?.toString() ?: "—"
                    val fpsLabel = if (gameFps != null) {
                        if (isRtl) "إطارات اللعبة" else "Game FPS"
                    } else {
                        if (isRtl) "إطارات الواجهة" else "App UI FPS"
                    }
                    val fpsStatus = if (gameFps != null) {
                        if (isRtl) "SurfaceFlinger مباشر" else "SurfaceFlinger Live"
                    } else if (appUiFps != null) {
                        if (isRtl) "Choreographer الواجهة" else "In-App Frame Timing"
                    } else {
                        if (isRtl) "اللعبة غير مشغلة" else "Game Not In Foreground"
                    }

                    AppleMetricCard(
                        modifier = Modifier.weight(1f),
                        title = fpsLabel,
                        value = fpsDisplay,
                        unit = "FPS",
                        statusText = fpsStatus,
                        statusColor = if (fpsDisplay != "—") theme.accent else theme.textSecondary,
                        icon = Icons.Default.Speed,
                        theme = theme
                    )

                    // Real TCP Ping
                    val pingDisplay = currentPing?.toString() ?: "—"
                    val pingStatus = when {
                        currentPing == null -> if (isRtl) "جاري الفحص..." else "Probing socket..."
                        currentPing!! <= 45 -> if (isRtl) "استجابة ممتازة" else "Optimal"
                        currentPing!! <= 90 -> if (isRtl) "استجابة جيدة" else "Moderate"
                        else -> if (isRtl) "استجابة مرتفعة" else "Elevated"
                    }
                    AppleMetricCard(
                        modifier = Modifier.weight(1f),
                        title = if (isRtl) "زمن الاستجابة" else "Ping RTT",
                        value = pingDisplay,
                        unit = "ms",
                        statusText = pingStatus,
                        statusColor = if (currentPing != null && currentPing!! <= 50) theme.accent else theme.warning,
                        icon = Icons.Default.CellTower,
                        theme = theme,
                        onClick = { viewModel.setScreen("network") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.s)
                ) {
                    // Temperature
                    val tempDisplay = temperature?.let { String.format("%.1f", it) } ?: "—"
                    AppleMetricCard(
                        modifier = Modifier.weight(1f),
                        title = if (isRtl) "حرارة البطارية" else "Battery Temp",
                        value = tempDisplay,
                        unit = "°C",
                        statusText = tempLevel,
                        statusColor = tempColor,
                        icon = Icons.Default.Thermostat,
                        theme = theme,
                        onClick = { viewModel.setScreen("thermal") }
                    )

                    // RAM Usage
                    val ramDisplay = ramUsage?.toString() ?: "—"
                    val ramStatus = when {
                        ramUsage == null -> if (isRtl) "غير متاح" else "Unavailable"
                        ramUsage!! < 80 -> if (isRtl) "سعة كافية" else "Optimal"
                        else -> if (isRtl) "ضغط مرتفع" else "Heavy"
                    }
                    AppleMetricCard(
                        modifier = Modifier.weight(1f),
                        title = if (isRtl) "استهلاك الذاكرة" else "RAM Usage",
                        value = ramDisplay,
                        unit = "%",
                        statusText = ramStatus,
                        statusColor = if (ramUsage != null && ramUsage!! < 80) theme.accent else theme.warning,
                        icon = Icons.Default.Memory,
                        theme = theme
                    )
                }
            }
        }

        // 5. PROFILE SELECTOR
        item {
            AppleSectionHeader(
                title = if (isRtl) "ملفات نمط الأداء" else "Performance Profiles",
                theme = theme
            )
        }

        item {
            val profiles = listOf("balanced", "performance", "battery", "competitive")
            AppleSegmentedControl(
                items = profiles,
                selectedItem = selectedProfile,
                onItemSelected = { profile ->
                    if (profile == "performance" || profile == "competitive") {
                        viewModel.showWarning(
                            title = if (isRtl) "تنبيه الأداء الأقصى" else "Maximum Performance Notice",
                            description = if (isRtl)
                                "تفعيل نمط الأداء الأقصى يثبت أعلى تردد للشاشة ويعطل حركات الرسوم، مما قد يزيد من استهلاك البطارية. هل ترغب بالمتابعة؟"
                            else
                                "Activating maximum performance locks display refresh rate to peak and disables window animations, increasing battery drain. Proceed?",
                            riskLevel = "Medium",
                            onConfirm = { viewModel.setProfile(profile) }
                        )
                    } else {
                        viewModel.setProfile(profile)
                    }
                },
                labelProvider = { p ->
                    when (p) {
                        "performance" -> if (isRtl) "فائق" else "Speed"
                        "battery" -> if (isRtl) "توفير" else "Eco"
                        "competitive" -> if (isRtl) "احترافي" else "Pro"
                        else -> if (isRtl) "متوازن" else "Balanced"
                    }
                },
                theme = theme
            )
        }

        // 6. TOGGLES
        item {
            AppleGroupedCard(theme = theme) {
                // Floating Overlay
                AppleSettingsRow(
                    title = if (isRtl) "شاشة المراقبة العائمة (Overlay)" else "Floating Telemetry HUD",
                    subtitle = if (isRtl) "عرض معدل إطارات SurfaceFlinger والحرارة فوق اللعبة" else "Draw real-time SurfaceFlinger FPS & temp over game",
                    icon = Icons.Default.Layers,
                    iconTint = theme.accentBlue,
                    theme = theme,
                    trailing = {
                        Switch(
                            checked = overlayEnabled,
                            onCheckedChange = { viewModel.setOverlay(it, context) },
                            colors = SwitchDefaults.colors(checkedTrackColor = theme.accent)
                        )
                    }
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Safe Mode
                AppleSettingsRow(
                    title = if (isRtl) "الحماية الحرارية التلقائية (Safe Mode)" else "Thermal Protection (Safe Mode)",
                    subtitle = if (isRtl) "استعادة الإعدادات الحادة تلقائياً إذا ارتفعت الحرارة فوق 41°م" else "Auto-rollback aggressive tweaks if phone heats up",
                    icon = Icons.Default.Security,
                    iconTint = theme.accent,
                    theme = theme,
                    trailing = {
                        Switch(
                            checked = safeMode,
                            onCheckedChange = { viewModel.setSafeMode(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = theme.accent)
                        )
                    }
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Auto Game Detection
                AppleSettingsRow(
                    title = if (isRtl) "الكشف التلقائي عند فتح اللعبة" else "Foreground Game Auto-Detect",
                    subtitle = if (isRtl) "تفعيل الجلسة تلقائياً عبر UsageStatsManager" else "Detect game launch via UsageStats and trigger session",
                    icon = Icons.Default.Bolt,
                    iconTint = theme.warning,
                    theme = theme,
                    trailing = {
                        Switch(
                            checked = autoDetectEnabled,
                            onCheckedChange = { viewModel.setAutoDetect(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = theme.accent)
                        )
                    }
                )
            }
        }

        // 7. OPTIMIZATION STATUS SHORTCUT CARD
        item {
            AppleGlassSurface(
                level = GlassLevel.Standard,
                theme = theme,
                cornerRadius = AppleRadius.large,
                glowColor = theme.accentBlue.copy(alpha = 0.15f),
                onClick = { viewModel.setScreen("status") }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppleSpacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.m)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(theme.accentBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = theme.accentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isRtl) "تقرير الحقيقة (Reality Report)" else "Reality Report",
                                style = AppleTypography.titleSmall,
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppleRadius.pill))
                                    .background(theme.accentBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isRtl) "فحص ذاتي" else "SELF-TEST",
                                    style = AppleTypography.caption,
                                    color = theme.accentBlue,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = if (isRtl) "فحص وتشخيص ذاتي حي لكل ميزة وأمر في التطبيق مع إظهار الأوامر والنتائج الحقيقية ونسخ التقرير"
                            else "Inspect real execution status, verify system responses, and export full reality report",
                            style = AppleTypography.footnote,
                            color = theme.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (isRtl) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = theme.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 8. SYSTEM DIAGNOSTICS & VERIFIED AUDIT
        item {
            AppleSectionHeader(
                title = if (isRtl) "حالة النظام الحقيقية" else "Verified System State",
                theme = theme
            )
        }

        item {
            AppleGlassSurface(
                level = GlassLevel.Subtle,
                theme = theme,
                cornerRadius = AppleRadius.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppleSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(AppleSpacing.s)
                ) {
                    DiagnosticItem(
                        icon = if (shizukuActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        color = if (shizukuActive) theme.accent else theme.warning,
                        title = if (isRtl) "خدمة Shizuku المتقدمة" else "Shizuku Shell Service",
                        status = if (shizukuActive) {
                            if (isRtl) "متصلة ومصرح لها بتعديل إعدادات العتاد" else "Active & authorized for shell operations"
                        } else {
                            if (isRtl) "غير متصلة (التعديلات المتقدمة معطلة حتى الربط)" else "Offline (Privileged optimizations disabled)"
                        },
                        theme = theme
                    )
                    HorizontalDivider(color = theme.divider, thickness = 0.5.dp)
                    DiagnosticItem(
                        icon = if (temperature != null && temperature!! < 41.0) Icons.Default.CheckCircle else Icons.Default.Thermostat,
                        color = tempColor,
                        title = if (isRtl) "الحالة الحرارية للجهاز" else "Thermal Status",
                        status = if (temperature != null) {
                            "${String.format("%.1f", temperature)}°C - $tempLevel"
                        } else {
                            if (isRtl) "المستشعر غير متاح" else "Sensor unavailable"
                        },
                        theme = theme
                    )
                    HorizontalDivider(color = theme.divider, thickness = 0.5.dp)
                    DiagnosticItem(
                        icon = if (pingMe != null) Icons.Default.CheckCircle else Icons.Default.SignalCellularConnectedNoInternet0Bar,
                        color = if (pingMe != null) theme.accent else theme.textSecondary,
                        title = if (isRtl) "خادم الشرق الأوسط" else "Middle East Server",
                        status = if (pingMe != null) {
                            if (isRtl) "الاستجابة الحقيقية $pingMe ms" else "Real socket latency: $pingMe ms"
                        } else {
                            if (isRtl) "جاري الفحص..." else "Probing socket endpoint..."
                        },
                        theme = theme
                    )
                }
            }
        }
    }

    // Health Scan Dialog
    healthScanReport?.let { report ->
        DeviceHealthScanDialog(
            report = report,
            theme = theme,
            isRtl = isRtl,
            onStartSession = {
                viewModel.triggerMainBoost(context)
            },
            onDismiss = { viewModel.dismissHealthScan() }
        )
    }
}

@Composable
fun DiagnosticItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    title: String,
    status: String,
    theme: AppleThemeTokens
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.s),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = title,
                style = AppleTypography.footnote,
                color = theme.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = status,
                style = AppleTypography.caption,
                color = theme.textSecondary
            )
        }
    }
}
