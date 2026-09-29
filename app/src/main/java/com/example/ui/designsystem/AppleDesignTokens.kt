package com.example.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppleColors {
    val AccentGreen = Color(0xFF30D158)
    val AccentGreenLight = Color(0xFF248A3D)
    val AccentBlue = Color(0xFF0A84FF)
    val AccentBlueLight = Color(0xFF007AFF)
    val AccentIndigo = Color(0xFF5E5CE6)
    val AccentOrange = Color(0xFFFF9F0A)
    val AccentRed = Color(0xFFFF453A)

    object Dark {
        val Background = Color(0xFF080B11)
        val Surface = Color(0xFF101522)
        val SurfaceElevated = Color(0xFF161E30)

        val GlassLevel1 = Color(0x221A2338)
        val GlassLevel2 = Color(0x55121A2B)
        val GlassLevel3 = Color(0x88172136)

        val GlassBorderTop = Color(0x33FFFFFF)
        val GlassBorderBottom = Color(0x0AFFFFFF)

        val TextPrimary = Color(0xFFFFFFFF)
        val TextSecondary = Color(0xFF94A3B8)
        val TextTertiary = Color(0xFF64748B)
        val Divider = Color(0x1FFFFFFF)
    }

    object Light {
        val Background = Color(0xFFFAF7F2)
        val Surface = Color(0xFFFFFFFF)
        val SurfaceElevated = Color(0xFFF3EFEA)

        val GlassLevel1 = Color(0xB3F5F1EB)
        val GlassLevel2 = Color(0xC8FFFFFF)
        val GlassLevel3 = Color(0xDCFFFFFF)

        val GlassBorderTop = Color(0x18000000)
        val GlassBorderBottom = Color(0x06000000)

        val TextPrimary = Color(0xFF1C1B18)
        val TextSecondary = Color(0xFF595854)
        val TextTertiary = Color(0xFF8A8882)
        val Divider = Color(0x0F000000)
    }
}

data class AppleThemeTokens(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val glassSubtle: Color,
    val glassStandard: Color,
    val glassElevated: Color,
    val glassBorderBrush: Brush,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val accent: Color,
    val accentBlue: Color,
    val accentIndigo: Color,
    val warning: Color,
    val danger: Color
)

@Composable
fun getAppleTheme(themeMode: String = "dark"): AppleThemeTokens {
    val isDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    return if (isDark) {
        AppleThemeTokens(
            isDark = true,
            background = AppleColors.Dark.Background,
            surface = AppleColors.Dark.Surface,
            surfaceElevated = AppleColors.Dark.SurfaceElevated,
            glassSubtle = AppleColors.Dark.GlassLevel1,
            glassStandard = AppleColors.Dark.GlassLevel2,
            glassElevated = AppleColors.Dark.GlassLevel3,
            glassBorderBrush = Brush.verticalGradient(
                listOf(AppleColors.Dark.GlassBorderTop, AppleColors.Dark.GlassBorderBottom)
            ),
            textPrimary = AppleColors.Dark.TextPrimary,
            textSecondary = AppleColors.Dark.TextSecondary,
            textTertiary = AppleColors.Dark.TextTertiary,
            divider = AppleColors.Dark.Divider,
            accent = AppleColors.AccentGreen,
            accentBlue = AppleColors.AccentBlue,
            accentIndigo = AppleColors.AccentIndigo,
            warning = AppleColors.AccentOrange,
            danger = AppleColors.AccentRed
        )
    } else {
        AppleThemeTokens(
            isDark = false,
            background = AppleColors.Light.Background,
            surface = AppleColors.Light.Surface,
            surfaceElevated = AppleColors.Light.SurfaceElevated,
            glassSubtle = AppleColors.Light.GlassLevel1,
            glassStandard = AppleColors.Light.GlassLevel2,
            glassElevated = AppleColors.Light.GlassLevel3,
            glassBorderBrush = Brush.verticalGradient(
                listOf(AppleColors.Light.GlassBorderTop, AppleColors.Light.GlassBorderBottom)
            ),
            textPrimary = AppleColors.Light.TextPrimary,
            textSecondary = AppleColors.Light.TextSecondary,
            textTertiary = AppleColors.Light.TextTertiary,
            divider = AppleColors.Light.Divider,
            accent = AppleColors.AccentGreenLight,
            accentBlue = AppleColors.AccentBlueLight,
            accentIndigo = AppleColors.AccentIndigo,
            warning = AppleColors.AccentOrange,
            danger = AppleColors.AccentRed
        )
    }
}

object AppleSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 40.dp
    val huge: Dp = 48.dp
}

object AppleRadius {
    val small: Dp = 10.dp
    val medium: Dp = 14.dp
    val large: Dp = 20.dp
    val extraLarge: Dp = 28.dp
    val pill: Dp = 999.dp
}

object AppleTypography {
    val displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-1.0).sp
    )
    val displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.5).sp
    )
    val titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.3).sp
    )
    val titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.2).sp
    )
    val titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    )
    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    val callout = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    val footnote = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp
    )
}
