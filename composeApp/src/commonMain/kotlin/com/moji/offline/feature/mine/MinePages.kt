package com.moji.offline.feature.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.audio.WordAudio
import com.moji.offline.data.audio.SpeechEngine
import com.moji.offline.data.db.UserDataRepository
import com.moji.offline.data.mine.MineHistoryItem
import com.moji.offline.data.mine.MineStore
import com.moji.offline.data.word.WordDict
import com.moji.offline.data.word.WordEntry
import com.moji.offline.db.Article_favorite
import com.moji.offline.db.Word_favorite
import com.moji.offline.feature.article.WordCardOverlay
import com.moji.offline.feature.article.WordDetailPage
import com.moji.offline.feature.article.WordDictSheet
import com.moji.offline.feature.article.WordInfo
import com.moji.offline.ui.MockImage
import com.moji.offline.ui.MojiBackButton
import com.moji.offline.ui.MojiNextArrow
import com.moji.offline.ui.MojiToolbar
import com.moji.offline.ui.PlatformBackHandler
import com.moji.offline.ui.i18n.LocalAppStrings
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.LocalDarkTheme
import com.moji.offline.ui.theme.MojiPink
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// 与 MineScreen 同一套原版色（colors.xml 采样）。
private val PageBg = Color(0xFFF1F1F1)
private val PageBgDark = Color(0xFF010101)
private val CardBg = Color(0xFFFFFFFF)
private val CardBgDark = Color(0xFF1C1C1E)
private val TitleFg = Color(0xFF3A3A3A)
private val TitleFgDark = Color(0xFFFAFAFA)
private val SecondaryFg = Color(0xFFACACAC)
private val DividerFg = Color(0xFFECECEC)
private val DividerFgDark = Color(0xFF3B3B3B)

@Composable
private fun pageColors(): Triple<Color, Color, Color> {
    val dark = LocalDarkTheme.current
    return Triple(
        if (dark) PageBgDark else PageBg,
        if (dark) CardBgDark else CardBg,
        if (dark) TitleFgDark else TitleFg,
    )
}

private fun dayTime(epochMs: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.monthNumber}/${local.dayOfMonth} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

