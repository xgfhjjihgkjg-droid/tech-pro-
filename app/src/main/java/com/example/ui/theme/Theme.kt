package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.AppAccentTheme
import com.example.data.AppFontStyle
import com.example.data.AppTextScale
import com.example.data.AppThemeMode

fun accentSwatchColor(accent: AppAccentTheme, isDark: Boolean = false): Color = when (accent) {
    AppAccentTheme.EMERALD -> if (isDark) EmeraldPrimaryDark else EmeraldPrimaryLight
    AppAccentTheme.OCEAN -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    AppAccentTheme.VIOLET -> if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)
    AppAccentTheme.AMBER -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    AppAccentTheme.ROSE -> if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
    AppAccentTheme.GOLD -> if (isDark) Color(0xFFE5C158) else Color(0xFFB48312)
    AppAccentTheme.SAPPHIRE -> if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    AppAccentTheme.TEAL -> if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
    AppAccentTheme.BURGUNDY -> if (isDark) Color(0xFFF472B6) else Color(0xFF9F1239)
    AppAccentTheme.INDIGO -> if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)
    AppAccentTheme.COPPER -> if (isDark) Color(0xFFFB923C) else Color(0xFFC2410C)
    AppAccentTheme.PLUM -> if (isDark) Color(0xFFD946EF) else Color(0xFF86198F)
    AppAccentTheme.CORAL -> if (isDark) Color(0xFFFF7A6B) else Color(0xFFE04836)
    AppAccentTheme.JADE -> if (isDark) Color(0xFF34D399) else Color(0xFF047857)
    AppAccentTheme.GRAPHITE -> if (isDark) Color(0xFF94A3B8) else Color(0xFF334155)
    AppAccentTheme.COBALT -> if (isDark) Color(0xFF3B82F6) else Color(0xFF0369A1)
}

