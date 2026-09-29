package com.example.ui.designsystem

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.thermal.ThermalStatusLevel

enum class WarningSeverityLevel(val nameEn: String, val nameAr: String) {
    LEVEL_0_INFO("Information", "معلومات"),
    LEVEL_1_LOW("Low Impact", "تأثير منخفض"),
    LEVEL_2_MODERATE("Moderate Impact", "تأثير متوسط"),
    LEVEL_3_HIGH("High Impact", "تأثير مرتفع")
}

data class FeatureSafetyProfile(
    val featureId: String,
    val titleEn: String,
    val titleAr: String,
    val componentEn: String,
    val componentAr: String,
    val purposeEn: String,
    val purposeAr: String,
    val expectedBenefitEn: String,
    val expectedBenefitAr: String,
    val potentialSideEffectsEn: String,
    val potentialSideEffectsAr: String,
    val severity: WarningSeverityLevel,
    val increasesThermal: Boolean = false,
    val increasesBattery: Boolean = false,
    val autoRollbackSupported: Boolean = true,
    val verificationMethodEn: String = "Read-back comparison of system setting value.",
    val verificationMethodAr: String = "التحقق المباشر من القيمة الحقيقية المقروءة من النظام.",
    val rollbackMethodEn: String = "Restores original value captured in session snapshot.",
    val rollbackMethodAr: String = "استعادة القيمة الأصلية المأخوذة في لقطة الجلسة."
)

object SafetyCatalog {
    val CPU_GOVERNOR = FeatureSafetyProfile(
        featureId = "cpu_governor",
        titleEn = "CPU High Performance Governor",
        titleAr = "حاكم أداء أنوية المعالج (CPU)",
        componentEn = "Kernel Task Scheduler & Performance Governor",
        componentAr = "حاكم ترددات المعالج ومجدول المهام",
        purposeEn = "Prioritizes gaming execution threads on high-performance governor for sustained frame pacing.",
        purposeAr = "توجيه أنوية المعالج للعمل بالتردد العالي لتقليل التذبذب في سرعة الإطارات.",
        expectedBenefitEn = "More consistent frame pacing during high-load combat scenes.",
        expectedBenefitAr = "استقرار أفضل في معدل الإطارات أثناء المشاهد المزدحمة.",
        potentialSideEffectsEn = "Higher power draw and increased SoC heat dissipation over long sessions.",
        potentialSideEffectsAr = "زيادة استهلاك الطاقة وارتفاع تدريجي في حرارة الهاتف مع الجلسات الطويلة.",
        severity = WarningSeverityLevel.LEVEL_2_MODERATE,
        increasesThermal = true,
        increasesBattery = true
    )

    val PRIVATE_DNS_LOCK = FeatureSafetyProfile(
        featureId = "private_dns_lock",
        titleEn = "Encrypted Low-Latency DNS",
        titleAr = "مزود DNS المشفر للألعاب",
        componentEn = "Android Network Stack & DNS Resolver",
        componentAr = "مكدس الشبكة في أندرويد ومحلل أسماء النطاقات",
        purposeEn = "Routes UDP game traffic via verified low-latency host (e.g. Cloudflare 1.1.1.1).",
        purposeAr = "توجيه حزم البيانات عبر مزود DNS سريع ومشفر لتفادي بطء الاستجابة المحلي.",
        expectedBenefitEn = "Reduces matchmaking DNS lookup delay and stabilizes packet routing.",
        expectedBenefitAr = "تسريع استجابة الاتصال بخوادم اللعبة وتثبيت مسار البيانات.",
        potentialSideEffectsEn = "If private DNS is blocked on some restricted public Wi-Fi networks, connection may stall until reverted.",
        potentialSideEffectsAr = "إذا كانت شبكة Wi-Fi تمنع DNS المشفر، قد يتعطل الاتصال حتى استعادة الإعداد.",
        severity = WarningSeverityLevel.LEVEL_1_LOW,
        increasesThermal = false,
        increasesBattery = false
    )

    val REFRESH_RATE = FeatureSafetyProfile(
        featureId = "peak_refresh_rate",
        titleEn = "Peak Display Refresh Rate Lock",
        titleAr = "تثبيت أعلى معدل تحديث للشاشة",
        componentEn = "Display Manager & SurfaceFlinger",
        componentAr = "مدير الشاشة ومحول الإطارات SurfaceFlinger",
        purposeEn = "Prevents dynamic down-clocking of screen refresh rate during gameplay.",
        purposeAr = "منع الشاشة من خفض التردد فجأة أثناء اللعب لتفادي هبوط الإطارات.",
        expectedBenefitEn = "Fluid touch aim and zero refresh-rate switching stutter.",
        expectedBenefitAr = "سلاسة كاملة في حركة الشاشة وتفادي تقطيع تبديل التردد.",
        potentialSideEffectsEn = "Slightly higher display controller power consumption.",
        potentialSideEffectsAr = "زيادة طفيفة في استهلاك طاقة الشاشة.",
        severity = WarningSeverityLevel.LEVEL_1_LOW,
        increasesThermal = false,
        increasesBattery = true
    )
}

