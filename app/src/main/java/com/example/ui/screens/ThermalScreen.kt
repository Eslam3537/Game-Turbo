package com.example.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.thermal.ThermalStatusLevel
import com.example.ui.BoosterViewModel
import com.example.ui.designsystem.*

@Composable
fun AppleThermalScreen(
    viewModel: BoosterViewModel,
    theme: AppleThemeTokens,
    isRtl: Boolean
) {
    val context = LocalContext.current
    val thermalSnapshot by viewModel.thermalEngine.thermalState.collectAsState()
    val safeMode by viewModel.safeMode.collectAsState()

    val levelColor = when (thermalSnapshot.level) {
        ThermalStatusLevel.NORMAL -> theme.accent
        ThermalStatusLevel.WARM -> theme.warning
        ThermalStatusLevel.HOT -> theme.warning
        ThermalStatusLevel.CRITICAL -> theme.danger
        ThermalStatusLevel.UNKNOWN -> theme.textSecondary
    }

    val levelName = when (thermalSnapshot.level) {
        ThermalStatusLevel.NORMAL -> if (isRtl) "طبيعي (مستقر)" else "Normal (Stable)"
        ThermalStatusLevel.WARM -> if (isRtl) "دافئ (متوسط)" else "Warm (Moderate)"
        ThermalStatusLevel.HOT -> if (isRtl) "مرتفع (تقييد متوقع)" else "Hot (Throttling Likely)"
        ThermalStatusLevel.CRITICAL -> if (isRtl) "حرج (تنبيه)" else "Critical (Thermal Alert)"
        ThermalStatusLevel.UNKNOWN -> if (isRtl) "غير متاح" else "Unknown"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppleSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AppleSpacing.m),
        contentPadding = PaddingValues(top = AppleSpacing.m, bottom = 120.dp)
    ) {
        // 1. Header
        item {
            Column {
                Text(
                    text = if (isRtl) "الحماية الحرارية واستقرار العتاد" else "Thermal Guard & Stability",
                    style = AppleTypography.displayMedium,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isRtl) "مراقبة مستمرة لحرارة البطارية وحماية العتاد من التقييد المفاجئ" else "Continuous battery thermal telemetry & throttling prevention",
                    style = AppleTypography.footnote,
                    color = theme.textSecondary
                )
            }
        }

        // 2. Primary Thermal Hero Dial
        item {
            AppleGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                level = GlassLevel.Elevated,
                theme = theme,
                cornerRadius = AppleRadius.large,
                glowColor = levelColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppleSpacing.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppleRadius.pill))
                            .background(levelColor.copy(alpha = 0.15f))
                            .border(1.dp, levelColor.copy(alpha = 0.35f), RoundedCornerShape(AppleRadius.pill))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(levelColor, CircleShape)
                        )
                        Text(
                            text = levelName,
                            style = AppleTypography.caption,
                            color = levelColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Temperature Display
                    val tempStr = thermalSnapshot.batteryTempCelsius?.let { String.format("%.1f", it) } ?: "—"
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = tempStr,
                            style = AppleTypography.displayLarge,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "°C",
                            style = AppleTypography.titleLarge,
                            color = theme.textTertiary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // Advice message
                    Text(
                        text = if (isRtl) thermalSnapshot.adviceMessageAr else thermalSnapshot.adviceMessageEn,
                        style = AppleTypography.footnote,
                        color = theme.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // 3. Engineering Principle Notice
        item {
            AppleGlassSurface(
                level = GlassLevel.Subtle,
                theme = theme,
                cornerRadius = AppleRadius.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppleSpacing.m),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = theme.accentBlue,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                    )
                    Text(
                        text = if (isRtl)
                            "ملاحظة هندسية: لا يمكن لتطبيق برمجي تبريد الهاتف مادياً. وظيفة الحماية الحرارية هي مراقبة درجات الحرارة الحقيقية والتراجع التلقائي عن الإعدادات العنيفة قبل حدوث تقييد حراري طارئ من النظام."
                        else
                            "Engineering Note: No software can physically cool a device. Thermal Guard monitors real temperature thresholds and automatically rolls back aggressive settings to prevent emergency system throttling.",
                        style = AppleTypography.footnote,
                        color = theme.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 4. Safe Mode Controls
        item {
            AppleSectionHeader(
                title = if (isRtl) "إعدادات الأمان التلقائية" else "Thermal Mitigation Controls",
                theme = theme
            )
        }

        item {
            AppleGroupedCard(theme = theme) {
                AppleSettingsRow(
                    title = if (isRtl) "الحماية التلقائية (Safe Mode)" else "Automatic Thermal Protection (Safe Mode)",
                    subtitle = if (isRtl) "استعادة الإعدادات الأصلية تلقائياً عند تجاوز 41°م لمنع تلف العتاد" else "Automatically revert aggressive tweaks when temperature exceeds 41°C",
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
            }
        }

        // 5. Thermal Safety Profile
        item {
            OptimizationWarningCard(
                profile = FeatureSafetyProfile(
                    featureId = "thermal_guard_profile",
                    titleEn = "Thermal Guard Governor",
                    titleAr = "حاكم الحماية الحرارية",
                    componentEn = "Kernel Power & Thermal HAL",
                    componentAr = "طبقة عتاد الطاقة والحرارة بنواة أندرويد",
                    purposeEn = "Dynamically monitors device temperatures and throttles back aggressive settings safely.",
                    purposeAr = "مراقبة مستمرة لحرارة الهاتف والتراجع التلقائي عن الإعدادات الحادة للحفاظ على سلامة العتاد.",
                    expectedBenefitEn = "Prevents sudden mid-game frame rate collapse caused by OEM thermal emergency throttling.",
                    expectedBenefitAr = "تفادي هبوط معدل الإطارات المفاجئ الناجم عن تدخل نظام التبريد الطارئ للهاتف.",
                    potentialSideEffectsEn = "When triggered, high-performance overrides are removed until the phone cools down.",
                    potentialSideEffectsAr = "عند التفعيل، تُعطل الإعدادات العنيفة مؤقتاً حتى يبرد الهاتف.",
                    severity = WarningSeverityLevel.LEVEL_1_LOW,
                    increasesThermal = false,
                    increasesBattery = false
                ),
                theme = theme,
                isRtl = isRtl,
                currentThermalLevel = thermalSnapshot.level
            )
        }
    }
}
