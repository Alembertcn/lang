package com.moji.offline.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * 原版大量 `android:textSize="Ndp"`（不是 sp），字号跟密度走、不跟系统「字体大小」走。
 * Compose 的 `N.sp` 会乘 [LocalDensity.fontScale]，系统调大字时会再胀一圈。
 * 这里把 N dp 转成当前 density 下等价的 Sp，对位原版 dp 字号。
 */
@Composable
@ReadOnlyComposable
fun mojiSp(dp: Int): TextUnit = with(LocalDensity.current) { dp.dp.toSp() }
