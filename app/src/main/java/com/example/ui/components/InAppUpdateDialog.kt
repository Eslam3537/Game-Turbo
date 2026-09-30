package com.example.ui.components

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.engine.update.DownloadStatus
import com.example.engine.update.InAppUpdateManager
import com.example.engine.update.UpdateDialogState
import com.example.ui.designsystem.AppleThemeTokens

@Composable
fun InAppUpdateDialog(
    state: UpdateDialogState.Visible,
    theme: AppleThemeTokens
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val payload = state.updatePayload
    val progress = state.progress

    val isDownloading = progress.status == DownloadStatus.DOWNLOADING ||
            progress.status == DownloadStatus.CONNECTING ||
            progress.status == DownloadStatus.VERIFYING_HASH
    val isCompleted = progress.status == DownloadStatus.DOWNLOAD_COMPLETED
    val isFailed = progress.status == DownloadStatus.FAILED

    Dialog(
        onDismissRequest = {
            if (!payload.forceUpdate && !isDownloading) {
                InAppUpdateManager.dismissDialog()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !payload.forceUpdate && !isDownloading,
            dismissOnClickOutside = !payload.forceUpdate && !isDownloading
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.surfaceElevated
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with App Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(theme.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update Icon",
                        tint = theme.accent,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "يوجد تحديث جديد",
                    color = theme.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Version comparison badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الحالي: v${state.currentVersionName}",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "  ➔  ",
                        color = theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = theme.accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "v${payload.versionName}",
                            color = theme.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                if (payload.fileSize > 0) {
                    val sizeMb = String.format(java.util.Locale.US, "%.1f MB", payload.fileSize / (1024.0 * 1024.0))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "حجم التحديث: $sizeMb ${if (payload.releaseDate.isNotBlank()) "• ${payload.releaseDate}" else ""}",
                        color = theme.textTertiary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Release notes list
                if (payload.releaseNotes.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.surface,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "أبرز التحسينات في هذا الإصدار:",
                                color = theme.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            payload.releaseNotes.forEach { note ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("• ", color = theme.accent, fontSize = 12.sp)
                                    Text(
                                        text = note,
                                        color = theme.textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Download Progress Display
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val progressFraction = progress.percentage / 100f
                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = theme.accent,
                            trackColor = theme.surface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val downloadedMb = String.format(java.util.Locale.US, "%.1f", progress.bytesDownloaded / (1024.0 * 1024.0))
                            val totalMb = if (progress.totalBytes > 0) {
                                String.format(java.util.Locale.US, "%.1f MB", progress.totalBytes / (1024.0 * 1024.0))
                            } else "—"

                            Text(
                                text = when (progress.status) {
                                    DownloadStatus.CONNECTING -> "جاري الاتصال..."
                                    DownloadStatus.VERIFYING_HASH -> "جاري التحقق من أمان الملف..."
                                    else -> "$downloadedMb / $totalMb"
                                },
                                color = theme.textSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${progress.percentage}%",
                                color = theme.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (isCompleted) {
                    Surface(
                        color = Color(0xFF30D158).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF30D158),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "اكتمل تحميل التحديث بنجاح! جاهز للتثبيت الآن.",
                                color = Color(0xFF30D158),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (isFailed) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = progress.errorMessage ?: "فشل تحميل التحديث",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!payload.forceUpdate && !isDownloading) {
                        OutlinedButton(
                            onClick = {
                                InAppUpdateManager.snoozeUpdate(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = theme.textSecondary
                            )
                        ) {
                            Text("لاحقًا", fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (isCompleted) {
                                InAppUpdateManager.installDownloadedUpdate(context)
                            } else if (activity != null) {
                                InAppUpdateManager.startUpdate(activity)
                            }
                        },
                        enabled = !isDownloading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCompleted) Color(0xFF30D158) else theme.accent
                        )
                    ) {
                        Text(
                            text = when {
                                isCompleted -> "إعادة تشغيل وتثبيت"
                                isFailed -> "إعادة المحاولة"
                                isDownloading -> "جاري التحميل..."
                                else -> "التحديث الآن"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
