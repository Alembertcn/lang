package com.moji.offline.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_common_next
import com.moji.offline.resources.ic_hc_nav_back_black
import com.moji.offline.resources.ic_hc_nav_back_white
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.LocalDarkTheme
import com.moji.offline.ui.theme.mojiSp
import org.jetbrains.compose.resources.painterResource

/** `@dimen/moji_toolbar_height`. */
val MojiToolbarHeight: Dp = 44.dp

private val ToolbarTitleLight = Color(0xFF3A3A3A) // Moji_text_color_primary
private val ToolbarTitleDark = Color(0xFFFAFAFA) // Moji_text_color_primary_dark

/**
 * Title / icon colour of every bar, i.e. the original `G5.b.h()` and `G5.b.i()`
 * (both return `Moji_text_color_primary`, or its `_dark` variant at night).
 */
@Composable
fun mojiToolbarContentColor(): Color =
    if (LocalDarkTheme.current) ToolbarTitleDark else ToolbarTitleLight

/**
 * The one title bar of the app, ported from `layout_moji_toolbar.xml`
 * (我的学习 / 阅读器), `layout_moji_big_text_toolbar.xml` (发现) and
 * `activity_plan_list.xml` (阅读计划). All of them are 44dp tall with an 18dp bold
 * centred title, and the two toolbar widgets resolve their title colour through
 * the same `G5.b.h()/i()`: #3A3A3A light, #FAFAFA dark. Side slots follow the
 * original geometry — back icon 44dp box with 10dp padding and 6dp start margin,
 * right icon 40dp box with 11dp padding and 9dp end margin — which is why the
 * title keeps 56dp clear on both sides.
 *
 * The bar paints its own status bar inset, so callers must not add another one.
 */
@Composable
fun MojiToolbar(
    title: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleColor = mojiToolbarContentColor()
    Box(
        modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .height(MojiToolbarHeight),
    ) {
        Text(
            title,
            modifier = Modifier
                .align(Alignment.Center)
                // MojiToolbar keeps 56dp clear on both sides for back/right icons.
                .padding(horizontal = 56.dp),
            color = titleColor,
            // layout_moji_toolbar：标题 18dp bold（原版 textSize 用 dp）
            fontSize = mojiSp(18),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 9.dp)
                    .size(40.dp),
                contentAlignment = Alignment.Center,
            ) { trailing() }
        }
        if (leading != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(44.dp),
                contentAlignment = Alignment.Center,
            ) { leading() }
        }
    }
}

/**
 * 工具栏返回。`MojiToolbar.a()` 走 `G5.b.b()`：
 * 浅色 `ic_hc_nav_back_black`，深色 `ic_hc_nav_back_white`，xxhdpi 72px = 24dp。
 * `ic_back` 是关闭用的叉，不拿来当返回。
 */
@Composable
fun MojiBackGlyph() {
    Image(
        painterResource(
            if (LocalDarkTheme.current) Res.drawable.ic_hc_nav_back_white
            else Res.drawable.ic_hc_nav_back_black,
        ),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}

/** 列表和头图尾标。原版 `ic_common_next`，xxhdpi 48px = 16dp，浅色深色同一张。 */
@Composable
fun MojiNextArrow(modifier: Modifier = Modifier) {
    Image(
        painterResource(Res.drawable.ic_common_next),
        contentDescription = null,
        modifier = modifier.size(16.dp),
    )
}

@Composable
fun MojiBackButton(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clickableNoRipple(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MojiBackGlyph()
    }
}
