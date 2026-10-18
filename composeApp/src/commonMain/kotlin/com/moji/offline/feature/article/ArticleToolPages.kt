package com.moji.offline.feature.article

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.db.UserDataRepository
import com.moji.offline.data.reader.BookServer
import com.moji.offline.db.Reading_note
import com.moji.offline.resources.Res
import com.moji.offline.resources.collect_icon_jp_default
import com.moji.offline.resources.ic_common_collect
import com.moji.offline.resources.ic_common_export
import com.moji.offline.resources.ic_dialog_annotation
import com.moji.offline.resources.ic_dialog_annotation_dark
import com.moji.offline.resources.ic_member_close
import com.moji.offline.resources.img_note_empty
import com.moji.offline.ui.MojiBackButton
import com.moji.offline.ui.MojiToolbar
import com.moji.offline.ui.PlatformBackHandler
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.sharePlainText
import com.moji.offline.ui.theme.LocalDarkTheme
import com.moji.offline.ui.theme.mojiSp
import com.moji.offline.ui.webview.ComposeHtmlView
import com.moji.offline.ui.webview.WebViewController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private val NoteTitle = Color(0xFF3A3A3A)
private val NoteTitleDark = Color(0xFFFAFAFA)
private val NoteTabIdle = Color(0xFFACACAC)
private val NoteEmpty = Color(0xFF8B8787)
private val NoteEmptyDark = Color(0xFFACACAC)
private val NotePageDark = Color(0xFF0E0E11)
private val NoteSheetDark = Color(0xFF1C1C1E)
private val ExportPink = Color(0xFFE0315B)

/**
 * 阅读笔记。原版 `btnClickTrigger(3)` → `clickNote` → `ReadingNoteActivity`。
 * 单词对位 `readNote-list`（targetType 102，每页 20），例句对位 `fav-listSens`。
 * 数据在本机 `reading_note`，不请求原版接口。
 */
