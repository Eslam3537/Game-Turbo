package com.example.ui

import android.widget.Toast
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.designsystem.*
import com.example.util.PermissionManager

@Composable
fun PermissionCenterDialog(
    viewModel: BoosterViewModel,
    theme: AppleThemeTokens = getAppleTheme("dark"),
    onRequestAllPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val permissionStatus by viewModel.permissionsState.collectAsState()
    val isArabic by viewModel.selectedLanguage.collectAsState()
    val isRtl = isArabic == "ar"
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                AppleGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f),
                    level = GlassLevel.Elevated,
                    theme = theme,
                    cornerRadius = AppleRadius.extraLarge
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
                        item {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(theme.accentBlue.copy(alpha = 0.16f), CircleShape)
                                    .border(1.dp, theme.accentBlue.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Security",
                                    tint = theme.accentBlue,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isRtl) "مركز أذونات النظام الذكي" else "System Permissions Center",
                                color = theme.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isRtl)
                                    "يتطلب تطبيق Game Turbo الأذونات التالية لتفعيل مراقبة العتاد الحقيقية وتنفيذ أوامر Shell الموثقة."
                                else
                                    "Game Turbo requires the following verified permissions for hardware telemetry and privileged shell execution.",
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }

                        // Progress Card
                        item {
                            val grantedCount = permissionStatus.grantedCount
                            val totalPermissions = permissionStatus.totalRequired
                            val progress = (grantedCount.toFloat() / totalPermissions.toFloat()).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AppleRadius.large))
                                    .background(theme.glassSubtle)
                                    .border(1.dp, theme.divider, RoundedCornerShape(AppleRadius.large))
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isRtl) "حالة الأذونات الممنوحة" else "Active Permissions Status",
                                            color = theme.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "$grantedCount / $totalPermissions " + if (isRtl) "مكتمل" else "Granted",
                                            color = if (grantedCount == totalPermissions) theme.accent else theme.accentBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (grantedCount == totalPermissions) theme.accent else theme.accentBlue,
                                        trackColor = theme.surfaceElevated
                                    )
                                    if (permissionStatus.isFullyEmpowered) {
                                        Text(
                                            text = if (isRtl) "كافة الصلاحيات ممنوحة ومؤكدة! التطبيق يعمل بأقصى كفاءة."
                                            else "All permissions verified! System is fully empowered.",
                                            color = theme.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // Auto Grant CTA
                        item {
                            Button(
                                onClick = {
                                    onRequestAllPermissions()
                                    Toast.makeText(
                                        context,
                                        if (isRtl) "بدء طلب الصلاحيات..." else "Requesting permissions...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(AppleRadius.medium),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (isRtl) "منح الصلاحيات بالتتابع" else "Request Permissions Sequentially",
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 1. Shizuku Access
                        item {
                            PermissionItemCard(
                                icon = Icons.Default.Terminal,
                                title = if (isRtl) "صلاحيات Shizuku المتقدمة" else "Shizuku ADB System Access",
                                description = if (isRtl)
                                    "لتنفيذ أوامر Shell الحقيقية لتثبيت تردد الشاشة واستثناء Doze بدون روت."
                                else
                                    "To execute privileged Android shell commands for refresh rate locks and Doze exemptions.",
                                isGranted = permissionStatus.hasShizuku,
                                isRtl = isRtl,
                                theme = theme,
                                actionLabel = if (permissionStatus.isShizukuServiceRunning) {
                                    if (isRtl) "طلب الإذن" else "Grant"
                                } else {
                                    if (isRtl) "فتح Shizuku" else "Open Shizuku"
                                },
                                onGrant = {
                                    if (permissionStatus.isShizukuServiceRunning) {
                                        PermissionManager.requestShizukuPermission()
                                    } else {
                                        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        if (intent != null) {
                                            context.startActivity(intent)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                if (isRtl) "تطبيق Shizuku غير مثبت على الهاتف" else "Please install Shizuku to execute privileged shell commands",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }

                        // 2. Notifications
                        item {
                            PermissionItemCard(
                                icon = Icons.Default.NotificationsActive,
                                title = if (isRtl) "الإشعارات والتنبيهات" else "Notifications & Alerts",
                                description = if (isRtl)
                                    "لإرسال تنبيهات فورية عند حدوث ارتفاع حراري خطير."
                                else
                                    "To deliver instant alerts when critical thermal throttling is detected.",
                                isGranted = permissionStatus.hasNotification,
                                isRtl = isRtl,
                                theme = theme,
                                onGrant = { onRequestAllPermissions() }
                            )
                        }

                        // 3. Floating Overlay
                        item {
                            PermissionItemCard(
                                icon = Icons.Default.Layers,
                                title = if (isRtl) "الظهور فوق التطبيقات (Overlay)" else "Display Over Other Apps (Overlay)",
                                description = if (isRtl)
                                    "لرسم شاشة مراقبة معدل إطارات SurfaceFlinger والحرارة فوق نافذة اللعبة."
                                else
                                    "To float the real SurfaceFlinger FPS and battery temperature HUD over games.",
                                isGranted = permissionStatus.hasOverlay,
                                isRtl = isRtl,
                                theme = theme,
                                onGrant = { PermissionManager.openOverlaySettings(context) }
                            )
                        }

                        // 4. Usage Stats (Auto-Detect)
                        item {
                            PermissionItemCard(
                                icon = Icons.Default.QueryStats,
                                title = if (isRtl) "الوصول لبيانات الاستخدام (Usage Stats)" else "Usage Access (Game Auto-Detect)",
                                description = if (isRtl)
                                    "لرصد تشغيل اللعبة في الواجهة الأمامية عبر UsageStatsManager وتفعيل الجلسة تلقائياً."
                                else
                                    "To detect when a game enters the foreground and automatically trigger Game Turbo.",
                                isGranted = permissionStatus.hasUsageStats,
                                isRtl = isRtl,
                                theme = theme,
                                onGrant = { PermissionManager.openUsageAccessSettings(context) }
                            )
                        }

                        // 5. Gaming DND
                        item {
                            PermissionItemCard(
                                icon = Icons.Default.DoNotDisturbOn,
                                title = if (isRtl) "سياسة عدم الإزعاج (Gaming DND)" else "Do Not Disturb Gaming Policy",
                                description = if (isRtl)
                                    "لكتم الإشعارات المزعجة أثناء المباريات واستعادة الوضع الطبيعي عند الانتهاء."
                                else
                                    "To silence disturbing alerts during matches to prevent touch interruptions.",
                                isGranted = permissionStatus.hasDnd,
                                isRtl = isRtl,
                                theme = theme,
                                onGrant = { PermissionManager.openDndSettings(context) }
                            )
                        }

                        // Close button
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(AppleRadius.medium),
                                border = BorderStroke(1.dp, theme.divider)
                            ) {
                                Text(
                                    text = if (isRtl) "العودة إلى لوحة التحكم" else "Return to Dashboard",
                                    color = theme.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isRtl: Boolean,
    theme: AppleThemeTokens,
    actionLabel: String? = null,
    onGrant: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppleRadius.medium))
            .background(theme.glassSubtle)
            .border(
                1.dp,
                if (isGranted) theme.accent.copy(alpha = 0.35f) else theme.divider,
                RoundedCornerShape(AppleRadius.medium)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isGranted) theme.accent.copy(alpha = 0.15f) else theme.surfaceElevated,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) theme.accent else theme.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            if (isGranted) {
                Box(
                    modifier = Modifier
                        .background(theme.accent.copy(alpha = 0.15f), RoundedCornerShape(AppleRadius.pill))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRtl) "ممنوح ✓" else "Granted ✓",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onGrant,
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(AppleRadius.small),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accentBlue.copy(alpha = 0.2f),
                        contentColor = theme.accentBlue
                    ),
                    border = BorderStroke(1.dp, theme.accentBlue.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = actionLabel ?: if (isRtl) "تفعيل" else "Enable",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
