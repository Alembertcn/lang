package com.moji.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.moji.offline.domain.model.ThemeMode

/** Brand pink sampled from the original APK tab icons (ic_home_find_sel). */
val MojiPink = Color(0xFFE0325C)
val MojiPage = Color(0xFFF3F4F6)

/** Resolved app theme (respects [ThemeMode], not just the system setting). */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = MojiPink,
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3D5A80),
    background = MojiPage,
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A90),
    onPrimary = Color(0xFF3B0012),
    secondary = Color(0xFF9BB1CE),
    background = Color(0xFF121412),
    surface = Color(0xFF1C1F1D),
    onBackground = Color(0xFFE8E6E1),
    onSurface = Color(0xFFE8E6E1),
)

/**
 * Material3 默认 Typography 给 body/label 加了 0.2–0.5.sp 的 letterSpacing
 * （TypeScaleTokens.BodyLargeTracking = 0.5.sp）。`Text(fontSize = N.sp)` 只覆盖字号，
 * 字距仍从 LocalTextStyle 继承，中文会被撑疏一圈，看起来比原版 TextView（字距 0）大。
 * 这里把各档 tracking 清零，行高也按「字号本身」收紧，对位原版。
 */
private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun TextStyle.mojiChrome(): TextStyle = copy(
    letterSpacing = 0.sp,
    lineHeight = fontSize,
    lineHeightStyle = TightLineHeight,
)

private val MojiTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.mojiChrome(),
        displayMedium = base.displayMedium.mojiChrome(),
        displaySmall = base.displaySmall.mojiChrome(),
        headlineLarge = base.headlineLarge.mojiChrome(),
        headlineMedium = base.headlineMedium.mojiChrome(),
        headlineSmall = base.headlineSmall.mojiChrome(),
        titleLarge = base.titleLarge.mojiChrome(),
        titleMedium = base.titleMedium.mojiChrome(),
        titleSmall = base.titleSmall.mojiChrome(),
        bodyLarge = base.bodyLarge.mojiChrome(),
        bodyMedium = base.bodyMedium.mojiChrome(),
        bodySmall = base.bodySmall.mojiChrome(),
        labelLarge = base.labelLarge.mojiChrome(),
        labelMedium = base.labelMedium.mojiChrome(),
        labelSmall = base.labelSmall.mojiChrome(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MojiTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MojiTypography,
    ) {
        // Default: no Material ripple flash on TextButton / Surface clicks (see UI.md).
        CompositionLocalProvider(
            LocalRippleConfiguration provides null,
            LocalDarkTheme provides dark,
        ) {
            content()
        }
    }
}
