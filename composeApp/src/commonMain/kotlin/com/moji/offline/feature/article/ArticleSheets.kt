package com.moji.offline.feature.article

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.article.ArticlePlayer
import com.moji.offline.data.audio.SpeechEngine
import com.moji.offline.data.audio.WordAudio
import com.moji.offline.data.audio.createAudioPlayer
import com.moji.offline.data.audio.createAudioRecorder
import com.moji.offline.data.audio.rememberMicPermission
import com.moji.offline.data.db.UserDataRepository
import com.moji.offline.data.settings.AppSettings
import com.moji.offline.data.settings.SpellMode
import com.moji.offline.data.word.RelatedGroup
import com.moji.offline.data.word.RelatedWord
import com.moji.offline.data.word.WordDict
import com.moji.offline.data.word.WordEntry
import com.moji.offline.data.word.WordExample
import com.moji.offline.data.word.WordSense
import com.moji.offline.resources.Res
import com.moji.offline.resources.edit_icon_share_white
import com.moji.offline.resources.ic_common_expand_dark
import com.moji.offline.resources.ic_common_expand_white
import com.moji.offline.resources.ic_common_fav_dark
import com.moji.offline.resources.ic_common_fav_selected
import com.moji.offline.resources.ic_common_more_hor
import com.moji.offline.resources.ic_common_more_hor_dark
import com.moji.offline.resources.ic_common_note_dark
import com.moji.offline.resources.ic_player_menu_pause
import com.moji.offline.resources.ic_player_menu_start
import com.moji.offline.resources.ic_player_next
import com.moji.offline.resources.ic_player_prev
import com.moji.offline.resources.ic_voice_high_red
import com.moji.offline.resources.ic_word_common_fav
import com.moji.offline.resources.ic_word_common_note
import com.moji.offline.resources.ic_word_common_share
import com.moji.offline.resources.ic_word_lang_ja
import com.moji.offline.resources.ic_word_lang_ja_dark
import com.moji.offline.resources.ic_word_lang_zh
import com.moji.offline.resources.ic_word_lang_zh_dark
import com.moji.offline.resources.ic_word_sense_voice
import com.moji.offline.resources.ic_word_sense_voice_dark
import com.moji.offline.ui.MojiBackButton
import com.moji.offline.ui.PlatformBackHandler
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.sharePlainText
import com.moji.offline.ui.theme.LocalDarkTheme
import kotlin.time.Clock
import org.jetbrains.compose.resources.DrawableResource
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
    LaunchedEffect(current.id) {
        ArticlePlayer.expandQueueIfNeeded()
    }
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
                // Original `rl_audio_player_list` is 454dp tall.
                LazyColumn(Modifier.height(454.dp)) {
                    itemsIndexed(queue) { index, item ->
                        val active = item.id == current.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickableNoRipple {
                                    ArticlePlayer.playAt(index)
                                    onDismiss()
                                }
                                .height(56.dp),
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
    /** Original wort id when this page was opened from 关联词. */
    val id: String = "",
) {
    /** What TTS should say (dictionary form when the ruby provides it). */
    val speakAs: String get() = lemma.ifBlank { surface }
}

/**
 * 词条详情页。原版 `SearchResultClickHelper.clickToWord` 打开
 * `ContentShowActivity` + `WordDetailFragment`，不是盖在词卡上的弹层。
 * 页头是词形、声调、注音和发音；释义卡片和关联词来自本机词库。
 * 底栏编辑按钮原版对词条隐藏，只留笔记、收藏、分享。
 */
@Composable
fun WordDetailPage(
    word: WordInfo,
    onDismiss: () -> Unit,
) {
    var stack by remember(word) { mutableStateOf(listOf(word)) }
    val current = stack.last()
    val closeOrPop = {
        if (stack.size > 1) stack = stack.dropLast(1) else onDismiss()
    }
    PlatformBackHandler(onBack = closeOrPop)
    val dark = LocalDarkTheme.current
    val pageBg = if (dark) Color(0xFF0E0E11) else Color.White
    val fg = if (dark) Color(0xFFF2F2F2) else Color(0xFF1C1C1E)
    val sub = if (dark) Color(0xFF9A9AA0) else Color(0xFF8E8E93)
    val barBg = if (dark) Color(0xFF1C1C1E) else Color.White
    var entry by remember(current) { mutableStateOf<WordEntry?>(null) }
    var senses by remember(current) { mutableStateOf<List<WordSense>>(emptyList()) }
    var related by remember(current) { mutableStateOf<List<RelatedGroup>>(emptyList()) }
    var expandedRelate by remember(current) { mutableStateOf(setOf<String>()) }
    var favorite by remember(current) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val speech = remember { SpeechEngine() }
    DisposableEffect(Unit) { onDispose { speech.stop() } }
    LaunchedEffect(current) {
        // An id from 关联词 must not fall back to the spelling: 多音词 share a
        // spell with a different record, and a missing id stays a bare headword.
        val found = if (current.id.isNotBlank()) {
            WordDict.findById(current.id)
        } else {
            WordDict.find(current.surface, current.lemma)
        }
        entry = found
        senses = found?.let { WordDict.detail(it.id) }.orEmpty()
        related = found?.let { WordDict.related(it.id) }.orEmpty()
        favorite = UserDataRepository.isWordFavorite(found?.id?.takeIf { it.isNotBlank() } ?: current.surface)
    }
    // 原版 `word_detail_fold_state_` 默认 false：shouldFold=false，释义全部展开。
    // 右上角翻转这个开关并清空单条覆盖；点某一条只改这一条（没有例句的不收）。
    var foldAll by remember(current) { mutableStateOf(false) }
    var openOverride by remember(current) { mutableStateOf(mapOf<Int, Boolean>()) }
    val head = entry?.spell?.takeIf { it.isNotBlank() } ?: current.lemma.ifBlank { current.surface }
    val phonetic = when (AppSettings.spell) {
        SpellMode.ROMAJI -> entry?.romaji?.takeIf { it.isNotBlank() } ?: entry?.pron?.takeIf { it.isNotBlank() } ?: current.reading
        SpellMode.HIRAGANA -> entry?.pron?.takeIf { it.isNotBlank() } ?: current.reading
        SpellMode.NONE -> ""
    }
    Box(Modifier.fillMaxSize().background(pageBg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(50.dp)
                    .padding(start = 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    MojiBackButton(closeOrPop)
                }
                Spacer(Modifier.weight(1f))
                DetailIcon(
                    // 文件名和像素相反：expand_white 是 #3A3A3A，给白底；expand_dark 是白色，给深色底。
                    // 对位 AbsContentFragment.loadTheme 里 iv_expand 的选择。
                    if (dark) Res.drawable.ic_common_expand_dark else Res.drawable.ic_common_expand_white,
                    24.dp,
                    onClick = {
                        foldAll = !foldAll
                        openOverride = emptyMap()
                    },
                )
                Spacer(Modifier.width(4.dp))
                DetailIcon(
                    if (dark) Res.drawable.ic_common_more_hor_dark else Res.drawable.ic_common_more_hor,
                    24.dp,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 108.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(head, color = fg, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    val accent = entry?.accent.orEmpty()
                    if (accent.isNotBlank()) {
                        Text(
                            accent,
                            color = fg,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(36.dp)
                            .clickableNoRipple {
                                scope.launch {
                                    val id = entry?.id.orEmpty()
                                    if (id.isNotBlank() && WordAudio.speak(id)) return@launch
                                    speech.speak(entry?.spokenOr(current.speakAs) ?: current.speakAs)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painterResource(Res.drawable.ic_voice_high_red),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                if (phonetic.isNotBlank()) {
                    Text(phonetic, color = sub, fontSize = 15.sp)
                }
                val pos = entry?.pos.orEmpty()
                if (pos.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        pos,
                        color = fg,
                        fontSize = 15.sp,
                        modifier = Modifier.detailPosUnderline(Color(0xFFFF9F0A)),
                    )
                }
                Spacer(Modifier.height(22.dp))
                Text("释义", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (senses.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("离线词书暂无该词条目", color = sub, fontSize = 13.sp)
                } else {
                    senses.forEachIndexed { index, sense ->
                        Spacer(Modifier.height(if (index == 0) 16.dp else 8.dp))
                        val open = openOverride[index] ?: !foldAll
                        SenseCard(
                            sense = sense,
                            expanded = open,
                            dark = dark,
                            fg = fg,
                            onToggle = {
                                if (sense.examples.isNotEmpty()) {
                                    openOverride = openOverride + (index to !open)
                                }
                            },
                            onSpeakGloss = { text ->
                                scope.launch {
                                    WordAudio.stop()
                                    speech.stop()
                                    speech.speak(text)
                                }
                            },
                            onSpeakExample = { example ->
                                scope.launch {
                                    // 原版点例句或右侧喇叭都走 readObject(example)，
                                    // native 用 tarType 103 + 例句 id 播 mp3。没有文件时才退回系统日语语音。
                                    speech.stop()
                                    val played = WordAudio.speakExample(example.id)
                                    if (!played && example.ja.isNotBlank()) {
                                        speech.speak(example.ja)
                                    }
                                }
                            },
                        )
                    }
                }
                if (related.isNotEmpty()) {
                    RelatedWords(
                        groups = related,
                        expanded = expandedRelate,
                        dark = dark,
                        fg = fg,
                        onToggle = { kind ->
                            expandedRelate = if (kind in expandedRelate) {
                                expandedRelate - kind
                            } else {
                                expandedRelate + kind
                            }
                        },
                        onOpen = { item, kind ->
                            stack = stack + WordInfo(
                                surface = item.spell,
                                reading = if (kind == "s") "" else item.extra,
                                lemma = item.spell,
                                id = item.id,
                            )
                        },
                    )
                }
            }
        }
        if (senses.isNotEmpty()) {
            Text(
                "1/1",
                color = sub,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (dark) Color(0xFF2C2C2E) else Color(0xFFF4F4F4))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .shadow(8.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(barBg)
                .height(60.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBarButton(
                icon = if (dark) Res.drawable.ic_common_note_dark else Res.drawable.ic_word_common_note,
                label = "笔记",
                fg = fg,
                onClick = {},
            )
            DetailBarButton(
                icon = when {
                    favorite -> Res.drawable.ic_common_fav_selected
                    dark -> Res.drawable.ic_common_fav_dark
                    else -> Res.drawable.ic_word_common_fav
                },
                label = "收藏",
                fg = fg,
                onClick = {
                    val next = !favorite
                    favorite = next
                    scope.launch {
                        val key = entry?.id?.takeIf { it.isNotBlank() } ?: current.surface
                        if (next) {
                            UserDataRepository.addWordFavorite(
                                wordId = key,
                                spell = head,
                                pron = entry?.pron ?: current.reading,
                                excerpt = entry?.excerpt.orEmpty(),
                                nowMs = Clock.System.now().toEpochMilliseconds(),
                            )
                        } else {
                            UserDataRepository.removeWordFavorite(key)
                        }
                    }
                },
            )
            DetailBarButton(
                icon = if (dark) Res.drawable.edit_icon_share_white else Res.drawable.ic_word_common_share,
                label = "分享",
                fg = fg,
                onClick = {
                    val lines = buildList {
                        add(head)
                        if (phonetic.isNotBlank()) add(phonetic)
                        senses.forEach { sense ->
                            if (sense.zh.isNotBlank()) add(sense.zh)
                            if (sense.ja.isNotBlank()) add(sense.ja)
                        }
                    }
                    sharePlainText(lines.joinToString("\n"))
                },
            )
        }
    }
}

private val RELATE_LABEL = mapOf(
    "i" to "自动词",
    "t" to "他动词",
    "a" to "同词位",
    "p" to "多音词",
    "y" to "近义词",
    "n" to "反义词",
    "s" to "话题词",
)

/**
 * 原版 H5 `relateView`：标题「关联词」，每一类一行。
 * 近义词、反义词先显示 8 个，其余用「更多」展开。话题词带「Ai生成」和中文。
 * 多音词、同词位、自他动词带读音。点词条打开那一条的详情。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedWords(
    groups: List<RelatedGroup>,
    expanded: Set<String>,
    dark: Boolean,
    fg: Color,
    onToggle: (String) -> Unit,
    onOpen: (RelatedWord, String) -> Unit,
) {
    val label = if (dark) Color(0x80F2F2F2) else Color(0x80000000)
    val gloss = if (dark) Color(0x66F2F2F2) else Color(0x66000000)
    val line = if (dark) Color(0x33FFFFFF) else Color(0x33000000)
    val tagBg = if (dark) Color(0x1AFFFFFF) else Color(0x1AACACAC)
    Spacer(Modifier.height(40.dp))
    Text("关联词", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(line))
    groups.forEachIndexed { index, group ->
        val preview = group.kind == "y" || group.kind == "n"
        val shown = if (preview && group.kind !in expanded) group.words.take(8) else group.words
        val more = preview && group.words.size > 8 && group.kind !in expanded
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.width(69.dp)) {
                Text(
                    RELATE_LABEL[group.kind] ?: group.kind,
                    color = label,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                )
                if (group.kind == "s") {
                    Text(
                        "Ai生成",
                        color = label,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tagBg)
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    )
                }
            }
            FlowRow(
                Modifier.weight(1f).padding(start = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                shown.forEach { item ->
                    Row(
                        Modifier.clickableNoRipple { onOpen(item, group.kind) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.spell, color = fg, fontSize = 16.sp, lineHeight = 26.sp)
                        val extra = when (group.kind) {
                            "y", "n" -> ""
                            else -> item.extra
                        }
                        if (extra.isNotBlank()) {
                            Text(
                                " $extra",
                                color = if (group.kind == "s") gloss else label,
                                fontSize = 16.sp,
                                lineHeight = 26.sp,
                            )
                        }
                    }
                }
                if (more) {
                    Text(
                        "更多",
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        lineHeight = 26.sp,
                        modifier = Modifier.clickableNoRipple { onToggle(group.kind) },
                    )
                }
            }
        }
        if (index != groups.lastIndex) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(line.copy(alpha = 0.45f)))
        }
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(line))
}

@Composable
private fun DetailIcon(
    icon: DrawableResource,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .size(36.dp)
            .then(if (onClick != null) Modifier.clickableNoRipple(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(size))
    }
}

@Composable
private fun DetailBarButton(
    icon: DrawableResource,
    label: String,
    fg: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(72.dp)
            .fillMaxSize()
            .clickableNoRipple(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = fg, fontSize = 10.sp)
    }
}

/**
 * 释义卡片。对位词条 H5 `subdetailView`：
 * 展开时底色 `--bg-second`，例句在卡片外面；收起且有例句时改 `--bg-third`，
 * 右下角切一刀并叠上 `IconCorner`。没有例句的条目点了不收。
 */
@Composable
private fun SenseCard(
    sense: WordSense,
    expanded: Boolean,
    dark: Boolean,
    fg: Color,
    onToggle: () -> Unit,
    onSpeakGloss: (String) -> Unit,
    onSpeakExample: (WordExample) -> Unit,
) {
    val folded = sense.examples.isNotEmpty() && !expanded
    val cardColor = when {
        dark && folded -> Color.White.copy(alpha = 0.08f)
        dark -> Color(0xFF1C1C1E)
        folded -> Color.Black.copy(alpha = 0.05f)
        else -> Color.Black.copy(alpha = 0.10f)
    }
    val langZh = if (dark) Res.drawable.ic_word_lang_zh_dark else Res.drawable.ic_word_lang_zh
    val langJa = if (dark) Res.drawable.ic_word_lang_ja_dark else Res.drawable.ic_word_lang_ja
    val voice = if (dark) Res.drawable.ic_word_sense_voice_dark else Res.drawable.ic_word_sense_voice
    val exampleZh = if (dark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clickableNoRipple(onClick = onToggle)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(if (folded) FoldedSenseShape else RoundedCornerShape(12.dp))
                    .background(cardColor)
                    .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                if (sense.zh.isNotBlank()) {
                    SenseLine(langZh, sense.zh, fg)
                }
                if (sense.ja.isNotBlank()) {
                    if (sense.zh.isNotBlank()) Spacer(Modifier.height(8.dp))
                    SenseLine(langJa, sense.ja, fg, voice) { onSpeakGloss(sense.ja) }
                }
            }
            if (folded) {
                Canvas(Modifier.align(Alignment.BottomEnd).size(16.dp, 15.dp)) {
                    val sx = size.width / 16f
                    val sy = size.height / 15f
                    fun px(v: Float) = v * sx
                    fun py(v: Float) = v * sy
                    val corner = Path().apply {
                        moveTo(px(0f), py(15f))
                        lineTo(px(16f), py(0f))
                        quadraticBezierTo(px(11.2646f), py(1.71272f), px(7.12766f), py(1.64422f))
                        cubicTo(
                            px(4.44691f), py(1.59982f),
                            px(1.33184f), py(4.52036f),
                            px(1.38642f), py(7.20093f),
                        )
                        quadraticBezierTo(px(1.46711f), py(11.1638f), px(0f), py(15f))
                        close()
                    }
                    drawPath(
                        corner,
                        if (dark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                    )
                }
            }
        }
        if (expanded) {
            sense.examples.forEach { example ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .clickableNoRipple { onSpeakExample(example) },
                    ) {
                        if (example.ja.isNotBlank()) {
                            Text(example.ja, color = fg, fontSize = 16.sp, lineHeight = 20.sp)
                        }
                        if (example.zh.isNotBlank()) {
                            Text(
                                example.zh,
                                color = exampleZh,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    if (example.ja.isNotBlank()) {
                        // 右侧是例句喇叭 IconExampleVoice，20px 红喇叭，和词头的发音键同一张图。
                        // 释义行里的小喇叭才是 IconSentenceVoice。
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .size(32.dp)
                                .clickableNoRipple { onSpeakExample(example) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painterResource(Res.drawable.ic_voice_high_red),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SenseLine(
    icon: DrawableResource,
    text: String,
    fg: Color,
    speaker: DrawableResource? = null,
    onSpeak: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.Top) {
        Image(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Box(Modifier.padding(top = 6.dp).size(12.dp, 12.dp), contentAlignment = Alignment.Center) {
            if (speaker != null && onSpeak != null) {
                Image(
                    painterResource(speaker),
                    contentDescription = null,
                    modifier = Modifier.size(8.dp, 12.dp).clickableNoRipple(onClick = onSpeak),
                )
            }
        }
        Text(
            text,
            color = fg,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 收起时右下角切掉 16×15，对位 `.subdetailView.isFold` 的 clip-path。 */
private object FoldedSenseShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cutW = with(density) { 16.dp.toPx() }
        val cutH = with(density) { 15.dp.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - cutH)
            lineTo(size.width - cutW, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/** 词条详情页词性下的橙色短线，对位截图里「助动」的下划线。 */
private fun Modifier.detailPosUnderline(color: Color): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        val height = 3.dp.toPx()
        drawRect(color, topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
    }
)

/**
 * MJ辞書 for the tapped word: the dictionary gloss from the bundled subset
 * the local word book; the online dictionary stays off per project rule 零之三.
 */
@Composable
fun WordDictSheet(
    word: WordInfo,
    entry: WordEntry?,
    card: Color,
    fg: Color,
    onDismiss: () -> Unit,
) {
    Overlay(card, fg, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry?.spell?.takeIf { it.isNotBlank() } ?: word.surface,
                color = fg,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            val reading = entry?.pron?.takeIf { it.isNotBlank() } ?: word.reading
            if (reading.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(reading, color = fg.copy(alpha = 0.6f), fontSize = 16.sp)
            }
            val accent = entry?.accent.orEmpty()
            if (accent.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(accent, color = fg.copy(alpha = 0.6f), fontSize = 15.sp)
            }
        }
        val e = entry
        if (e == null) {
            Spacer(Modifier.height(10.dp))
            Text("离线词书暂无该词条目", color = fg.copy(alpha = 0.4f), fontSize = 12.sp)
        } else {
            if (e.pos.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(e.pos, color = fg.copy(alpha = 0.55f), fontSize = 13.sp)
            }
            if (e.meaningZh.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(e.meaningZh, color = fg, fontSize = 15.sp, lineHeight = 22.sp)
            }
            if (e.definitionJa.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(e.definitionJa, color = fg.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
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