/** 阅读历史（本地 reading_history）：列表 + 长按移除。 */
@Composable
fun MineHistoryPage(onBack: () -> Unit, onOpenArticle: (String) -> Unit) {
    val strings = LocalAppStrings.current
    val (pageBg, card, titleFg) = pageColors()
    var items by remember { mutableStateOf<List<MineHistoryItem>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { items = MineStore.history() }

    Column(Modifier.fillMaxSize().background(pageBg)) {
        MojiToolbar(strings.mineHistory, background = pageBg, leading = { MojiBackButton(onBack) })
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.mineHistoryEmpty, color = SecondaryFg, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.articleId }) { item ->
                    HistoryRow(
                        item = item,
                        card = card,
                        titleFg = titleFg,
                        onClick = { onOpenArticle(item.articleId) },
                        onLongClick = {
                            scope.launch {
                                UserDataRepository.removeHistory(item.articleId)
                                reload++
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: MineHistoryItem,
    card: Color,
    titleFg: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    Column(Modifier.fillMaxWidth().background(card)) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(60.dp).height(40.dp).clip(RoundedCornerShape(6.dp))) {
                MockImage(item.cover, Modifier.matchParentSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 15.sp,
                    color = titleFg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(dayTime(item.lastReadAt), fontSize = 12.sp, color = SecondaryFg)
            }
            Spacer(Modifier.width(8.dp))
            MojiNextArrow()
        }
        Box(
            Modifier.fillMaxWidth().padding(start = 88.dp).height(0.5.dp)
                .background(if (dark) DividerFgDark else DividerFg),
        )
    }
}

/** 收藏夹（原版 CollectSearchActivity 的离线版）：短文 + 单词两段，长按取消收藏。 */
@Composable
fun MineFavoritesPage(onBack: () -> Unit, onOpenArticle: (String) -> Unit) {
    val strings = LocalAppStrings.current
    val (pageBg, card, titleFg) = pageColors()
    var articles by remember { mutableStateOf<List<Article_favorite>>(emptyList()) }
    var words by remember { mutableStateOf<List<Word_favorite>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var cardWord by remember { mutableStateOf<WordInfo?>(null) }
    var detailWord by remember { mutableStateOf<WordInfo?>(null) }
    var dictWord by remember { mutableStateOf<WordInfo?>(null) }
    var dictEntry by remember { mutableStateOf<WordEntry?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val speech = remember { SpeechEngine() }
    PlatformBackHandler(enabled = detailWord == null, onBack = onBack)

    LaunchedEffect(reload) {
        articles = UserDataRepository.articleFavorites()
        words = UserDataRepository.wordFavorites()
    }

    Column(Modifier.fillMaxSize().background(pageBg)) {
        MojiToolbar(strings.mineFavorites, background = pageBg, leading = { MojiBackButton(onBack) })
        if (articles.isEmpty() && words.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.mineFavoritesEmpty, color = SecondaryFg, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (articles.isNotEmpty()) {
                    item { SectionHeader(strings.mineArticles) }
                    items(articles, key = { it.article_id }) { fav ->
                        HistoryRow(
                            item = MineHistoryItem(fav.article_id, fav.title, "", fav.created_at),
                            card = card,
                            titleFg = titleFg,
                            onClick = { onOpenArticle(fav.article_id) },
                            onLongClick = {
                                scope.launch {
                                    UserDataRepository.removeArticleFavorite(fav.article_id)
                                    reload++
                                }
                            },
                        )
                    }
                }
                if (words.isNotEmpty()) {
                    item { SectionHeader(strings.mineWords) }
                    items(words, key = { it.word_id }) { fav ->
                        WordRow(
                            fav = fav,
                            card = card,
                            titleFg = titleFg,
                            onClick = {
                                cardWord = WordInfo(
                                    surface = fav.spell,
                                    reading = fav.pron.orEmpty(),
                                    lemma = fav.spell,
                                )
                            },
                            onLongClick = {
                                scope.launch {
                                    UserDataRepository.removeWordFavorite(fav.word_id)
                                    reload++
                                }
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    val word = cardWord
    if (word != null) {
        WordCardOverlay(
            word = word,
            onRead = {
                scope.launch {
                    val entry = WordDict.find(word.surface, word.lemma)
                    if (entry != null && WordAudio.speak(entry.id)) return@launch
                    speech.speak(entry?.spokenOr(word.speakAs) ?: word.speakAs)
                }
            },
            onCopy = { clipboard.setText(AnnotatedString(word.surface)) },
            onDetail = { detailWord = word },
            onDictionary = {
                dictWord = word
                scope.launch { dictEntry = WordDict.find(word.surface, word.lemma) }
            },
            onDismiss = { cardWord = null },
        )
    }
    detailWord?.let { w ->
        WordDetailPage(w) { detailWord = null }
    }
    dictWord?.let { w ->
        WordDictSheet(w, dictEntry, card, titleFg) { dictWord = null }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        color = SecondaryFg,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun WordRow(
    fav: Word_favorite,
    card: Color,
    titleFg: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fav.spell, fontSize = 16.sp, color = titleFg, maxLines = 1)
            if (!fav.pron.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(fav.pron, fontSize = 13.sp, color = SecondaryFg, maxLines = 1)
            }
        }
        if (!fav.excerpt.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                fav.excerpt,
                fontSize = 13.sp,
                color = SecondaryFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


/** 本地单用户资料弹层（原版为账号资料页；离线版只存昵称/简介）。 */
@Composable
fun MineProfileSheet(
    nick: String,
    bio: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val (_, card, titleFg) = pageColors()
    var name by remember { mutableStateOf(nick) }
    var summary by remember { mutableStateOf(bio) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickableNoRipple(onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(card)
                .clickableNoRipple { }
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                strings.profileEditTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = titleFg,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.profileNameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it },
                label = { Text(strings.profileBioLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MojiPink)
                    .clickableNoRipple {
                        scope.launch {
                            MineStore.saveProfile(name.trim(), summary.trim())
                            onSaved()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(strings.profileSave, fontSize = 15.sp, color = Color.White) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
