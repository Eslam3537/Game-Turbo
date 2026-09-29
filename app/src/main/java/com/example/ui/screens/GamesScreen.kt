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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.AddedGame
import com.example.ui.BoosterViewModel
import com.example.ui.designsystem.*

@Composable
fun AppleGamesScreen(
    viewModel: BoosterViewModel,
    theme: AppleThemeTokens,
    isRtl: Boolean
) {
    val context = LocalContext.current
    val gamesList by viewModel.gamesList.collectAsState()
    val selectedPkg by viewModel.selectedGamePackage.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppleSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AppleSpacing.m),
        contentPadding = PaddingValues(top = AppleSpacing.m, bottom = 110.dp)
    ) {
        // 1. Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isRtl) "مكتبة الألعاب" else "Game Library",
                        style = AppleTypography.displayMedium,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isRtl) "تخصيص ملفات الأداء لكل لعبة وتشغيلها مباشرة" else "Per-game optimization profiles & direct launching",
                        style = AppleTypography.footnote,
                        color = theme.textSecondary
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(AppleRadius.medium),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isRtl) "إضافة لعبة" else "Add Game",
                            style = AppleTypography.caption,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Games List
        if (gamesList.isEmpty()) {
            item {
                AppleGlassSurface(
                    level = GlassLevel.Subtle,
                    theme = theme,
                    cornerRadius = AppleRadius.large
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppleSpacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppleSpacing.s)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = theme.textTertiary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (isRtl) "لا توجد ألعاب مضافة بعد" else "No games configured yet",
                                style = AppleTypography.titleSmall,
                                color = theme.textPrimary
                            )
                            Text(
                                text = if (isRtl) "اضغط على 'إضافة لعبة' لإدراج اسم الحزمة الخاصة باللعبة" else "Tap 'Add Game' to insert custom package name",
                                style = AppleTypography.footnote,
                                color = theme.textSecondary
                            )
                        }
                    }
                }
            }
        } else {
            items(gamesList) { game ->
                val isSelected = game.packageName == selectedPkg
                AppleGameCard(
                    game = game,
                    isSelected = isSelected,
                    theme = theme,
                    isRtl = isRtl,
                    onSelect = {
                        viewModel.selectGame(game.packageName)
                    },
                    onLaunch = {
                        viewModel.launchGame(context, game.packageName)
                    },
                    onDelete = {
                        viewModel.deleteGame(game)
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddGameModalDialog(
            theme = theme,
            isRtl = isRtl,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, pkg ->
                viewModel.addGame(name, pkg)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AppleGameCard(
    game: AddedGame,
    isSelected: Boolean,
    theme: AppleThemeTokens,
    isRtl: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onDelete: () -> Unit
) {
    AppleGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        level = if (isSelected) GlassLevel.Elevated else GlassLevel.Standard,
        theme = theme,
        cornerRadius = AppleRadius.large,
        glowColor = if (isSelected) theme.accent else Color.Transparent,
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AppleSpacing.s)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.m)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(AppleRadius.medium))
                        .background(theme.surfaceElevated)
                        .border(
                            1.dp,
                            if (isSelected) theme.accent.copy(alpha = 0.5f) else theme.divider,
                            RoundedCornerShape(AppleRadius.medium)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = game.name,
                        tint = if (isSelected) theme.accent else theme.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = game.name,
                            style = AppleTypography.titleSmall,
                            color = theme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppleRadius.pill))
                                    .background(theme.accent.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isRtl) "محددة" else "Active",
                                    style = AppleTypography.caption,
                                    color = theme.accent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = game.packageName,
                        style = AppleTypography.footnote,
                        color = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (game.isCustom) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = theme.danger,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onLaunch,
                        shape = RoundedCornerShape(AppleRadius.medium),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) theme.accent else theme.surfaceElevated
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else theme.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isRtl) "تشغيل" else "Play",
                                style = AppleTypography.caption,
                                color = if (isSelected) Color.Black else theme.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Quick profile pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProfilePill(title = if (isRtl) "حاكم الأداء: نشط" else "Governor: Active", color = theme.accent)
                ProfilePill(title = if (isRtl) "استثناء Doze: مدعوم" else "Doze Exemption: Yes", color = theme.accentBlue)
                ProfilePill(title = if (isRtl) "معدل التحديث: مقفل" else "Refresh Rate: Peak", color = theme.warning)
            }
        }
    }
}

@Composable
fun ProfilePill(title: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppleRadius.pill))
            .background(color.copy(alpha = 0.1f))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(AppleRadius.pill))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = title,
            style = AppleTypography.caption,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AddGameModalDialog(
    theme: AppleThemeTokens,
    isRtl: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var gameName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }

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
                Text(
                    text = if (isRtl) "إضافة لعبة مخصصة" else "Add Custom Game",
                    style = AppleTypography.titleMedium,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = gameName,
                    onValueChange = { gameName = it },
                    label = { Text(if (isRtl) "اسم اللعبة (مثال: PUBG Mobile)" else "Game Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppleRadius.medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.divider
                    )
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text(if (isRtl) "اسم الحزمة (مثال: com.tencent.ig)" else "Package Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppleRadius.medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.divider
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = if (isRtl) "إلغاء" else "Cancel",
                            color = theme.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (gameName.isNotBlank() && packageName.isNotBlank()) {
                                onConfirm(gameName.trim(), packageName.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(AppleRadius.medium)
                    ) {
                        Text(
                            text = if (isRtl) "إضافة" else "Add",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