@Composable
fun ReadingNotePage(articleId: String, onBack: () -> Unit) {
    val dark = LocalDarkTheme.current
    val page = if (dark) NotePageDark else Color.White
    val title = if (dark) NoteTitleDark else NoteTitle
    var wordsTab by remember { mutableStateOf(true) }
    var wordNotes by remember(articleId) { mutableStateOf<List<Reading_note>>(emptyList()) }
    var sentenceNotes by remember(articleId) { mutableStateOf<List<Reading_note>>(emptyList()) }
    var reload by remember(articleId) { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<WordInfo?>(null) }
    var shareTip by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    PlatformBackHandler(enabled = detail == null, onBack = onBack)
    LaunchedEffect(articleId, reload) {
        wordNotes = UserDataRepository.wordReadingNotes(articleId)
        sentenceNotes = UserDataRepository.sentenceReadingNotes(articleId)
    }
    LaunchedEffect(shareTip) {
        if (shareTip != null) {
            delay(1600)
            shareTip = null
        }
    }
    val notes = if (wordsTab) wordNotes else sentenceNotes
    Box(Modifier.fillMaxSize().background(page)) {
        Column(Modifier.fillMaxSize()) {
            MojiToolbar(
                title = "阅读笔记",
                background = page,
                leading = { MojiBackButton(onBack) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                NoteTab("单词", wordsTab, title) { wordsTab = true }
                NoteTab("例句", !wordsTab, title) { wordsTab = false }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (notes.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painterResource(Res.drawable.img_note_empty),
                            contentDescription = null,
                            modifier = Modifier.size(168.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (wordsTab) {
                                "查询过的单词（单击查词）会记录在此"
                            } else {
                                "收藏过的例句（长按收藏）会记录在此"
                            },
                            color = if (dark) NoteEmptyDark else NoteEmpty,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(notes, key = { it.object_id }) { note ->
                            if (wordsTab) {
                                WordNoteRow(
                                    note = note,
                                    titleColor = title,
                                    onClick = {
                                        detail = WordInfo(
                                            surface = note.title,
                                            lemma = note.title,
                                            id = note.target_id,
                                        )
                                    },
                                    onDelete = {
                                        scope.launch {
                                            UserDataRepository.deleteReadingNote(note.object_id)
                                            reload++
                                        }
                                    },
                                )
                            } else {
                                SentenceNoteRow(
                                    note = note,
                                    titleColor = title,
                                    onDelete = {
                                        scope.launch {
                                            UserDataRepository.deleteReadingNote(note.object_id)
                                            reload++
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ExportPink)
                    .clickableNoRipple {
                        if (wordNotes.isEmpty() && sentenceNotes.isEmpty()) {
                            shareTip = "本篇笔记无内容分享"
                        } else {
                            sharePlainText(readingNoteExport(wordNotes, sentenceNotes))
                        }
                    }
                    .padding(horizontal = 73.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(Res.drawable.ic_common_export),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "导出笔记",
                    color = Color.White,
                    fontSize = mojiSp(17),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        shareTip?.let { text ->
            Text(
                text,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                fontSize = 13.sp,
            )
        }
        detail?.let { word ->
            WordDetailPage(word = word, onDismiss = { detail = null })
        }
    }
}

@Composable
private fun WordNoteRow(
    note: Reading_note,
    titleColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(Res.drawable.collect_icon_jp_default),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                note.title,
                color = titleColor,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.excerpt.isNotBlank()) {
                Text(
                    note.excerpt,
                    color = NoteEmpty,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (note.collected != 0L) {
            Spacer(Modifier.width(8.dp))
            Image(
                painterResource(Res.drawable.ic_common_collect),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SentenceNoteRow(
    note: Reading_note,
    titleColor: Color,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .combinedClickable(onClick = {}, onLongClick = onDelete)
            .padding(16.dp),
    ) {
        Text(
            note.title,
            color = titleColor,
            fontSize = 16.sp,
        )
        if (note.trans.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(note.trans, color = NoteEmpty, fontSize = 14.sp)
        }
    }
}

/** 本机导出，对位 `readNote-export`：两页都空才是「本篇笔记无内容分享」。 */
private fun readingNoteExport(words: List<Reading_note>, sentences: List<Reading_note>): String = buildString {
    if (words.isNotEmpty()) {
        append("单词\n")
        words.forEach { note ->
            append(note.title)
            if (note.excerpt.isNotBlank()) {
                append('\n')
                append(note.excerpt)
            }
            append('\n')
        }
    }
    if (sentences.isNotEmpty()) {
        if (isNotEmpty()) append('\n')
        append("例句\n")
        sentences.forEach { note ->
            append(note.title)
            if (note.trans.isNotBlank()) {
                append('\n')
                append(note.trans)
            }
            append('\n')
        }
    }
}.trim()

@Composable
private fun RowScope.NoteTab(label: String, selected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clickableNoRipple(onClick = onClick)
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) selectedColor else NoteTabIdle,
            fontSize = mojiSp(16),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(16.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected) ExportPink else Color.Transparent),
        )
    }
}

/**
 * 辅助阅读。原版 `btnClickTrigger(1)` → `clickAnalyse` →
 * `AnalysisDetailActivity`（工具栏标题 `assist_title`）。
 * 句子解析包（OssAnalysisZip）没有抓到本地，页面先打开原版 H5 壳。
 */
@Composable
fun AssistReadPage(onBack: () -> Unit) {
    PlatformBackHandler(onBack = onBack)
    val dark = LocalDarkTheme.current
    val page = if (dark) NotePageDark else Color.White
    var url by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val controller = remember { WebViewController() }
    LaunchedEffect(Unit) {
        val port = runCatching { BookServer.ensureStarted() }.getOrNull()
        if (port == null) {
            failure = "本机内容服务启动失败"
            return@LaunchedEffect
        }
        url = "http://127.0.0.1:$port/android_asset/web/mojiread/analysisReading/android/index.html"
    }
    Column(Modifier.fillMaxSize().background(page)) {
        MojiToolbar(
            title = "辅助阅读",
            background = page,
            leading = { MojiBackButton(onBack) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val pageUrl = url
            when {
                failure != null -> Text(
                    failure!!,
                    Modifier.align(Alignment.Center).padding(24.dp),
                    color = if (dark) NoteEmptyDark else NoteEmpty,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                pageUrl != null -> ComposeHtmlView(
                    url = pageUrl,
                    controller = controller,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * 正文里的「注」。原版 `clickArticleNote` → `DialogC1439l`
 * （`dialog_article_note`，标题是 data-text，正文是 data-content）。
 * 文案写在章节 HTML 里，不是阅读笔记列表。
 */
@Composable
fun ArticleNoteSheet(title: String, text: String, onDismiss: () -> Unit) {
    PlatformBackHandler(onBack = onDismiss)
    val dark = LocalDarkTheme.current
    val sheet = if (dark) NoteSheetDark else Color.White
    val fg = if (dark) NoteTitleDark else NoteTitle
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = source, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(sheet)
                .clickable(interactionSource = source, indication = null) {}
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (dark) Color(0xFF3B3B3B) else Color(0xFFE5E5E5)),
            )
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(
                        if (dark) Res.drawable.ic_dialog_annotation_dark
                        else Res.drawable.ic_dialog_annotation,
                    ),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 20.dp).size(24.dp),
                )
                Text(
                    title,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    color = fg,
                    fontSize = mojiSp(18),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier.size(44.dp).clickableNoRipple(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(Res.drawable.ic_member_close),
                        contentDescription = "关闭",
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                plainNote(text),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(189.dp)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                color = fg,
                fontSize = mojiSp(16),
            )
        }
    }
}

private fun plainNote(html: String): String =
    html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .trim()