@Composable
fun OptimizationWarningCard(
    profile: FeatureSafetyProfile,
    theme: AppleThemeTokens,
    isRtl: Boolean,
    currentThermalLevel: ThermalStatusLevel = ThermalStatusLevel.NORMAL,
    currentBatteryPercent: Int = 80,
    modifier: Modifier = Modifier
) {
    var expandedDetails by remember { mutableStateOf(false) }

    val severityColor = when (profile.severity) {
        WarningSeverityLevel.LEVEL_0_INFO -> theme.accentBlue
        WarningSeverityLevel.LEVEL_1_LOW -> theme.accent
        WarningSeverityLevel.LEVEL_2_MODERATE -> theme.warning
        WarningSeverityLevel.LEVEL_3_HIGH -> theme.danger
    }

    val isDeviceHot = currentThermalLevel == ThermalStatusLevel.HOT || currentThermalLevel == ThermalStatusLevel.CRITICAL
    val isBatteryLow = currentBatteryPercent < 20
    val dynamicWarning = when {
        isDeviceHot && profile.increasesThermal -> {
            if (isRtl) "تحذير حراري: الهاتف دافئ حالياً! هذا الإعداد يزيد من الحمل الحراري."
            else "Thermal Warning: Device is currently warm! This feature will increase thermal load."
        }
        isBatteryLow && profile.increasesBattery -> {
            if (isRtl) "تحذير البطارية ($currentBatteryPercent%): هذا الإعداد يستهلك المزيد من الطاقة."
            else "Low Battery Warning ($currentBatteryPercent%): This feature increases power draw."
        }
        else -> null
    }

    AppleGlassSurface(
        modifier = modifier.fillMaxWidth(),
        level = GlassLevel.Subtle,
        theme = theme,
        cornerRadius = AppleRadius.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(severityColor, CircleShape)
                    )
                    Text(
                        text = if (isRtl) profile.titleAr else profile.titleEn,
                        style = AppleTypography.titleSmall,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppleRadius.pill))
                        .background(severityColor.copy(alpha = 0.14f))
                        .border(0.5.dp, severityColor.copy(alpha = 0.4f), RoundedCornerShape(AppleRadius.pill))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isRtl) profile.severity.nameAr else profile.severity.nameEn,
                        style = AppleTypography.caption,
                        color = severityColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            // Benefit
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = theme.accent,
                    modifier = Modifier.size(15.dp).padding(top = 2.dp)
                )
                Text(
                    text = if (isRtl) profile.expectedBenefitAr else profile.expectedBenefitEn,
                    style = AppleTypography.footnote,
                    color = theme.textPrimary
                )
            }

            // Side effect
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = if (profile.severity == WarningSeverityLevel.LEVEL_3_HIGH) theme.danger else theme.warning,
                    modifier = Modifier.size(15.dp).padding(top = 2.dp)
                )
                Text(
                    text = if (isRtl) profile.potentialSideEffectsAr else profile.potentialSideEffectsEn,
                    style = AppleTypography.footnote,
                    color = theme.textSecondary
                )
            }

            if (dynamicWarning != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppleRadius.small))
                        .background(theme.danger.copy(alpha = 0.12f))
                        .border(1.dp, theme.danger.copy(alpha = 0.35f), RoundedCornerShape(AppleRadius.small))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = dynamicWarning,
                        style = AppleTypography.caption,
                        color = theme.danger,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (profile.increasesThermal) {
                    ImpactBadge(title = if (isRtl) "الحرارة ↑" else "Thermals ↑", color = theme.warning)
                }
                if (profile.increasesBattery) {
                    ImpactBadge(title = if (isRtl) "البطارية ↑" else "Battery ↑", color = theme.warning)
                }
                if (profile.autoRollbackSupported) {
                    ImpactBadge(title = if (isRtl) "استعادة أصلية ✓" else "Auto-Revert ✓", color = theme.accent)
                }
            }

            // Expandable Technical Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDetails = !expandedDetails }
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRtl) "تفاصيل التحقق والاستعادة" else "Verification & Rollback Details",
                    style = AppleTypography.caption,
                    color = theme.accentBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expandedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = theme.accentBlue,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = expandedDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppleRadius.small))
                        .background(theme.surfaceElevated)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailRow(
                        label = if (isRtl) "المكون المستهدف" else "Target Component",
                        value = if (isRtl) profile.componentAr else profile.componentEn,
                        theme = theme
                    )
                    DetailRow(
                        label = if (isRtl) "طريقة التحقق" else "Verification Method",
                        value = if (isRtl) profile.verificationMethodAr else profile.verificationMethodEn,
                        theme = theme
                    )
                    DetailRow(
                        label = if (isRtl) "طريقة الاستعادة" else "Rollback Method",
                        value = if (isRtl) profile.rollbackMethodAr else profile.rollbackMethodEn,
                        theme = theme
                    )
                }
            }
        }
    }
}

@Composable
fun ImpactBadge(title: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppleRadius.pill))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(AppleRadius.pill))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = title,
            style = AppleTypography.caption,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, theme: AppleThemeTokens) {
    Column {
        Text(
            text = label,
            style = AppleTypography.caption,
            color = theme.textTertiary,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
        Text(
            text = value,
            style = AppleTypography.footnote,
            color = theme.textPrimary,
            fontSize = 11.sp
        )
    }
}
