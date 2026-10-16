package com.moji.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.moji.offline.domain.model.ThemeMode

val MojiPink = Color(0xFFFF4D6A)
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
