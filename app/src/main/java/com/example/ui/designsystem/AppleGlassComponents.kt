package com.example.ui.designsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

enum class GlassLevel {
    Subtle,
    Standard,
    Elevated
}

@Composable
fun AppleGlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Standard,
    theme: AppleThemeTokens,
    cornerRadius: Dp = AppleRadius.large,
    glowColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor = when (level) {
        GlassLevel.Subtle -> theme.glassSubtle
        GlassLevel.Standard -> theme.glassStandard
        GlassLevel.Elevated -> theme.glassElevated
    }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                brush = theme.glassBorderBrush,
                shape = shape
            )
            .drawBehind {
                if (glowColor != Color.Transparent) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.12f), Color.Transparent),
                            radius = size.width * 0.9f,
                            center = Offset(size.width * 0.5f, 0f)
                        )
                    )
                }
            }
            .let { base ->
                if (onClick != null) {
                    base.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick
                    )
                } else base
            }
    ) {
        content()
    }
}

@Composable
fun AppleHeroStatus(
    isOptimized: Boolean,
    isOptimizing: Boolean,
    profileName: String,
    theme: AppleThemeTokens,
    isRtl: Boolean,
    shizukuActive: Boolean,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isOptimized) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val activeGlow = if (isOptimized) theme.accent else theme.accentBlue

    AppleGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        level = GlassLevel.Elevated,
        theme = theme,
        cornerRadius = AppleRadius.extraLarge,
        glowColor = if (isOptimized) activeGlow else Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Capsule
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppleRadius.pill))
                    .background(
                        if (isOptimized) theme.accent.copy(alpha = 0.14f)
                        else theme.glassSubtle
                    )
                    .border(
                        1.dp,
                        if (isOptimized) theme.accent.copy(alpha = 0.35f)
                        else theme.divider,
                        RoundedCornerShape(AppleRadius.pill)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isOptimizing) theme.warning
                            else if (isOptimized) theme.accent
                            else theme.textTertiary,
                            CircleShape
                        )
                )
                Text(
                    text = if (isOptimizing) {
                        if (isRtl) "جاري التحقق والتطبيق..." else "Applying System Verifications..."
                    } else if (isOptimized) {
                        if (isRtl) "Game Turbo قيد التشغيل ومؤكد" else "Game Turbo Active & Verified"
                    } else {
                        if (isRtl) "Game Turbo في وضع الاستعداد" else "Game Turbo Standby"
                    },
                    style = AppleTypography.caption,
                    color = if (isOptimized) theme.accent else theme.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Central Interactive Dial
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    (if (isOptimized) theme.accent else theme.accentBlue).copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                colors = if (isOptimized) listOf(theme.accent, theme.accentBlue, theme.accent)
                                else listOf(theme.divider, theme.accentBlue.copy(alpha = 0.5f), theme.divider)
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOptimized) theme.accent
                            else theme.surfaceElevated
                        )
                        .border(
                            1.dp,
                            if (isOptimized) Color.White.copy(alpha = 0.4f) else theme.divider,
                            CircleShape
                        )
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isOptimized) Icons.Default.Bolt else Icons.Default.PowerSettingsNew,
                            contentDescription = "Turbo",
                            tint = if (isOptimized) Color.Black else theme.accentBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isOptimized) {
                                if (isRtl) "نشط" else "ACTIVE"
                            } else {
                                if (isRtl) "تشغيل" else "START"
                            },
                            style = AppleTypography.caption,
                            color = if (isOptimized) Color.Black else theme.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Sub-info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isRtl) "الملف النشط" else "Active Profile",
                        style = AppleTypography.footnote,
                        color = theme.textTertiary
                    )
                    Text(
                        text = profileName,
                        style = AppleTypography.titleSmall,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppleRadius.small))
                        .background(theme.glassSubtle)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = if (shizukuActive) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (shizukuActive) theme.accent else theme.textTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (shizukuActive) {
                            if (isRtl) "Shizuku متصل" else "Shizuku Active"
                        } else {
                            if (isRtl) "Shizuku غير متصل" else "Shizuku Required"
                        },
                        style = AppleTypography.caption,
                        color = if (shizukuActive) theme.accent else theme.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun AppleMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String = "",
    statusText: String? = null,
    statusColor: Color? = null,
    icon: ImageVector,
    theme: AppleThemeTokens,
    onClick: (() -> Unit)? = null
) {
    AppleGlassSurface(
        modifier = modifier,
        level = GlassLevel.Standard,
        theme = theme,
        cornerRadius = AppleRadius.large,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppleSpacing.m),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = AppleTypography.footnote,
                    color = theme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor ?: theme.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = AppleTypography.displayMedium,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = AppleTypography.callout,
                        color = theme.textTertiary,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            if (statusText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor ?: theme.accent, CircleShape)
                    )
                    Text(
                        text = statusText,
                        style = AppleTypography.caption,
                        color = statusColor ?: theme.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun <T> AppleSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    theme: AppleThemeTokens,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppleRadius.medium))
            .background(theme.glassSubtle)
            .border(1.dp, theme.divider, RoundedCornerShape(AppleRadius.medium))
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val label = labelProvider(item)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(AppleRadius.small))
                        .background(
                            if (isSelected) theme.surfaceElevated else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            brush = if (isSelected) theme.glassBorderBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                            shape = RoundedCornerShape(AppleRadius.small)
                        )
                        .clickable { onItemSelected(item) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = AppleTypography.caption,
                        color = if (isSelected) theme.textPrimary else theme.textSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun AppleSettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color,
    theme: AppleThemeTokens,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = AppleSpacing.m, vertical = AppleSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.m)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(AppleRadius.small))
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppleTypography.body,
                color = theme.textPrimary,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppleTypography.footnote,
                    color = theme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing()
    }
}

