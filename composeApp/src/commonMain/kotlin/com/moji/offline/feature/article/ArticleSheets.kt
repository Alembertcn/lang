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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.article.ArticlePlayer
import com.moji.offline.data.audio.createAudioPlayer
import com.moji.offline.data.audio.createAudioRecorder
import com.moji.offline.data.audio.rememberMicPermission
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_player_menu_pause
import com.moji.offline.resources.ic_player_menu_start
import com.moji.offline.resources.ic_player_next
import com.moji.offline.resources.ic_player_prev
import com.moji.offline.ui.interaction.clickableNoRipple
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** Transparent scrim used by every article overlay. */
@Composable
private fun Scrim(onDismiss: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = source, indication = null, onClick = onDismiss),
    )
}

/**
 * Playback settings — `layout_audio_player_setting` in the original: loop mode
 * (none / sentence / article) and speed 0.6–1.4.
 */
@Composable
fun ArticleSettingsSheet(onDismiss: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val fg = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("播放设置", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("循环", color = fg.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LoopOption(
                    label = "不循环",
                    selected = ArticlePlayer.loopMode == ArticlePlayer.LoopMode.NONE,
                ) { ArticlePlayer.changeLoopMode(ArticlePlayer.LoopMode.NONE) }
                LoopOption(
                    label = "单句循环",
                    selected = ArticlePlayer.loopMode == ArticlePlayer.LoopMode.SENTENCE,
                ) { ArticlePlayer.changeLoopMode(ArticlePlayer.LoopMode.SENTENCE) }
                LoopOption(
                    label = "全文循环",
                    selected = ArticlePlayer.loopMode == ArticlePlayer.LoopMode.ARTICLE,
                ) { ArticlePlayer.changeLoopMode(ArticlePlayer.LoopMode.ARTICLE) }
            }
            Spacer(Modifier.height(18.dp))
            Text("倍速", color = fg.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0.6f, 0.8f, 1.0f, 1.2f, 1.4f).forEach { value ->
                    LoopOption(
                        label = "${formatSpeed(value)}x",
                        selected = ArticlePlayer.speed == value,
                    ) { ArticlePlayer.changeSpeed(value) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LoopOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFFE0315B) else fg.copy(alpha = 0.06f))
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (selected) Color.White else fg,
        fontSize = 14.sp,
    )
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

/**
 * `dialog_audio_player_list` — the plan/column playback queue. The current
 * track is highlighted; tapping another entry switches the session and keeps
 * playing (MOJi advances the same way at the end of a work).
 */
@Composable
fun ArticleQueueDialog(onOpenArticle: (String) -> Unit, onDismiss: () -> Unit) {
    val queue = ArticlePlayer.queue
    val current = ArticlePlayer.meta ?: return
    val surface = MaterialTheme.colorScheme.surface
    val fg = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(surface)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("播放列表", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (queue.isEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickableNoRipple {
                            onOpenArticle(current.id)
                            onDismiss()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        current.title,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFE0315B),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                LazyColumn(Modifier.height(320.dp)) {
                    itemsIndexed(queue) { index, item ->
                        val active = item.id == current.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickableNoRipple {
                                    ArticlePlayer.playAt(index)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                modifier = Modifier.width(24.dp),
                                color = if (active) Color(0xFFE0315B) else fg.copy(alpha = 0.35f),
                                fontSize = 13.sp,
                            )
                            Text(
                                item.title,
                                modifier = Modifier.weight(1f),
                                color = if (active) Color(0xFFE0315B) else fg,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatClock(item.audioDuration * 1000L),
                                color = fg.copy(alpha = 0.35f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** Bottom sheet with a scrim (same shape as the playback settings panel). */
@Composable
private fun Overlay(card: Color, fg: Color, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(interactionSource = source, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(card)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            content()
        }
    }
}

/** Word tapped in the article: surface form + ruby reading + dictionary form. */
data class WordInfo(
    val surface: String,
    val reading: String = "",
    val lemma: String = "",
) {
    /** What TTS should say (dictionary form when the ruby provides it). */
    val speakAs: String get() = lemma.ifBlank { surface }
}

/**
 * 详情 for a tapped word. The full dictionary card (释义/例句) ships with the
 * 词库 module (P8); until then show what the article itself knows: the surface
 * form, its furigana and the sentence it came from.
 */
@Composable
fun WordDetailSheet(
    word: WordInfo,
    sentence: String?,
    card: Color,
    fg: Color,
    onDismiss: () -> Unit,
) {
    Overlay(card, fg, onDismiss) {
        Text(word.surface, color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (word.reading.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(word.reading, color = fg.copy(alpha = 0.5f), fontSize = 14.sp)
        }
        if (word.lemma.isNotBlank() && word.lemma != word.surface) {
            Spacer(Modifier.height(4.dp))
            Text("原形：${word.lemma}", color = fg.copy(alpha = 0.5f), fontSize = 13.sp)
        }
        if (!sentence.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text("原文", color = fg.copy(alpha = 0.4f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(sentence, color = fg, fontSize = 15.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "释义与例句将在词库模块（P8）接入后显示",
            color = fg.copy(alpha = 0.4f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 精听跟读 — `ArticleIntensiveActivity` reduced to the part that works offline:
 * listen to the current SRT sentence, record one take, replay it, move between
 * sentences. (The original adds waveform scoring + a paged player.)
 */
@Composable
fun ShadowingSheet(onDismiss: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val fg = MaterialTheme.colorScheme.onSurface
    val scope = rememberCoroutineScope()
    val recorder = remember { createAudioRecorder() }
    val review = remember { createAudioPlayer() }
    var recording by remember { mutableStateOf(false) }
    var recordedPath by remember { mutableStateOf<String?>(null) }
    var playingTake by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch { recorder.stop() }
            review.release()
        }
    }

    val startRecording = {
        scope.launch {
            recorder.stop()
            recordedPath = null
            val path = recorder.start()
            recording = path != null
        }
    }
    val requestMic = rememberMicPermission { startRecording() }

    val index = ArticlePlayer.highlightIndex.coerceAtLeast(0)
    val sentence = ArticlePlayer.sentences.getOrNull(index)

    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(surface)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("精听跟读", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "第 ${index + 1} / ${ArticlePlayer.sentences.size} 句",
                    color = fg.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                sentence?.text?.ifBlank { "（这一篇没有逐句文稿）" } ?: "（这一篇没有逐句文稿）",
                color = fg,
                fontSize = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadowIcon(Res.drawable.ic_player_prev, "上一句", sentence != null) {
                    ArticlePlayer.seekToIndex((index - 1).coerceAtLeast(0))
                }
                ShadowIcon(
                    if (ArticlePlayer.playing) Res.drawable.ic_player_menu_pause else Res.drawable.ic_player_menu_start,
                    if (ArticlePlayer.playing) "暂停" else "听这一句",
                    sentence != null,
                ) {
                    if (ArticlePlayer.playing) {
                        ArticlePlayer.toggle()
                    } else {
                        ArticlePlayer.seekToIndex(index)
                        ArticlePlayer.toggle()
                    }
                }
                // Record: press once to start, again to stop.
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (recording) Color(0xFFE0315B) else Color(0xFF3A3A3A))
                        .clickableNoRipple {
                            if (recording) {
                                scope.launch {
                                    recordedPath = recorder.stop()
                                    recording = false
                                }
                            } else {
                                requestMic()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (recording) 16.dp else 22.dp)
                            .clip(if (recording) RoundedCornerShape(3.dp) else CircleShape)
                            .background(Color.White),
                    )
                }
                ShadowIcon(Res.drawable.ic_player_next, "下一句", sentence != null) {
                    ArticlePlayer.seekToIndex((index + 1).coerceAtMost(ArticlePlayer.sentences.size - 1))
                }
                ShadowIcon(
                    if (playingTake) Res.drawable.ic_player_menu_pause else Res.drawable.ic_player_menu_start,
                    "回放我的读音",
                    recordedPath != null,
                ) {
                    val path = recordedPath ?: return@ShadowIcon
                    scope.launch {
                        if (playingTake) {
                            review.pause()
                            playingTake = false
                        } else {
                            review.load(path)
                            review.play()
                            playingTake = true
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    recording -> "正在录音…再次点击停止"
                    recordedPath != null -> "已录好，点右侧 ▶ 回放对比"
                    else -> "点麦克风录一遍，再回放和原音对比"
                },
                color = fg.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ShadowIcon(
    icon: org.jetbrains.compose.resources.DrawableResource,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Image(
        painterResource(icon),
        contentDescription = description,
        modifier = Modifier
            .size(36.dp)
            .padding(6.dp)
            .clickableNoRipple(enabled = enabled, onClick = onClick),
    )
}
