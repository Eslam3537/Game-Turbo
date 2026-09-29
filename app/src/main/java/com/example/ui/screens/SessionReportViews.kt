package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import com.example.engine.detection.DeviceHealthScanReport
import com.example.engine.detection.HealthScanVerdict
import com.example.engine.session.SessionActiveReport
import com.example.ui.designsystem.*

@Composable
fun DeviceHealthScanDialog(
    report: DeviceHealthScanReport,
    theme: AppleThemeTokens,
    isRtl: Boolean,
    onStartSession: () -> Unit,
    onDismiss: () -> Unit
) {
    val verdictColor = when (report.verdict) {
        HealthScanVerdict.READY -> theme.accent
        HealthScanVerdict.READY_WITH_WARNINGS -> theme.warning
        HealthScanVerdict.NOT_READY -> theme.danger
    }
    val verdictTitle = when (report.verdict) {
        HealthScanVerdict.READY -> if (isRtl) "الجهاز جاهز 100% لبدء اللعب" else "Device Ready for Gaming"
        HealthScanVerdict.READY_WITH_WARNINGS -> if (isRtl) "جاهز مع وجود بعض التنبيهات" else "Ready with Warnings"
        HealthScanVerdict.NOT_READY -> if (isRtl) "يلزم اتخاذ إجراء قبل البدء" else "Action Required Before Launch"
    }

    Dialog(onDismissRequest = onDismiss) {
        AppleGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            level = GlassLevel.Elevated,
            theme = theme,
            cornerRadius = AppleRadius.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(verdictColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (report.verdict) {
                                HealthScanVerdict.READY -> Icons.Default.CheckCircle
                                HealthScanVerdict.READY_WITH_WARNINGS -> Icons.Default.Warning
                                HealthScanVerdict.NOT_READY -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = verdictColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isRtl) "فحص جاهزية العتاد" else "Hardware Readiness Scan",
                            style = AppleTypography.titleMedium,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = verdictTitle,
                            style = AppleTypography.caption,
                            color = verdictColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Check Items List
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(report.items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppleRadius.small))
                                .background(theme.glassSubtle)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (item.isHealthy) theme.accent else theme.warning,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isRtl) item.titleAr else item.titleEn,
                                    style = AppleTypography.footnote,
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isRtl) item.statusAr else item.statusEn,
                                    style = AppleTypography.caption,
                                    color = theme.textSecondary
                                )
                            }
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(AppleRadius.medium),
                        border = BorderStroke(1.dp, theme.divider)
                    ) {
                        Text(
                            text = if (isRtl) "إغلاق" else "Close",
                            color = theme.textPrimary
                        )
                    }
                    Button(
                        onClick = {
                            onDismiss()
                            onStartSession()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(AppleRadius.medium)
                    ) {
                        Text(
                            text = if (isRtl) "بدء الجلسة" else "Start Session",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveSessionTelemetryCard(
    report: SessionActiveReport,
    theme: AppleThemeTokens,
    isRtl: Boolean,
    onEndSession: () -> Unit
) {
    val mins = report.durationSeconds / 60
    val secs = report.durationSeconds % 60
    val formattedDuration = String.format("%02d:%02d", mins, secs)

    AppleGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        level = GlassLevel.Elevated,
        theme = theme,
        cornerRadius = AppleRadius.large,
        glowColor = theme.accent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            .size(10.dp)
                            .background(theme.accent, CircleShape)
                    )
                    Text(
                        text = if (isRtl) "جلسة اللعب النشطة" else "Active Game Session",
                        style = AppleTypography.titleSmall,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = formattedDuration,
                    style = AppleTypography.caption,
                    color = theme.accent,
                    fontWeight = FontWeight.Bold
                )
            }

            // Real Before vs After Comparison Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val baseRttStr = report.baselineRttMs?.let { "$it ms" } ?: "—"
                val currRttStr = report.currentRttMs?.let { "$it ms" } ?: "—"
                val isRttImprovement = report.currentRttMs != null && report.baselineRttMs != null && report.currentRttMs <= report.baselineRttMs

                ComparisonPill(
                    title = if (isRtl) "الاستجابة (RTT)" else "Ping RTT",
                    before = baseRttStr,
                    current = currRttStr,
                    isImprovement = isRttImprovement,
                    theme = theme
                )

                val baseJitterStr = report.baselineJitterMs?.let { "±$it ms" } ?: "—"
                val currJitterStr = report.currentJitterMs?.let { "±$it ms" } ?: "—"
                val isJitterImprovement = report.currentJitterMs != null && report.baselineJitterMs != null && report.currentJitterMs <= report.baselineJitterMs

                ComparisonPill(
                    title = if (isRtl) "التذبذب (Jitter)" else "Jitter",
                    before = baseJitterStr,
                    current = currJitterStr,
                    isImprovement = isJitterImprovement,
                    theme = theme
                )

                val baseTempStr = report.baselineTempC?.let { String.format("%.1f°", it) } ?: "—"
                val currTempStr = report.currentTempC?.let { String.format("%.1f°", it) } ?: "—"
                val isTempStable = report.currentTempC == null || report.baselineTempC == null || report.currentTempC <= report.baselineTempC + 2.0

                ComparisonPill(
                    title = if (isRtl) "الحرارة" else "Temp",
                    before = baseTempStr,
                    current = currTempStr,
                    isImprovement = isTempStable,
                    theme = theme
                )
            }

            // Status Note & End Session Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRtl) "تم التحقق من ${report.verifiedOptimizations} إعداد" else "${report.verifiedOptimizations} verified optimizations",
                    style = AppleTypography.caption,
                    color = theme.textSecondary
                )
                Button(
                    onClick = onEndSession,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.danger.copy(alpha = 0.2f), contentColor = theme.danger),
                    border = BorderStroke(1.dp, theme.danger.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(AppleRadius.medium),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = if (isRtl) "إنهاء واستعادة" else "End & Revert",
                        style = AppleTypography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ComparisonPill(
    title: String,
    before: String,
    current: String,
    isImprovement: Boolean,
    theme: AppleThemeTokens
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(AppleRadius.medium))
            .background(theme.glassSubtle)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = AppleTypography.caption,
            color = theme.textTertiary,
            fontSize = 10.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = before,
                style = AppleTypography.caption,
                color = theme.textSecondary,
                fontSize = 11.sp
            )
            Text(text = "→", color = theme.textTertiary, fontSize = 10.sp)
            Text(
                text = current,
                style = AppleTypography.caption,
                color = if (isImprovement) theme.accent else theme.warning,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
