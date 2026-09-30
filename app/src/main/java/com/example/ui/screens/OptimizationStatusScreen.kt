package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.diagnostics.DiagnosticsSummary
import com.example.engine.diagnostics.FeatureTestResult
import com.example.engine.diagnostics.TestStatus
import com.example.ui.BoosterViewModel
import com.example.ui.designsystem.*

@Composable
fun AppleOptimizationStatusScreen(
    viewModel: BoosterViewModel,
    theme: AppleThemeTokens,
    isRtl: Boolean
) {
    val context = LocalContext.current
    val testResults by viewModel.featureTestResults.collectAsState()
    val isTestingAll by viewModel.isTestingAll.collectAsState()
    val filterMode by viewModel.diagnosticsFilter.collectAsState()

    val summary = remember(testResults) {
        viewModel.getDiagnosticsSummary()
    }

    // Filter list based on selected filter
    val filteredResults = remember(testResults, filterMode) {
        when (filterMode) {
            "failed_only" -> testResults.filter { it.status == TestStatus.FAILED }
            "permission_only" -> testResults.filter { it.status == TestStatus.PERMISSION_REQUIRED }
            "unsupported_only" -> testResults.filter { it.status == TestStatus.UNSUPPORTED }
            else -> testResults
        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRtl) "تقرير الحقيقة (Reality Report)" else "Reality Report",
                        style = AppleTypography.displayMedium,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isRtl) "فحص وتشخيص حي لكل ميزة وأمر ينفذه التطبيق للتأكد من استجابة النظام الحقيقية وإظهار النتائج ورموز الأخطاء بدقة"
                        else "In-app self-test verifying every feature, exact shell command, exit code, and live system response",
                        style = AppleTypography.footnote,
                        color = theme.textSecondary
                    )
                }
            }
        }

        // 2. Action Bar: "إعادة اختبار الكل" + "نسخ الكل"
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.s)
            ) {
                // Re-test All Button
                Button(
                    onClick = { viewModel.runAllFeatureDiagnostics() },
                    enabled = !isTestingAll,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(46.dp),
                    shape = RoundedCornerShape(AppleRadius.medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accent,
                        disabledContainerColor = theme.accent.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isTestingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (isTestingAll) {
                                if (isRtl) "جاري الفحص..." else "Testing..."
                            } else {
                                if (isRtl) "إعادة اختبار الكل" else "Re-test All"
                            },
                            style = AppleTypography.titleSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Copy All Button
                Button(
                    onClick = {
                        val report = viewModel.getFormattedExportReport()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Game Turbo Diagnostics", report)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(
                            context,
                            if (isRtl) "تم نسخ تقرير الفحص الكامل إلى الحافظة بنجاح" else "Full diagnostic report copied to clipboard",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(AppleRadius.medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.surfaceElevated,
                        contentColor = theme.textPrimary
                    ),
                    border = BorderStroke(1.dp, theme.divider)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = theme.textPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = if (isRtl) "نسخ الكل" else "Copy All",
                            style = AppleTypography.titleSmall,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Simple Real Counters (عداد بسيط)
        item {
            AppleGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                level = GlassLevel.Elevated,
                theme = theme,
                cornerRadius = AppleRadius.large
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppleSpacing.m, horizontal = AppleSpacing.s),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CounterPill(
                        label = if (isRtl) "نجح" else "Success",
                        count = summary.successCount,
                        color = theme.accent,
                        icon = Icons.Default.CheckCircle,
                        theme = theme
                    )
                    VerticalDivider(modifier = Modifier.height(32.dp), color = theme.divider, thickness = 0.5.dp)

                    CounterPill(
                        label = if (isRtl) "فشل" else "Failed",
                        count = summary.failedCount,
                        color = theme.danger,
                        icon = Icons.Default.Cancel,
                        theme = theme
                    )
                    VerticalDivider(modifier = Modifier.height(32.dp), color = theme.divider, thickness = 0.5.dp)

                    CounterPill(
                        label = if (isRtl) "غير مدعوم" else "Unsupported",
                        count = summary.unsupportedCount,
                        color = theme.warning,
                        icon = Icons.Default.HelpOutline,
                        theme = theme
                    )
                    VerticalDivider(modifier = Modifier.height(32.dp), color = theme.divider, thickness = 0.5.dp)

                    CounterPill(
                        label = if (isRtl) "يحتاج صلاحية" else "Permission",
                        count = summary.permissionRequiredCount,
                        color = theme.accentBlue,
                        icon = Icons.Default.Security,
                        theme = theme
                    )
                }
            }
        }

        // 4. Filters Segmented Control
        item {
            val filterOptions = listOf(
                "all" to (if (isRtl) "الكل (${testResults.size})" else "All (${testResults.size})"),
                "failed_only" to (if (isRtl) "الفاشلة (${summary.failedCount})" else "Failed (${summary.failedCount})"),
                "permission_only" to (if (isRtl) "الصلاحيات (${summary.permissionRequiredCount})" else "Perms (${summary.permissionRequiredCount})"),
                "unsupported_only" to (if (isRtl) "غير مدعومة (${summary.unsupportedCount})" else "Unsupported (${summary.unsupportedCount})")
            )

            AppleSegmentedControl(
                items = filterOptions.map { it.first },
                selectedItem = filterMode,
                onItemSelected = { viewModel.setDiagnosticsFilter(it) },
                labelProvider = { key -> filterOptions.find { it.first == key }?.second ?: key },
                theme = theme
            )
        }

        // 5. Tested Features List
        if (filteredResults.isEmpty()) {
            item {
                AppleGlassSurface(
                    level = GlassLevel.Subtle,
                    theme = theme,
                    cornerRadius = AppleRadius.medium
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppleSpacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRtl) "لا توجد عناصر مطابقة للفلتر المحدد." else "No features match the selected filter.",
                            style = AppleTypography.footnote,
                            color = theme.textTertiary
                        )
                    }
                }
            }
        } else {
            items(filteredResults, key = { it.id }) { item ->
                FeatureDiagnosticCard(
                    item = item,
                    theme = theme,
                    isRtl = isRtl,
                    onRetry = { viewModel.retryFeatureDiagnostic(item.id) },
                    onCopyError = {
                        val errorReport = viewModel.getSingleErrorReport(item)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Feature Error", errorReport))
                        Toast.makeText(
                            context,
                            if (isRtl) "تم نسخ تفاصيل الخطأ للحافظة" else "Error details copied to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
}

@Composable
fun CounterPill(
    label: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: AppleThemeTokens
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "$count",
                style = AppleTypography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            style = AppleTypography.caption,
            color = theme.textTertiary,
            fontSize = 10.sp
        )
    }
}

@Composable
fun FeatureDiagnosticCard(
    item: FeatureTestResult,
    theme: AppleThemeTokens,
    isRtl: Boolean,
    onRetry: () -> Unit,
    onCopyError: () -> Unit
) {
    var expanded by remember { mutableStateOf(item.status == TestStatus.FAILED || item.status == TestStatus.PERMISSION_REQUIRED) }

    val statusColor = when (item.status) {
        TestStatus.SUCCESS -> theme.accent
        TestStatus.FAILED -> theme.danger
        TestStatus.UNSUPPORTED -> theme.warning
        TestStatus.PERMISSION_REQUIRED -> theme.accentBlue
        TestStatus.NOT_TESTED -> theme.textTertiary
    }

    val statusBadgeText = if (isRtl) item.status.labelAr else item.status.labelEn

    AppleGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        level = GlassLevel.Standard,
        theme = theme,
        cornerRadius = AppleRadius.large,
        glowColor = if (item.status == TestStatus.FAILED) theme.danger.copy(alpha = 0.08f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRtl) item.nameAr else item.nameEn,
                        style = AppleTypography.titleSmall,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isRtl) item.nameEn else item.nameAr,
                        style = AppleTypography.caption,
                        color = theme.textTertiary,
                        fontSize = 11.sp
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppleRadius.pill))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(0.5.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(AppleRadius.pill))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (item.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = statusColor,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape)
                            )
                        }
                        Text(
                            text = if (item.isTesting) (if (isRtl) "جاري الفحص..." else "Testing...") else statusBadgeText,
                            style = AppleTypography.caption,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Quick Execution / Actual Result Preview
            if (item.status == TestStatus.SUCCESS) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppleRadius.small))
                        .background(theme.glassSubtle)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isRtl) "النتيجة الفعلية المؤكدة:" else "Verified Actual Result:",
                        style = AppleTypography.caption,
                        color = theme.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = item.actualResult,
                        style = AppleTypography.footnote,
                        color = theme.textPrimary
                    )
                    if (item.executedCommand != "—") {
                        Text(
                            text = (if (isRtl) "الأمر المستخدم: " else "Executed Command: ") + item.executedCommand,
                            style = AppleTypography.caption,
                            color = theme.textTertiary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Action Buttons: "إعادة المحاولة" + "نسخ الخطأ" (if failed) + Expand/Collapse
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Retry Button
                    Button(
                        onClick = onRetry,
                        enabled = !item.isTesting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.surfaceElevated,
                            contentColor = theme.textPrimary
                        ),
                        border = BorderStroke(1.dp, theme.divider),
                        shape = RoundedCornerShape(AppleRadius.small),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = theme.textPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isRtl) "إعادة المحاولة" else "Retry",
                                style = AppleTypography.caption,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Copy Error Button (if failed or permission required)
                    if (item.status == TestStatus.FAILED || item.status == TestStatus.PERMISSION_REQUIRED) {
                        Button(
                            onClick = onCopyError,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.danger.copy(alpha = 0.12f),
                                contentColor = theme.danger
                            ),
                            border = BorderStroke(1.dp, theme.danger.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(AppleRadius.small),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = theme.danger,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isRtl) "نسخ الخطأ" else "Copy Error",
                                    style = AppleTypography.caption,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Expand / Collapse Details Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppleRadius.small))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (expanded) (if (isRtl) "إخفاء التفاصيل" else "Hide Details")
                        else (if (isRtl) "عرض التفاصيل" else "View Details"),
                        style = AppleTypography.caption,
                        color = theme.accentBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = theme.accentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Comprehensive Diagnostic Details (Shown when expanded)
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppleRadius.medium))
                        .background(theme.surfaceElevated)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Executed Command
                    DiagnosticDetailItem(
                        label = if (isRtl) "الأمر المنفذ" else "Executed Command",
                        value = item.executedCommand,
                        theme = theme,
                        isCode = true
                    )

                    // Expected vs Actual
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            DiagnosticDetailItem(
                                label = if (isRtl) "النتيجة المتوقعة" else "Expected Result",
                                value = item.expectedResult,
                                theme = theme
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DiagnosticDetailItem(
                                label = if (isRtl) "النتيجة الفعلية" else "Actual Result",
                                value = item.actualResult,
                                theme = theme,
                                valueColor = if (item.status == TestStatus.SUCCESS) theme.accent else theme.textPrimary
                            )
                        }
                    }

                    // Error Message & Code (if failed)
                    if (item.errorMessage != "—") {
                        DiagnosticDetailItem(
                            label = if (isRtl) "رسالة الخطأ" else "Error Message",
                            value = item.errorMessage,
                            theme = theme,
                            valueColor = theme.danger
                        )
                    }

                    if (item.errorCode != "—") {
                        DiagnosticDetailItem(
                            label = if (isRtl) "كود الخطأ" else "Error Code",
                            value = item.errorCode,
                            theme = theme,
                            isCode = true
                        )
                    }

                    // Failure Cause
                    if (item.failureCause != "—") {
                        DiagnosticDetailItem(
                            label = if (isRtl) "سبب الفشل" else "Failure Cause",
                            value = item.failureCause,
                            theme = theme
                        )
                    }

                    // Unsupported Reason & Alternative
                    if (item.unsupportedReason != "—") {
                        DiagnosticDetailItem(
                            label = if (isRtl) "سبب عدم الدعم" else "Unsupported Reason",
                            value = item.unsupportedReason,
                            theme = theme,
                            valueColor = theme.warning
                        )
                    }

                    if (item.suggestedAlternative != "—") {
                        DiagnosticDetailItem(
                            label = if (isRtl) "البديل المقترح" else "Suggested Alternative",
                            value = item.suggestedAlternative,
                            theme = theme
                        )
                    }

                    // Required Permission
                    if (item.requiredPermission != "—") {
                        DiagnosticDetailItem(
                            label = if (isRtl) "الصلاحية المطلوبة" else "Required Permission",
                            value = item.requiredPermission,
                            theme = theme,
                            valueColor = theme.accentBlue
                        )
                    }

                    // System metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            DiagnosticDetailItem(
                                label = if (isRtl) "إصدار Android" else "Android Version",
                                value = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                                theme = theme
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DiagnosticDetailItem(
                                label = if (isRtl) "الشركة المصنعة" else "Manufacturer",
                                value = "${Build.MANUFACTURER} (${Build.MODEL})",
                                theme = theme
                            )
                        }
                    }

                    // Fix Steps
                    if (item.fixSteps != "—") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppleRadius.small))
                                .background(theme.glassSubtle)
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (isRtl) "طريقة الإصلاح الموصى بها:" else "Recommended Fix Steps:",
                                    style = AppleTypography.caption,
                                    color = theme.accentBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = item.fixSteps,
                                    style = AppleTypography.footnote,
                                    color = theme.textPrimary,
                                    lineHeight = 16.sp
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
fun DiagnosticDetailItem(
    label: String,
    value: String,
    theme: AppleThemeTokens,
    valueColor: Color? = null,
    isCode: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = AppleTypography.caption,
            color = theme.textTertiary,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = if (isCode) AppleTypography.caption.copy(fontFamily = FontFamily.Monospace) else AppleTypography.footnote,
            color = valueColor ?: theme.textPrimary,
            lineHeight = 16.sp
        )
    }
}