private fun buildDarkScheme(accent: AppAccentTheme): ColorScheme {
    val (primary, onPrimary, container, onContainer, secondary) = when (accent) {
        AppAccentTheme.EMERALD -> AccentColors(
            EmeraldPrimaryDark, OnEmeraldPrimaryDark, EmeraldContainerDark, OnEmeraldContainerDark, IceBlueSecondaryDark
        )
        AppAccentTheme.OCEAN -> AccentColors(
            Color(0xFF38BDF8), Color(0xFF002A40), Color(0xFF07466B), Color(0xFFC2E8FF), Color(0xFF00E5A3)
        )
        AppAccentTheme.VIOLET -> AccentColors(
            Color(0xFFA78BFA), Color(0xFF230E59), Color(0xFF3B1F7D), Color(0xFFE9DDFF), Color(0xFF38BDF8)
        )
        AppAccentTheme.AMBER -> AccentColors(
            Color(0xFFFBBF24), Color(0xFF3E2800), Color(0xFF5C3D00), Color(0xFFFFE49E), Color(0xFF00E5A3)
        )
        AppAccentTheme.ROSE -> AccentColors(
            Color(0xFFFB7185), Color(0xFF4C0519), Color(0xFF78122E), Color(0xFFFFD6DD), Color(0xFF38BDF8)
        )
        AppAccentTheme.GOLD -> AccentColors(
            Color(0xFFE5C158), Color(0xFF3B2B00), Color(0xFF574005), Color(0xFFFFEBA8), Color(0xFF38BDF8)
        )
        AppAccentTheme.SAPPHIRE -> AccentColors(
            Color(0xFF60A5FA), Color(0xFF09224D), Color(0xFF153875), Color(0xFFD6E6FF), Color(0xFF2DD4BF)
        )
        AppAccentTheme.TEAL -> AccentColors(
            Color(0xFF2DD4BF), Color(0xFF003731), Color(0xFF095249), Color(0xFFCCFBF1), Color(0xFF60A5FA)
        )
        AppAccentTheme.BURGUNDY -> AccentColors(
            Color(0xFFF472B6), Color(0xFF4A0723), Color(0xFF70123A), Color(0xFFFCE7F3), Color(0xFFE5C158)
        )
        AppAccentTheme.INDIGO -> AccentColors(
            Color(0xFF818CF8), Color(0xFF1B1954), Color(0xFF2E2C7D), Color(0xFFE0E7FF), Color(0xFF2DD4BF)
        )
        AppAccentTheme.COPPER -> AccentColors(
            Color(0xFFFB923C), Color(0xFF451900), Color(0xFF6B2A06), Color(0xFFFFEDD5), Color(0xFF2DD4BF)
        )
        AppAccentTheme.PLUM -> AccentColors(
            Color(0xFFE879F9), Color(0xFF44064E), Color(0xFF661075), Color(0xFFFAE8FF), Color(0xFF38BDF8)
        )
        AppAccentTheme.CORAL -> AccentColors(
            Color(0xFFFF7A6B), Color(0xFF4A0D07), Color(0xFF731910), Color(0xFFFFE2DE), Color(0xFF2DD4BF)
        )
        AppAccentTheme.JADE -> AccentColors(
            Color(0xFF34D399), Color(0xFF003824), Color(0xFF075437), Color(0xFFD1FAE5), Color(0xFF60A5FA)
        )
        AppAccentTheme.GRAPHITE -> AccentColors(
            Color(0xFFCBD5E1), Color(0xFF0F172A), Color(0xFF334155), Color(0xFFF1F5F9), Color(0xFF38BDF8)
        )
        AppAccentTheme.COBALT -> AccentColors(
            Color(0xFF38BDF8), Color(0xFF002945), Color(0xFF0A436B), Color(0xFFE0F2FE), Color(0xFFA78BFA)
        )
    }

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = secondary,
        onSecondary = OnIceBlueSecondaryDark,
        secondaryContainer = IceBlueContainerDark,
        onSecondaryContainer = OnIceBlueContainerDark,
        tertiary = VioletTertiaryDark,
        background = ObsidianBackground,
        onBackground = ObsidianTextPrimary,
        surface = ObsidianSurface,
        onSurface = ObsidianTextPrimary,
        surfaceVariant = ObsidianSurfaceVariant,
        onSurfaceVariant = ObsidianTextSecondary,
        surfaceContainerHigh = ObsidianSurfaceHigh,
        outline = ObsidianOutline,
        error = Color(0xFFF87171),
        onError = Color(0xFF450A0A)
    )
}

