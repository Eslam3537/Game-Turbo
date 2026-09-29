package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.window.Dialog
import com.example.ui.BoosterViewModel
import com.example.ui.designsystem.*

@Composable
fun AppleSettingsScreen(
    viewModel: BoosterViewModel,
    theme: AppleThemeTokens,
    isRtl: Boolean
) {
    val context = LocalContext.current
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val isArabic by viewModel.selectedLanguage.collectAsState()
    val overlayEnabled by viewModel.overlayEnabled.collectAsState()
    val autoDetectEnabled by viewModel.autoDetectEnabled.collectAsState()
    val dndEnabled by viewModel.dndEnabled.collectAsState()
    val permissionsStatus by viewModel.permissionsState.collectAsState()
    val shizukuActive by viewModel.shizukuActive.collectAsState()
    var showShizukuGuide by remember { mutableStateOf(false) }

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
                    text = if (isRtl) "الإعدادات" else "Settings",
                    style = AppleTypography.displayMedium,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isRtl) "تفضيلات التطبيق، أذونات النظام ومعلومات المطور" else "Preferences, system permissions & developer metadata",
                    style = AppleTypography.footnote,
                    color = theme.textSecondary
                )
            }
        }

        // 2. APPEARANCE & LOCALIZATION
        item {
            AppleSectionHeader(
                title = if (isRtl) "المظهر واللغة" else "General & Appearance",
                theme = theme
            )
        }

        item {
            AppleGroupedCard(theme = theme) {
                // Theme Mode Selector
                AppleSettingsRow(
                    title = if (isRtl) "المظهر (Theme)" else "Appearance",
                    subtitle = if (isRtl) "التبديل بين النمط الداكن والفاتح" else "Dark or Light theme mode",
                    icon = Icons.Default.Palette,
                    iconTint = theme.accentIndigo,
                    theme = theme,
                    trailing = {
                        val modes = listOf("dark", "light")
                        AppleSegmentedControl(
                            items = modes,
                            selectedItem = currentThemeMode,
                            onItemSelected = { viewModel.setThemeMode(it) },
                            labelProvider = { m ->
                                if (m == "dark") (if (isRtl) "داكن" else "Dark")
                                else (if (isRtl) "فاتح" else "Light")
                            },
                            theme = theme,
                            modifier = Modifier.width(130.dp)
                        )
                    }
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Language Switch
                AppleSettingsRow(
                    title = if (isRtl) "لغة الواجهة (Language)" else "Interface Language",
                    subtitle = if (isRtl) "العربية (RTL) / الإنجليزية (LTR)" else "Arabic (RTL) / English (LTR)",
                    icon = Icons.Default.Language,
                    iconTint = theme.accentBlue,
                    theme = theme,
                    trailing = {
                        val languages = listOf("ar", "en")
                        AppleSegmentedControl(
                            items = languages,
                            selectedItem = isArabic,
                            onItemSelected = { viewModel.setLanguage(it) },
                            labelProvider = { l -> if (l == "ar") "عربي" else "EN" },
                            theme = theme,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                )
            }
        }

        // 3. GAMING & SYSTEM CONTROLS
        item {
            AppleSectionHeader(
                title = if (isRtl) "تفضيلات بيئة اللعب" else "Gaming Preferences",
                theme = theme
            )
        }

        item {
            AppleGroupedCard(theme = theme) {
                // Floating Overlay Monitor
                AppleSettingsRow(
                    title = if (isRtl) "شاشة المراقبة العائمة (Overlay)" else "Floating Telemetry HUD",
                    subtitle = if (isRtl) "عرض معدل إطارات SurfaceFlinger والحرارة فوق اللعبة" else "Render real-time SurfaceFlinger FPS & temp overlay",
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

                // Auto Game Detection
                AppleSettingsRow(
                    title = if (isRtl) "الكشف التلقائي عن تشغيل اللعبة" else "Foreground Game Auto-Detect",
                    subtitle = if (isRtl) "تفعيل الجلسة تلقائياً عبر UsageStatsManager عند تشغيل أي لعبة مسجلة" else "Automatically activate booster when registered game is in foreground",
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
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Gaming DND
                AppleSettingsRow(
                    title = if (isRtl) "كتم الإشعارات أثناء اللعب (Gaming DND)" else "Gaming Do Not Disturb",
                    subtitle = if (isRtl) "تحويل وضع عدم الإزعاج إلى الأولوية أثناء المباراة واستعادته بعدها" else "Silence alerts during matches and restore filter afterwards",
                    icon = Icons.Default.DoNotDisturbOn,
                    iconTint = theme.danger,
                    theme = theme,
                    trailing = {
                        Switch(
                            checked = dndEnabled,
                            onCheckedChange = { viewModel.setDndEnabled(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = theme.accent)
                        )
                    }
                )
            }
        }

        // 4. PERMISSIONS & SHIZUKU SYSTEM
        item {
            AppleSectionHeader(
                title = if (isRtl) "الأذونات وصلاحيات النظام" else "System & Authorization",
                theme = theme
            )
        }

        item {
            AppleGroupedCard(theme = theme) {
                // Permission Center Row
                AppleSettingsRow(
                    title = if (isRtl) "مركز الأذونات الذكي" else "Smart Permissions Center",
                    subtitle = if (permissionsStatus.isFullyEmpowered) {
                        if (isRtl) "كافة الأذونات ممنوحة وتعمل بشكل كامل" else "All permissions active and verified"
                    } else {
                        if (isRtl) "تم منح ${permissionsStatus.grantedCount} من ${permissionsStatus.totalRequired} أذونات"
                        else "${permissionsStatus.grantedCount} of ${permissionsStatus.totalRequired} permissions granted"
                    },
                    icon = Icons.Default.Security,
                    iconTint = if (permissionsStatus.isFullyEmpowered) theme.accent else theme.accentBlue,
                    theme = theme,
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = theme.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { viewModel.setPermissionCenterVisible(true) }
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Shizuku Guide & Status
                AppleSettingsRow(
                    title = if (isRtl) "دليل ربط Shizuku والتصحيح اللاسلكي" else "Shizuku Wireless ADB Guide",
                    subtitle = if (shizukuActive) {
                        if (isRtl) "الخدمة متصلة ومصرح لها بالعمل المباشر" else "Connected with elevated shell authority"
                    } else {
                        if (isRtl) "اضغط لعرض خطوات الربط بالتفصيل" else "Tap for step-by-step pairing guide"
                    },
                    icon = Icons.Default.Terminal,
                    iconTint = if (shizukuActive) theme.accent else theme.warning,
                    theme = theme,
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = theme.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { showShizukuGuide = true }
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Restore System Defaults
                AppleSettingsRow(
                    title = if (isRtl) "استعادة الإعدادات الأصلية للجهاز" else "Restore Device Defaults",
                    subtitle = if (isRtl) "استعادة كافة المتغيرات إلى قيم المصنع المسجلة" else "Revert all modified variables to original snapshot baseline",
                    icon = Icons.Default.Restore,
                    iconTint = theme.textSecondary,
                    theme = theme,
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = theme.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        viewModel.showWarning(
                            title = if (isRtl) "استعادة الإعدادات الأصلية" else "Restore System Defaults",
                            description = if (isRtl)
                                "سيتم استعادة كافة متغيرات النظام المسجلة في لقطات الجلسة وإعادتها لوضع المصنع. هل ترغب بالمتابعة؟"
                            else
                                "This will revert all snapshotted system parameters back to original baseline values. Proceed?",
                            riskLevel = "Low",
                            onConfirm = { viewModel.resetSystemDefaults(context) }
                        )
                    }
                )
            }
        }

        // 5. ABOUT & DEVELOPER PROFILE
        item {
            AppleSectionHeader(
                title = if (isRtl) "حول التطبيق والمطور" else "About & Developer",
                theme = theme
            )
        }

        item {
            AppleGroupedCard(theme = theme) {
                // Developer Profile Row
                AppleSettingsRow(
                    title = if (isRtl) "مهندس البرمجيات الرئيسي" else "Lead Software Engineer",
                    subtitle = "Islam Ramadan Rabia (إسلام رمضان)",
                    icon = Icons.Default.Person,
                    iconTint = theme.accent,
                    theme = theme,
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppleRadius.pill))
                                .background(theme.accent.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "v2.5 Pro",
                                style = AppleTypography.caption,
                                color = theme.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // WhatsApp Contact
                AppleSettingsRow(
                    title = if (isRtl) "التواصل عبر WhatsApp" else "Contact via WhatsApp",
                    subtitle = "+201140922854",
                    icon = Icons.Default.Chat,
                    iconTint = Color(0xFF25D366),
                    theme = theme,
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = theme.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/201140922854")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                            Toast.makeText(context, "Cannot open WhatsApp link", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Facebook Profile
                AppleSettingsRow(
                    title = if (isRtl) "الصفحة الرسمية على Facebook" else "Official Facebook Profile",
                    subtitle = "islam.ramadan.rabia",
                    icon = Icons.Default.Public,
                    iconTint = Color(0xFF1877F2),
                    theme = theme,
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = theme.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/islam.ramadan.rabia")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                            Toast.makeText(context, "Cannot open Facebook link", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // 6. IN-APP UPDATES & VERSION DETAILS
        item {
            AppleSectionHeader(
                title = if (isRtl) "التحديثات وإصدار التطبيق" else "Updates & Version Details",
                theme = theme
            )
        }

        item {
            AppleGroupedCard(theme = theme) {
                AppleSettingsRow(
                    title = if (isRtl) "فحص التحديثات (In-App Update)" else "Check for Updates",
                    subtitle = if (isRtl) "الإصدار المثبت: v1.0.0 (كود: 1) • فحص رسمي عبر HTTPS وGoogle Play" else "Installed: v1.0.0 (Code: 1) • Verified via HTTPS & Play Store",
                    icon = Icons.Default.SystemUpdate,
                    iconTint = theme.accent,
                    theme = theme,
                    trailing = {
                        Text(
                            text = if (isRtl) "فحص الآن" else "Check",
                            color = theme.accent,
                            style = AppleTypography.footnote,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = {
                        Toast.makeText(context, if (isRtl) "جاري فحص خوادم التحديث..." else "Checking update servers...", Toast.LENGTH_SHORT).show()
                        com.example.engine.update.InAppUpdateManager.checkForUpdates(context, isUserInitiated = true)
                    }
                )
            }
        }
    }

    if (showShizukuGuide) {
        Dialog(onDismissRequest = { showShizukuGuide = false }) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(theme.accent.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = theme.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (isRtl) "دليل تشغيل وربط Shizuku" else "Shizuku Wireless ADB Guide",
                            style = AppleTypography.titleMedium,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isRtl)
                            "1. فعّل خيارات المطور وشغّل 'التصحيح اللاسلكي' (Wireless Debugging).\n\n" +
                            "2. افتح تطبيق Shizuku واختر 'الإقران' (Pairing) وأدخل الرمز المكون من 6 أرقام.\n\n" +
                            "3. اضغط 'بدء التشغيل' (Start) داخل تطبيق Shizuku.\n\n" +
                            "4. عد إلى تطبيق Game Turbo وامنح الصلاحية لتنفيذ التعديلات الحقيقية بأمان وبدون روت."
                        else
                            "1. Enable Developer Options and turn on 'Wireless Debugging'.\n\n" +
                            "2. Open the Shizuku app, select 'Pairing' and enter the 6-digit code.\n\n" +
                            "3. Tap 'Start' inside Shizuku to launch the privileged service.\n\n" +
                            "4. Return to Game Turbo to grant authorization and execute verified system optimizations.",
                        style = AppleTypography.footnote,
                        color = theme.textSecondary,
                        lineHeight = 18.sp
                    )
                    Button(
                        onClick = { showShizukuGuide = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(AppleRadius.medium)
                    ) {
                        Text(
                            text = if (isRtl) "فهمت ذلك" else "Got It",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
