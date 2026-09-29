package com.example.ui.screens

import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BoosterViewModel
import com.example.ui.designsystem.*

@Composable
fun AppleNetworkScreen(
    viewModel: BoosterViewModel,
    theme: AppleThemeTokens,
    isRtl: Boolean
) {
    val context = LocalContext.current
    val currentPing by viewModel.currentPing.collectAsState()
    val pingMe by viewModel.pingMiddleEast.collectAsState()
    val pingEu by viewModel.pingEurope.collectAsState()
    val pingAsia by viewModel.pingAsia.collectAsState()
    val pingGlobal by viewModel.pingGlobal.collectAsState()

    val jitterMe by viewModel.jitterMe.collectAsState()
    val jitterEu by viewModel.jitterEu.collectAsState()
    val jitterAsia by viewModel.jitterAsia.collectAsState()
    val jitterGlobal by viewModel.jitterGlobal.collectAsState()

    val networkProfile by viewModel.networkEngine.networkProfile.collectAsState()
    val isNetOptimized by viewModel.isNetworkOptimized.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()
    val selectedDns by viewModel.selectedDns.collectAsState()
    val dnsBenchmarkResults by viewModel.dnsBenchmarkResults.collectAsState()

    val networkStatus = when {
        currentPing == null -> if (isRtl) "جاري الفحص..." else "Probing..."
        currentPing!! <= 50 -> if (isRtl) "استجابة ممتازة ومستقرة" else "Optimal & Stable"
        currentPing!! <= 90 -> if (isRtl) "استجابة متوسطة" else "Moderate Latency"
        else -> if (isRtl) "استجابة مرتفعة" else "High Latency"
    }

    val networkStatusColor = when {
        currentPing == null -> theme.textSecondary
        currentPing!! <= 50 -> theme.accent
        currentPing!! <= 90 -> theme.warning
        else -> theme.danger
    }

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
                    text = if (isRtl) "محرك استقرار الشبكة" else "Gaming Network Engine",
                    style = AppleTypography.displayMedium,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isRtl) "قياس زمن الاستجابة الحقيقي بمآخذ TCP، فحص التذبذب، واختبار DNS" else "Authentic TCP socket RTT measurement, jitter tracking & DNS benchmark",
                    style = AppleTypography.footnote,
                    color = theme.textSecondary
                )
            }
        }

        // 2. Primary Ping Hero Card
        item {
            AppleGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                level = GlassLevel.Elevated,
                theme = theme,
                cornerRadius = AppleRadius.large,
                glowColor = networkStatusColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppleSpacing.l),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRtl) "حالة الاتصال العامة" else "Global Network Status",
                            style = AppleTypography.titleSmall,
                            color = theme.textSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(networkStatusColor, CircleShape)
                            )
                            Text(
                                text = networkStatus,
                                style = AppleTypography.caption,
                                color = networkStatusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Large Ping Metric
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentPing?.toString() ?: "—",
                            style = AppleTypography.displayLarge,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ms RTT",
                            style = AppleTypography.body,
                            color = theme.textTertiary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // Packet loss & DNS row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isRtl) "فقدان الحزم (Packet Loss)" else "Packet Loss",
                                style = AppleTypography.footnote,
                                color = theme.textTertiary
                            )
                            val lossText = networkProfile.packetLossPercent?.let { String.format("%.1f%%", it) } ?: "0.0%"
                            Text(
                                text = lossText,
                                style = AppleTypography.titleSmall,
                                color = if ((networkProfile.packetLossPercent ?: 0.0) == 0.0) theme.accent else theme.warning,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isRtl) "مزود DNS المحدد" else "Selected DNS",
                                style = AppleTypography.footnote,
                                color = theme.textTertiary
                            )
                            val dnsLabel = when (selectedDns) {
                                "one.one.one.one" -> "Cloudflare (1.1.1.1)"
                                "dns.google" -> "Google (8.8.8.8)"
                                "dns.quad9.net" -> "Quad9"
                                "dns.adguard.com" -> "AdGuard"
                                else -> selectedDns
                            }
                            Text(
                                text = dnsLabel,
                                style = AppleTypography.titleSmall,
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. DNS Benchmark Action Button
        item {
            Button(
                onClick = {
                    viewModel.triggerNetworkOptimization(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(AppleRadius.large),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isNetOptimized) theme.accent else theme.accentBlue
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isNetOptimized) Icons.Default.Check else Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isOptimizing) {
                            if (isRtl) "جاري قياس استجابة DNS الحقيقية..." else "Benchmarking DNS Candidates..."
                        } else if (isNetOptimized) {
                            if (isRtl) "تم اختيار أسرع DNS تلقائياً ✓" else "Fastest DNS Verified ✓"
                        } else {
                            if (isRtl) "اختبار واختيار أسرع مزود DNS" else "Benchmark & Set Fastest DNS"
                        },
                        style = AppleTypography.titleSmall,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Real DNS Benchmark Results if available
        if (dnsBenchmarkResults.isNotEmpty()) {
            item {
                AppleSectionHeader(
                    title = if (isRtl) "نتائج اختبار DNS الحقيقية" else "DNS Benchmark Results",
                    theme = theme
                )
            }

            item {
                AppleGroupedCard(theme = theme) {
                    dnsBenchmarkResults.forEachIndexed { index, res ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppleSpacing.m, vertical = AppleSpacing.s),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = res.providerName,
                                    style = AppleTypography.body,
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = res.host,
                                    style = AppleTypography.footnote,
                                    color = theme.textTertiary
                                )
                            }
                            val latStr = res.latencyMs?.let { "$it ms" } ?: "Timeout"
                            val isFastest = res.host == networkProfile.fastestDnsHost
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppleRadius.pill))
                                    .background(if (isFastest) theme.accent.copy(alpha = 0.15f) else theme.glassSubtle)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = latStr + if (isFastest) " (الأسرع)" else "",
                                    style = AppleTypography.caption,
                                    color = if (isFastest) theme.accent else theme.textSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (index < dnsBenchmarkResults.size - 1) {
                            HorizontalDivider(color = theme.divider, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // 4. Regional Game Clusters (Real Socket Connect Latency)
        item {
            AppleSectionHeader(
                title = if (isRtl) "مراكز الخوادم الإقليمية (قياس TCP حقيقي)" else "Regional Game Server Clusters",
                theme = theme
            )
        }

        item {
            AppleGroupedCard(theme = theme) {
                // Middle East
                ServerPingRow(
                    name = if (isRtl) "الشرق الأوسط (البحرين / دبي)" else "Middle East (AWS Bahrain)",
                    ping = pingMe,
                    jitter = jitterMe,
                    isRecommended = true,
                    theme = theme,
                    isRtl = isRtl
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Europe
                ServerPingRow(
                    name = if (isRtl) "أوروبا (فرانكفورت)" else "Europe (AWS Frankfurt)",
                    ping = pingEu,
                    jitter = jitterEu,
                    isRecommended = false,
                    theme = theme,
                    isRtl = isRtl
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // Asia
                ServerPingRow(
                    name = if (isRtl) "آسيا (سنغافورة)" else "Asia (AWS Singapore)",
                    ping = pingAsia,
                    jitter = jitterAsia,
                    isRecommended = false,
                    theme = theme,
                    isRtl = isRtl
                )
                HorizontalDivider(color = theme.divider, thickness = 0.5.dp)

                // North America
                ServerPingRow(
                    name = if (isRtl) "أمريكا الشمالية (شرق الولايات المتحدة)" else "North America (AWS US East)",
                    ping = pingGlobal,
                    jitter = jitterGlobal,
                    isRecommended = false,
                    theme = theme,
                    isRtl = isRtl
                )
            }
        }

        // 5. Encrypted Gaming DNS Selector
        item {
            AppleSectionHeader(
                title = if (isRtl) "مزود DNS المشفر للألعاب" else "Encrypted Gaming DNS",
                theme = theme
            )
        }

        item {
            val dnsOptions = listOf("one.one.one.one", "dns.google", "dns.quad9.net", "dns.adguard.com")
            AppleSegmentedControl(
                items = dnsOptions,
                selectedItem = selectedDns,
                onItemSelected = { dns ->
                    viewModel.setDns(dns)
                },
                labelProvider = { host ->
                    when (host) {
                        "one.one.one.one" -> "Cloudflare"
                        "dns.google" -> "Google"
                        "dns.quad9.net" -> "Quad9"
                        "dns.adguard.com" -> "AdGuard"
                        else -> host
                    }
                },
                theme = theme
            )
        }

        // Safety Warning Card
        item {
            OptimizationWarningCard(
                profile = SafetyCatalog.PRIVATE_DNS_LOCK,
                theme = theme,
                isRtl = isRtl
            )
        }
    }
}

@Composable
fun ServerPingRow(
    name: String,
    ping: Int?,
    jitter: Int?,
    isRecommended: Boolean,
    theme: AppleThemeTokens,
    isRtl: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpacing.m, vertical = AppleSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = name,
                    style = AppleTypography.body,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                if (isRecommended) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppleRadius.pill))
                            .background(theme.accent.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isRtl) "الأقرب" else "RECOMMENDED",
                            style = AppleTypography.caption,
                            color = theme.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            val jitterStr = jitter?.let { "±$it ms" } ?: "—"
            Text(
                text = (if (isRtl) "التذبذب: " else "Jitter: ") + jitterStr,
                style = AppleTypography.footnote,
                color = theme.textTertiary
            )
        }

        val pingStr = ping?.let { "$it ms" } ?: "—"
        val pingColor = when {
            ping == null -> theme.textSecondary
            ping <= 45 -> theme.accent
            ping <= 90 -> theme.warning
            else -> theme.danger
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AppleRadius.pill))
                .background(pingColor.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = pingStr,
                style = AppleTypography.caption,
                color = pingColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