@Composable
fun AppleGroupedCard(
    theme: AppleThemeTokens,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppleGlassSurface(
        modifier = modifier.fillMaxWidth(),
        level = GlassLevel.Standard,
        theme = theme,
        cornerRadius = AppleRadius.large
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
fun AppleSectionHeader(
    title: String,
    theme: AppleThemeTokens,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpacing.xs, vertical = AppleSpacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AppleTypography.titleMedium,
            color = theme.textPrimary,
            fontWeight = FontWeight.Bold
        )
        action?.invoke()
    }
}

@Composable
fun AppleFloatingTabBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    theme: AppleThemeTokens,
    isRtl: Boolean
) {
    val tabs = listOf(
        Triple("home", if (isRtl) "الرئيسية" else "Home", Icons.Default.Dashboard),
        Triple("performance", if (isRtl) "الأداء" else "Performance", Icons.Default.Speed),
        Triple("status", if (isRtl) "تقرير الحقيقة" else "Reality Report", Icons.Default.FactCheck),
        Triple("network", if (isRtl) "الشبكة" else "Network", Icons.Default.Wifi),
        Triple("thermal", if (isRtl) "الحرارة" else "Thermal", Icons.Default.Thermostat),
        Triple("games", if (isRtl) "الألعاب" else "Games", Icons.Default.SportsEsports),
        Triple("settings", if (isRtl) "الإعدادات" else "Settings", Icons.Default.Settings)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AppleRadius.extraLarge))
                .background(
                    if (theme.isDark) Color(0xD9101626) else Color(0xEEFFFFFF)
                )
                .border(
                    width = 1.dp,
                    brush = theme.glassBorderBrush,
                    shape = RoundedCornerShape(AppleRadius.extraLarge)
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (tabKey, label, icon) ->
                    val isSelected = currentTab == tabKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppleRadius.medium))
                            .background(
                                if (isSelected) theme.accent.copy(alpha = 0.16f) else Color.Transparent
                            )
                            .clickable { onTabSelected(tabKey) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) theme.accent else theme.textTertiary,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = label,
                                style = AppleTypography.caption,
                                color = if (isSelected) theme.textPrimary else theme.textTertiary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppleDangerousActionDialog(
    title: String,
    description: String,
    riskLevel: String,
    theme: AppleThemeTokens,
    isRtl: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(theme.danger.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = theme.danger,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = AppleTypography.titleMedium,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = (if (isRtl) "مستوى المخاطرة: " else "Risk Level: ") + riskLevel,
                            style = AppleTypography.caption,
                            color = theme.danger,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = description,
                    style = AppleTypography.body,
                    color = theme.textSecondary
                )
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
                            text = if (isRtl) "إلغاء" else "Cancel",
                            color = theme.textPrimary
                        )
                    }
                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.danger),
                        shape = RoundedCornerShape(AppleRadius.medium)
                    ) {
                        Text(
                            text = if (isRtl) "متابعة" else "Proceed",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
