package com.moji.offline.feature.article

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.article.ArticlePlayer
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_player_menu_close
import com.moji.offline.resources.ic_player_menu_close_dark
import com.moji.offline.resources.ic_player_menu_list
import com.moji.offline.resources.ic_player_menu_list_dark
import com.moji.offline.resources.ic_player_menu_pause
import com.moji.offline.resources.ic_player_menu_pause_dark
import com.moji.offline.resources.ic_player_menu_play_dark
import com.moji.offline.resources.ic_player_menu_start
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.LocalDarkTheme
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource

/**
 * Global article progress bar — port of `layout_audio_player_controller` +
 * `AudioPlayerControllerView` (base.apk 3.4.9):
 *
 *  64dp tall, 16dp top corner radius, white (dark #1C1C1E), 20dp elevation,
 *  close (44dp, 14 padding) | 44dp cover (8dp radius) + 16sp title | ring play
 *  button | list (44dp, 10 padding). In the original it is `layout_above`
 *  `tb_main_bottom_bar` (55dp) and under it the content ends, so the page is
 *  shortened rather than covered.
 */
@Composable
fun GlobalMiniPlayer(onOpenArticle: (String) -> Unit, onOpenQueue: () -> Unit) {
    val meta = ArticlePlayer.meta ?: return
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val bg = if (dark) Color(0xFF1C1C1E) else Color.White
    val fg = MaterialTheme.colorScheme.onSurface

    Row(
        Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = shape, clip = false)
            .clip(shape)
            .background(bg)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(2.dp))
        Image(
            painterResource(if (dark) Res.drawable.ic_player_menu_close_dark else Res.drawable.ic_player_menu_close),
            contentDescription = "关闭播放",
            modifier = Modifier
                .size(44.dp)
                .clickableNoRipple { ArticlePlayer.close() }
                .padding(14.dp),
        )
        Row(
            Modifier
                .weight(1f)
                .padding(start = 2.dp, end = 10.dp)
                .clickableNoRipple { onOpenArticle(meta.id) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cover by produceState<ImageBitmap?>(null, meta.coverFile) {
                value = if (meta.coverFile.isNotBlank()) {
                    runCatching {
                        Res.readBytes("files/find/articles/${meta.coverFile}").decodeToImageBitmap()
                    }.getOrNull()
                } else {
                    null
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (dark) Color(0xFF2C2C2F) else Color(0xFFE9ECF2)),
                contentAlignment = Alignment.Center,
            ) {
                val image = cover
                if (image != null) {
                    Image(
                        image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp),
                    )
                } else {
                    // Plan articles without a bundled cover: keep a stable
                    // glyph instead of an empty white square.
                    Text(
                        meta.title.take(1),
                        color = if (dark) Color(0xFF8B8787) else Color(0xFF9AA0A6),
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            }
            Text(
                meta.title,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                color = fg,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MiniPlayButton(dark = dark)
        Image(
            painterResource(if (dark) Res.drawable.ic_player_menu_list_dark else Res.drawable.ic_player_menu_list),
            contentDescription = "播放列表",
            modifier = Modifier
                .size(44.dp)
                .clickableNoRipple(onClick = onOpenQueue)
                .padding(10.dp),
        )
        Spacer(Modifier.width(6.dp))
    }
}

/** MOJi `AudioPlayButton`: 24dp arc ring behind the play/pause glyph. */
@Composable
private fun MiniPlayButton(dark: Boolean) {
    val progress = if (ArticlePlayer.durationMs <= 0) 0f else {
        (ArticlePlayer.positionMs.toFloat() / ArticlePlayer.durationMs).coerceIn(0f, 1f)
    }
    val trackColor = if (dark) Color.White.copy(alpha = 0.18f) else Color(0xFFE5E5E5)
    Box(
        Modifier
            .size(44.dp)
            .clickableNoRipple(enabled = ArticlePlayer.loaded) { ArticlePlayer.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(
                    Color(0xFFE0315B),
                    -90f,
                    360f * progress,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Image(
            painterResource(
                when {
                    ArticlePlayer.playing && dark -> Res.drawable.ic_player_menu_pause_dark
                    ArticlePlayer.playing -> Res.drawable.ic_player_menu_pause
                    dark -> Res.drawable.ic_player_menu_play_dark
                    else -> Res.drawable.ic_player_menu_start
                },
            ),
            contentDescription = if (ArticlePlayer.playing) "暂停" else "播放",
            modifier = Modifier.size(24.dp),
        )
    }
}
