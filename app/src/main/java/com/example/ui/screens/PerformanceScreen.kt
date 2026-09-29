package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OptimizationLog
import com.example.engine.thermal.ThermalStatusLevel
import com.example.ui.BoosterViewModel
import com.example.ui.designsystem.*
import com.example.util.PermissionManager

@Composable
fun ApplePerformanceScreen(
    viewModel: BoosterViewModel,
    theme: AppleThemeTokens,
    isRtl: Boolean
) {
    val context = LocalContext.current
    val isOptimized by viewModel.isOptimized.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()
    val shizukuActive by viewModel.shizukuActive.collectAsState()
    val logsList by viewModel.logsList.collectAsState()
    val verificationItems by viewModel.verificationItems.collectAsState()
    val thermalSnapshot by viewModel.thermalEngine.thermalState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppleSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AppleSpacing.m),
        contentPadding = PaddingValues(top = AppleSpacing.m, bottom = 110.dp)
    ) {
        // 1. Header
        item {
            Column {
                Text(
                    text = if (isRtl) "محرك الأداء الحقيقي" else "Performance Engine",
                    style = AppleTypography.displayMedium,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isRtl) "ضبط ترددات الشاشة، تسريع استجابة اللمس، وتحرير الذاكرة" else "Hardware scheduling, display refresh rate & touch rate tuning",
                    style = AppleTypography.footnote,
                    color = theme.textSecondary
                )
            }
        }

        // 2. Shizuku Status Banner
        item {
            AppleGlassSurface(
                level = GlassLevel.Elevated,
                theme = theme,
                cornerRadius = AppleRadius.large,
                glowColor = if (shizukuActive) theme.accent else Color.Transparent
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (shizukuActive) theme.accent.copy(alpha = 0.15f)
                                else theme.warning.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (shizukuActive) Icons.Default.Terminal else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (shizukuActive) theme.accent else theme.warning,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isRtl) "خدمة Shizuku المتقدمة" else "Shizuku Shell Service",
                                style = AppleTypography.titleSmall,
                                color = theme.textPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppleRadius.pill))
                                    .background(
                                        if (shizukuActive) theme.accent.copy(alpha = 0.14f)
                                        else theme.warning.copy(alpha = 0.14f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (shizukuActive) {
                                        if (isRtl) "متصل" else "ONLINE"
                                    } else {
                                        if (isRtl) "غير متصل" else "OFFLINE"
                                    },
                                    style = AppleTypography.caption,
                                    color = if (shizukuActive) theme.accent else theme.warning,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = if (shizukuActive) {
                                if (isRtl) "اتصال Binder مؤكد - الأوامر تُنفذ مباشرة في بيئة النظام الأصلية."
                                else "Binder verified - commands execute natively via privileged shell."
                            } else {
                                if (isRtl) "الخدمة غير متصلة - اضغط للربط عبر التصحيح اللاسلكي أو منح الإذن."
                                else "Service offline - tap to grant permission or pair wireless ADB."
                            },
                            style = AppleTypography.footnote,
                            color = theme.textSecondary
                        )
                    }

                    if (!shizukuActive) {
                        Button(
                            onClick = {
                                if (PermissionManager.isShizukuRunning()) {
                                    PermissionManager.requestShizukuPermission()
                                } else {
                                    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    if (intent != null) context.startActivity(intent)
                                    else Toast.makeText(
                                        context,
                                        if (isRtl) "تطبيق Shizuku غير مثبت على الهاتف" else "Shizuku app is not installed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(AppleRadius.small),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (isRtl) "ربط" else "Connect",
                                style = AppleTypography.caption,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. Genuine Hardware Tuning Modules
        item {
            AppleSectionHeader(
                title = if (isRtl) "وحدات التحكم بالعتاد" else "Hardware Tuning Modules",
                theme = theme
            )
        }

        item {
            AppleGroupedCard(theme = theme) {
                // Touch Polling & Pointer Speed
                EngineModuleRow(
                    title = if (isRtl) "سرعة المؤشر واستجابة اللمس" else "Touch Polling & Pointer Speed",
                    subtitle = if (isRtl) "رفع سرعة المؤشر إلى 7 لتقليل تأخير استجابة حركات التصويب" else "Set pointer speed to maximum (7) for snappier touch response",
                    icon = Icons.Default.TouchApp,
                    iconTint = theme.accent,
                    isActive = isOptimized && verificationItems["pointer_speed"]?.status?.name?.contains("ACTIVE") == true,
                    theme = theme
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Window & Transition Animations
                EngineModuleRow(
                    title = if (isRtl) "تعطيل تأخير حركات النوافذ" else "Window & Transition Render Scales",
                    subtitle = if (isRtl) "إلغاء مدد حركات النوافذ لتسريع استجابة الواجهة" else "Disable window animations to eliminate UI latency before match",
                    icon = Icons.Default.Layers,
                    iconTint = theme.accentBlue,
                    isActive = isOptimized && verificationItems["window_animation"]?.status?.name?.contains("ACTIVE") == true,
                    theme = theme
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Display Peak Refresh Rate
                EngineModuleRow(
                    title = if (isRtl) "تثبيت أعلى معدل تحديث للشاشة" else "Peak Display Refresh Rate Lock",
                    subtitle = if (isRtl) "تثبيت معدل التحديث عند أعلى تردد للشاشة لمنع هبوط السلاسة" else "Lock peak and minimum refresh rates to eliminate switching stutter",
                    icon = Icons.Default.Speed,
                    iconTint = theme.accent,
                    isActive = isOptimized && verificationItems["peak_refresh_rate"]?.status?.name?.contains("ACTIVE") == true,
                    theme = theme
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Doze Whitelist
                EngineModuleRow(
                    title = if (isRtl) "استثناء اللعبة من قيود توفير الطاقة" else "Battery Optimization Exemption",
                    subtitle = if (isRtl) "إضافة اللعبة إلى القائمة البيضاء لنظام Doze لمنع تقييدها في الخلفية" else "Whitelist active game package from Doze standby restrictions",
                    icon = Icons.Default.BatteryChargingFull,
                    iconTint = theme.warning,
                    isActive = isOptimized && verificationItems["doze_whitelist"]?.status?.name?.contains("ACTIVE") == true,
                    theme = theme
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // RAM Trim
                EngineModuleRow(
                    title = if (isRtl) "تفريغ الذاكرة المؤقتة للتطبيقات الخاملة" else "Background Memory Cache Trim",
                    subtitle = if (isRtl) "إنهاء العمليات الخاملة لتخصيص سعة الذاكرة للعبة النشطة" else "Trim cached background processes to maximize available RAM",
                    icon = Icons.Default.Memory,
                    iconTint = theme.accentBlue,
                    isActive = isOptimized,
                    theme = theme
                )
            }
        }

        // Safety Warnings
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OptimizationWarningCard(
                    profile = SafetyCatalog.CPU_GOVERNOR,
                    theme = theme,
                    isRtl = isRtl,
                    currentThermalLevel = thermalSnapshot.level
                )
                OptimizationWarningCard(
                    profile = SafetyCatalog.REFRESH_RATE,
                    theme = theme,
                    isRtl = isRtl,
                    currentThermalLevel = thermalSnapshot.level
                )
            }
        }

        // 4. Primary Execute Tuning Button
        item {
            Button(
                onClick = {
                    viewModel.triggerMainBoost(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(AppleRadius.large),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOptimized) theme.accent else theme.accentBlue
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isOptimized) Icons.Default.Check else Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isOptimizing) {
                            if (isRtl) "جاري التحقق والتطبيق..." else "Applying System Verifications..."
                        } else if (isOptimized) {
                            if (isRtl) "إعادة تطبيق التحسينات" else "Re-Apply System Tweaks"
                        } else {
                            if (isRtl) "تطبيق إعدادات الأداء" else "Apply Performance Tuning"
                        },
                        style = AppleTypography.titleSmall,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5. Restore System Defaults Button
        item {
            OutlinedButton(
                onClick = {
                    viewModel.showWarning(
                        title = if (isRtl) "استعادة الإعدادات الأصلية" else "Restore System Defaults",
                        description = if (isRtl)
                            "سيتم إلغاء كافة التعديلات واستعادة القيم الأصلية المسجلة مسبقاً في لقطة الجلسة. متابعة؟"
                        else
                            "This will revert all applied settings back to the original baseline captured before the session. Proceed?",
                        riskLevel = "Low",
                        onConfirm = { viewModel.resetSystemDefaults(context) }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(AppleRadius.medium),
                border = BorderStroke(1.dp, theme.divider)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = theme.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isRtl) "استعادة القيم الأصلية للجهاز" else "Restore Device Defaults",
                        style = AppleTypography.footnote,
                        color = theme.textSecondary
                    )
                }
            }
        }

        // 6. Real Execution Audit Logs
        item {
            AppleSectionHeader(
                title = if (isRtl) "سجل التنفيذ الحقيقي (Live Audit)" else "Execution Audit Logs",
                theme = theme,
                action = {
                    TextButton(onClick = { viewModel.clearLogs() }) {
                        Text(
                            text = if (isRtl) "مسح" else "Clear",
                            style = AppleTypography.caption,
                            color = theme.accent
                        )
                    }
                }
            )
        }

        if (logsList.isEmpty()) {
            item {
                AppleGlassSurface(
                    level = GlassLevel.Subtle,
                    theme = theme,
                    cornerRadius = AppleRadius.medium
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppleSpacing.l),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRtl) "لا توجد سجلات بعد. اضغط 'تطبيق إعدادات الأداء' لبدء التنفيذ والتحقق."
                            else "No execution logs yet. Tap 'Apply Performance Tuning' to execute and verify.",
                            style = AppleTypography.footnote,
                            color = theme.textTertiary
                        )
                    }
                }
            }
        } else {
            items(logsList.take(6)) { log ->
                LogItemRow(log = log, theme = theme, isRtl = isRtl)
            }
        }
    }
}

@Composable
fun EngineModuleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    isActive: Boolean,
    theme: AppleThemeTokens
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpacing.m, vertical = AppleSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.m)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(AppleRadius.small))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppleTypography.body,
                color = theme.textPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = AppleTypography.footnote,
                color = theme.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AppleRadius.pill))
                .background(
                    if (isActive) theme.accent.copy(alpha = 0.15f)
                    else theme.glassSubtle
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isActive) "مفعل" else "جاهز",
                style = AppleTypography.caption,
                color = if (isActive) theme.accent else theme.textTertiary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LogItemRow(
    log: OptimizationLog,
    theme: AppleThemeTokens,
    isRtl: Boolean
) {
    AppleGlassSurface(
        level = GlassLevel.Subtle,
        theme = theme,
        cornerRadius = AppleRadius.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppleSpacing.m, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppleSpacing.s)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (log.status == "SUCCESS") theme.accent else theme.danger,
                        CircleShape
                    )
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.commandName,
                        style = AppleTypography.footnote,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = log.status,
                        style = AppleTypography.caption,
                        color = if (log.status == "SUCCESS") theme.accent else theme.danger,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = log.responseMsg,
                    style = AppleTypography.caption,
                    color = theme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
