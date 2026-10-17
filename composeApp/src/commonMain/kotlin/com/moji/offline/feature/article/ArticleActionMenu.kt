package com.moji.offline.feature.article

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_browser_collect_night
import com.moji.offline.resources.ic_common_copy
import com.moji.offline.resources.ic_find_search_dark
import com.moji.offline.resources.ic_voice_high_white
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Text action popup — port of `popup_article_action_mode` (base.apk 3.4.9):
 * a #3A3A3A rounded-16 bar, 66dp tall, 24dp icon + 12sp label items.
 *
 * The offline build keeps the four actions the product needs for a word:
 * 复制 / 朗读 / 收藏 / 详情 (user-confirmed scope; 查词与 AI 暂不出现).
 */
enum class ArticleMenuAction(val label: String, val icon: DrawableResource) {
    COPY("复制", Res.drawable.ic_common_copy),
    READ("朗读", Res.drawable.ic_voice_high_white),
    COLLECT("收藏", Res.drawable.ic_browser_collect_night),
    DETAIL("详情", Res.drawable.ic_find_search_dark),
}

@Composable
fun ArticleActionMenu(
    /** Selection top inside the content area (CSS px == dp for the WebView). */
    anchorTop: Dp,
    onAction: (ArticleMenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val scrim = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = scrim, indication = null, onClick = onDismiss),
        )
        val above = anchorTop > 110.dp
        val y = if (above) anchorTop - 74.dp else anchorTop + 34.dp
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = y.coerceAtLeast(4.dp))
                .height(66.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF3A3A3A)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            ArticleMenuAction.entries.forEach { action ->
                Column(
                    Modifier
                        .width(64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAction(action) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painterResource(action.icon),
                        contentDescription = action.label,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        action.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}