private fun buildLightScheme(accent: AppAccentTheme): ColorScheme {
    val (primary, onPrimary, container, onContainer, secondary) = when (accent) {
        AppAccentTheme.EMERALD -> AccentColors(
            EmeraldPrimaryLight, OnEmeraldPrimaryLight, EmeraldContainerLight, OnEmeraldContainerLight, IceBlueSecondaryLight
        )
        AppAccentTheme.OCEAN -> AccentColors(
            Color(0xFF0284C7), Color.White, Color(0xFFE0F2FE), Color(0xFF082F49), Color(0xFF008962)
        )
        AppAccentTheme.VIOLET -> AccentColors(
            Color(0xFF6D28D9), Color.White, Color(0xFFEDE9FE), Color(0xFF2E1065), Color(0xFF0284C7)
        )
        AppAccentTheme.AMBER -> AccentColors(
            Color(0xFFD97706), Color.White, Color(0xFFFEF3C7), Color(0xFF451A03), Color(0xFF008962)
        )
        AppAccentTheme.ROSE -> AccentColors(
            Color(0xFFE11D48), Color.White, Color(0xFFFFE4E6), Color(0xFF4C0519), Color(0xFF0284C7)
        )
        AppAccentTheme.GOLD -> AccentColors(
            Color(0xFFB48312), Color.White, Color(0xFFFEF3C7), Color(0xFF422D00), Color(0xFF0F766E)
        )
        AppAccentTheme.SAPPHIRE -> AccentColors(
            Color(0xFF1D4ED8), Color.White, Color(0xFFDBEAFE), Color(0xFF1E3A8A), Color(0xFF0F766E)
        )
        AppAccentTheme.TEAL -> AccentColors(
            Color(0xFF0F766E), Color.White, Color(0xFFCCFBF1), Color(0xFF134E4A), Color(0xFF1D4ED8)
        )
        AppAccentTheme.BURGUNDY -> AccentColors(
            Color(0xFF9F1239), Color.White, Color(0xFFFFE4E6), Color(0xFF4C0519), Color(0xFFB48312)
        )
        AppAccentTheme.INDIGO -> AccentColors(
            Color(0xFF4338CA), Color.White, Color(0xFFE0E7FF), Color(0xFF1E1B4B), Color(0xFF0F766E)
        )
        AppAccentTheme.COPPER -> AccentColors(
            Color(0xFFC2410C), Color.White, Color(0xFFFFEDD5), Color(0xFF431407), Color(0xFF0F766E)
        )
        AppAccentTheme.PLUM -> AccentColors(
            Color(0xFF86198F), Color.White, Color(0xFFFAE8FF), Color(0xFF4A044E), Color(0xFF0284C7)
        )
        AppAccentTheme.CORAL -> AccentColors(
            Color(0xFFE04836), Color.White, Color(0xFFFFE2DE), Color(0xFF4A0D07), Color(0xFF0F766E)
        )
        AppAccentTheme.JADE -> AccentColors(
            Color(0xFF047857), Color.White, Color(0xFFD1FAE5), Color(0xFF064E3B), Color(0xFF1D4ED8)
        )
        AppAccentTheme.GRAPHITE -> AccentColors(
            Color(0xFF334155), Color.White, Color(0xFFE2E8F0), Color(0xFF0F172A), Color(0xFF0284C7)
        )
        AppAccentTheme.COBALT -> AccentColors(
            Color(0xFF0369A1), Color.White, Color(0xFFE0F2FE), Color(0xFF0C4A6E), Color(0xFF6D28D9)
        )
    }

    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = secondary,
        onSecondary = OnIceBlueSecondaryLight,
        secondaryContainer = IceBlueContainerLight,
        onSecondaryContainer = OnIceBlueContainerLight,
        tertiary = Color(0xFF6D28D9),
        background = LightBackground,
        onBackground = LightTextPrimary,
        surface = LightSurface,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightTextSecondary,
        surfaceContainerHigh = Color(0xFFE2E8F0),
        outline = LightOutline,
        error = Color(0xFFDC2626),
        onError = Color.White
    )
}

private data class AccentColors(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
    val secondary: Color
)

private val CipherShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ToKerTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    accentTheme: AppAccentTheme = AppAccentTheme.EMERALD,
    fontStyle: AppFontStyle = AppFontStyle.CLASSIC,
    textScale: AppTextScale = AppTextScale.MEDIUM,
    boldText: Boolean = false,
    content: @Composable () -> Unit
) {
    CipherTalkTheme(
        themeMode = themeMode,
        accentTheme = accentTheme,
        fontStyle = fontStyle,
        textScale = textScale,
        boldText = boldText,
        content = content
    )
}

@Composable
fun CipherTalkTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    accentTheme: AppAccentTheme = AppAccentTheme.EMERALD,
    fontStyle: AppFontStyle = AppFontStyle.CLASSIC,
    textScale: AppTextScale = AppTextScale.MEDIUM,
    boldText: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    val colorScheme = remember(isDark, accentTheme) {
        if (isDark) buildDarkScheme(accentTheme) else buildLightScheme(accentTheme)
    }
    val typography = remember(fontStyle, textScale, boldText) {
        buildTypography(
            fontStyle = fontStyle,
            textScale = textScale,
            boldText = boldText
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = CipherShapes,
        content = content
    )
}
