package com.moji.offline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app switches themes in-app (MojiTheme), so the system bar icon contrast
 * has to follow LocalDarkTheme instead of the OS setting; every screen paints
 * its own top bar under the status bar (edge-to-edge, no double inset).
 */
@Composable
expect fun SystemBarAppearance(darkTheme: Boolean)

/**
 * 我的页头图是深色书墙照片：展开时状态栏图标要转白（对位原版 MineFragment），
 * 折叠到页面色工具条后再转回深色。App 级的 SystemBarAppearance 会读这个开关。
 */
object MineHeaderStatus {
    var lightIcons by mutableStateOf(false)
}

/** 原版设置项 `bright_mode_setting`（常亮模式）：开着时不让系统熄屏。 */
@Composable
expect fun KeepScreenOn(enabled: Boolean)
