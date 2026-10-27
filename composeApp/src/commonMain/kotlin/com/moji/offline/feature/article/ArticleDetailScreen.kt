package com.moji.offline.feature.article

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.config.Secrets
import com.moji.offline.data.article.ArticleCatalog
import com.moji.offline.data.article.ArticleMeta
import com.moji.offline.data.article.ArticlePlayer
import com.moji.offline.data.article.ArticleQueue
import com.moji.offline.data.audio.SpeechEngine
import com.moji.offline.data.audio.WordAudio
import com.moji.offline.data.audio.openTtsVoiceInstaller
import com.moji.offline.data.db.UserDataRepository
import com.moji.offline.data.reader.BookServer
import com.moji.offline.data.settings.AppSettings
import com.moji.offline.data.settings.ArticleDisplaySettings
import com.moji.offline.data.word.WordDict
import com.moji.offline.data.word.WordEntry
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_article_auxiliary
import com.moji.offline.resources.ic_article_note
import com.moji.offline.resources.ic_common_listen_read
import com.moji.offline.resources.ic_player_menu_pause
import com.moji.offline.resources.ic_player_menu_pause_dark
import com.moji.offline.resources.ic_player_menu_play_dark
import com.moji.offline.resources.ic_player_menu_start
import com.moji.offline.resources.ic_player_next
import com.moji.offline.resources.ic_player_next_dark
import com.moji.offline.resources.ic_player_next_unable
import com.moji.offline.resources.ic_player_prev
import com.moji.offline.resources.ic_player_prev_dark
import com.moji.offline.resources.ic_player_prev_unable
import com.moji.offline.resources.ic_player_set
import com.moji.offline.resources.ic_player_set_dark
import org.jetbrains.compose.resources.DrawableResource
import com.moji.offline.ui.MojiBackGlyph
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.PlatformBackHandler
import com.moji.offline.ui.theme.LocalDarkTheme
import com.moji.offline.ui.webview.ComposeHtmlView
import com.moji.offline.ui.webview.WebViewController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import kotlin.math.roundToLong
import kotlin.time.Clock

/**
 * 短文详情页 — faithful port of `ArticleActivity` + `ArticleDetailFragment`
 * (base.apk 3.4.9).
 *
 * The body is the **original H5 page** (`content/h5/web/mojiread/article/android`)
 * served by [BookServer] from loopback HTTP. After `CommonJsInterface.onJsLoaded`
 * the native side calls `MOJiArticleDetailDidSetHTML(opts, body, "[]")` and
 * `MOJiArticleDetailDidUpdateCoverContainer(cover)` exactly like the original
 * `ArticleDetailFragment` (captured body.html, zero rewriting).
 */

private val ArticleTitle = Color(0xFF030303)
private val SubGray = Color(0xFFACACAC)
private val SeekTrack = Color(0xFFEDEDED)
private val SeekTrackDark = Color(0xFF3B3B3B)
private val SeekPlayed = Color(0xFFE0315B)
private val BarBg = Color(0xFFFFFFFF)
private val PillBg = Color(0xFFF8F8F8)
private val PillBgDark = Color(0xFF2C2C2F)
private val DarkBarBg = Color(0xFF1C1C1E)
private val DarkBarFg = Color(0xFFF2F2F2)
/** 对位原版 `Moji_bg_color_page_dark` / H5 `--background-color`（theme-dark）。 */
private val PageBgDark = Color(0xFF0E0E11)

@Composable
fun ArticleDetailRoute(
    articleId: String,
    queueIds: List<String> = emptyList(),
    onBack: () -> Unit,
) {
    var meta by remember(articleId) { mutableStateOf<ArticleMeta?>(null) }
    var body by remember(articleId) { mutableStateOf<String?>(null) }
    var pageUrl by remember(articleId) { mutableStateOf<String?>(null) }
    var failure by remember(articleId) { mutableStateOf<String?>(null) }
    var menuWord by remember(articleId) { mutableStateOf<WordInfo?>(null) }
    // 本篇当次查过的词条 id。对位 ArticleDetailViewModel 里那份 LinkedHashSet：
    // 同一词再查不重复写入，删掉之后这次打开也不会再补。
    val notedWordIds = remember(articleId) { mutableSetOf<String>() }
    var detailWord by remember(articleId) { mutableStateOf<WordInfo?>(null) }
    var readingNote by remember(articleId) { mutableStateOf(false) }
    var assistRead by remember(articleId) { mutableStateOf(false) }
    var inlineNote by remember(articleId) { mutableStateOf<Pair<String, String>?>(null) }
    var dictWord by remember(articleId) { mutableStateOf<WordInfo?>(null) }
    var showSettings by remember(articleId) { mutableStateOf(false) }
    var showShadowing by remember(articleId) { mutableStateOf(false) }
    var notice by remember(articleId) { mutableStateOf<String?>(null) }
    var voiceMissing by remember(articleId) { mutableStateOf(false) }
    // 更多面板（右上角 ⋮）。controller 提到这一层，面板改设置时才能把 JS
    // 推给已经加载好的 H5（对位原版 BaseDetailFragment → ArticleWebView）。
    val controller = remember(articleId) { WebViewController() }
    var h5Ready by remember(articleId) { mutableStateOf(false) }
    var showMore by remember(articleId) { mutableStateOf(false) }
    var showTransSheet by remember(articleId) { mutableStateOf(false) }
    var showFontSheet by remember(articleId) { mutableStateOf(false) }
    var favorited by remember(articleId) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val speech = remember { SpeechEngine() }
    val scope = rememberCoroutineScope()

    // 弹层打开时系统返回先关弹层；无弹层时再退出详情。
    PlatformBackHandler(enabled = true, onBack = {
        when {
            showMore -> showMore = false
            showTransSheet -> showTransSheet = false
            showFontSheet -> showFontSheet = false
            showSettings -> showSettings = false
            showShadowing -> showShadowing = false
            detailWord != null -> detailWord = null
            readingNote -> readingNote = false
            assistRead -> assistRead = false
            inlineNote != null -> inlineNote = null
            dictWord != null -> dictWord = null
            else -> onBack()
        }
    })

    DisposableEffect(articleId) {
        onDispose { speech.stop() }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(1600)
            notice = null
        }
    }

    LaunchedEffect(articleId) {
        val port = runCatching { BookServer.ensureStarted() }.getOrNull()
        if (port == null) {
            failure = "本机内容服务启动失败"
            return@LaunchedEffect
        }
        // 先读回上次的显示设置与收藏态，再放行页面加载，避免注入时用默认值闪一下。
        ArticleDisplaySettings.load()
        meta = ArticleCatalog.meta(articleId)
        body = ArticleCatalog.body(articleId)
        if (meta == null || body == null) {
            failure = "没有找到这篇文章的离线缓存"
            return@LaunchedEffect
        }
        favorited = UserDataRepository.isArticleFavorite(articleId)
        pageUrl = "http://127.0.0.1:$port/android_asset/web/mojiread/article/android/index.html"
    }

    // 设置变化 → 实时推给 H5（原版 setWebFontSize / clickTranslate / toggleSentenceAnalysis）。
    LaunchedEffect(h5Ready, ArticleDisplaySettings.fontsSize) {
        if (h5Ready) {
            controller.evaluateJs(
                "window.MOJiArticleDetailSetFontSize(" + q(ArticleDisplaySettings.fontsSize) + ")",
            )
        }
    }
    LaunchedEffect(h5Ready, ArticleDisplaySettings.translationMode) {
        if (h5Ready) {
            controller.evaluateJs(
                "window.MOJiReadingArticleDetailVCSwitchTranslation(" +
                    q(ArticleDisplaySettings.translationMode.index) + ")",
            )
        }
    }
    LaunchedEffect(h5Ready, ArticleDisplaySettings.explanationOpen) {
        if (h5Ready) {
            controller.evaluateJs(
                "window.toggleSentenceAnalysis(" +
                    (if (ArticleDisplaySettings.explanationOpen) 1 else 0) + ")",
            )
        }
    }

    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        ArticleTopBar(
            title = meta?.columnTitle.orEmpty(),
            onBack = onBack,
            onMore = { showMore = true },
        )
        val current = meta
        val html = body
        val url = pageUrl
        // Hands the track to the app-scoped session: leaving the article keeps
        // playing and the global mini player appears above the tabs.
        LaunchedEffect(current, queueIds) {
            val entry = current ?: return@LaunchedEffect
            // Plan chapters / column articles form the playback queue
            // (MOJi's `dialog_audio_player_list`). Expand when the opener
            // only passed the current id (e.g. restore / discover).
            val resolved = ArticleQueue.resolve(articleId, queueIds)
            val effective = if (resolved.isEmpty()) listOf(entry) else resolved
            ArticlePlayer.setQueue(effective, articleId)
        }
        // 开播收起词卡，对位原版播放态不再响应点词。
        LaunchedEffect(ArticlePlayer.playing) {
            if (ArticlePlayer.playing) {
                menuWord = null
                detailWord = null
                dictWord = null
            }
        }
        // H5 body 背景透明（原版 WebView 同）；页面底色由原生壳铺，
        // 深色对位 `Moji_bg_color_page_dark` / CSS `--background-color: #0e0e11`。
        val pageBg = if (LocalDarkTheme.current) PageBgDark else Color.White
        Box(Modifier.weight(1f).background(pageBg)) {
            when {
                failure != null -> Text(
                    failure!!,
                    Modifier.align(Alignment.Center),
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
                current != null && html != null && url != null ->
                    ArticleH5(
                        meta = current,
                        body = html,
                        pageUrl = url,
                        controller = controller,
                        onJsReady = { h5Ready = true },
                        onToggleAudio = { ArticlePlayer.toggle() },
                        onSelection = { word, _ -> menuWord = word },
                        onNotice = { notice = it },
                        onTool = { index ->
                            when (index) {
                                1 -> assistRead = true
                                3 -> readingNote = true
                            }
                        },
                        onArticleNote = { title, text -> inlineNote = title to text },
                        onCollectSentence = { text ->
                            scope.launch {
                                UserDataRepository.addSentenceReadingNote(articleId, text)
                                notice = "已收藏"
                            }
                        },
                        onSentenceSeek = {
                            menuWord = null
                            detailWord = null
                            dictWord = null
                        },
                    )
            }
            val selected = menuWord
            if (selected != null) {
                // The original word card stays open for every action; only the
                // cross or a tap outside closes it (checked on device).
                WordCardOverlay(
                    word = selected,
                    articleId = articleId,
                    notedWordIds = notedWordIds,
                    onRead = {
                        scope.launch {
                            val entry = WordDict.find(selected.surface, selected.lemma)
                            if (entry != null && WordAudio.speak(entry.id)) return@launch
                            if (speech.speak(entry?.spokenOr(selected.speakAs) ?: selected.speakAs)) return@launch
                            notice = "日语发音生成失败，正在检查本机语音包…"
                            voiceMissing = true
                        }
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString(selected.surface))
                        notice = "已复制"
                    },
                    onDetail = { detailWord = selected },
                    onDictionary = { dictWord = selected },
                    onDismiss = { menuWord = null },
                )
            }
            notice?.let { text ->
                Text(
                    text,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (menuWord != null) 260.dp else 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        }
        val m = meta
        if (m != null && m.hasPlayableAudio()) {
            ArticleAudioBar(
                meta = m,
                onOpenSettings = { showSettings = true },
                onOpenShadowing = { showShadowing = true },
            )
        }
    }
    if (showSettings) ArticleSettingsSheet(onDismiss = { showSettings = false })

    // 右上角 ⋮ → 更多面板（BaseDetailFragment.showArticleDetailSettingDialog）
    if (showMore) {
        MoreMenuSheet(
            explanationOpen = ArticleDisplaySettings.explanationOpen,
            favorited = favorited,
            translationMode = ArticleDisplaySettings.translationMode,
            fontSizeLevel = ArticleDisplaySettings.fontSizeLevel,
            onDismiss = { showMore = false },
            onToggleExplanation = { open ->
                showMore = false
                scope.launch { ArticleDisplaySettings.setExplanationOpen(open) }
            },
            onToggleFavorite = {
                showMore = false
                scope.launch {
                    if (favorited) {
                        UserDataRepository.removeArticleFavorite(articleId)
                        favorited = false
                    } else {
                        UserDataRepository.addArticleFavorite(
                            articleId,
                            meta?.title.orEmpty(),
                            Clock.System.now().toEpochMilliseconds(),
                        )
                        favorited = true
                    }
                }
            },
            onPickTranslationMode = {
                showMore = false
                showTransSheet = true
            },
            onPickFontSize = {
                showMore = false
                showFontSheet = true
            },
        )
    }
    if (showTransSheet) {
        TranslationModeSheet(
            selected = ArticleDisplaySettings.translationMode,
            onSelect = { mode ->
                showTransSheet = false
                scope.launch { ArticleDisplaySettings.setTranslationMode(mode) }
            },
            onDismiss = { showTransSheet = false },
        )
    }
    if (showFontSheet) {
        FontSizeSheet(
            selected = ArticleDisplaySettings.fontSizeLevel,
            onSelect = { level ->
                showFontSheet = false
                scope.launch { ArticleDisplaySettings.setFontsSize(level.size) }
            },
            onDismiss = { showFontSheet = false },
        )
    }
    if (showShadowing) ShadowingSheet(onDismiss = { showShadowing = false })
    if (voiceMissing) {
        val colors = MaterialTheme.colorScheme
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(interactionSource = source, indication = null) { voiceMissing = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .clickable(interactionSource = source, indication = null) {}
                    .padding(20.dp),
            ) {
                Text("缺少日语语音", color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "本机文字转语音没有安装日语语音包，朗读会退回中文读音。安装后再点朗读即可。",
                    color = colors.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "取消",
                        color = colors.onSurface.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickableNoRipple { voiceMissing = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Text(
                        "去安装",
                        color = ArticleTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(SeekPlayed)
                            .clickableNoRipple {
                                voiceMissing = false
                                openTtsVoiceInstaller()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
    detailWord?.let { word ->
        WordDetailPage(word = word, onDismiss = { detailWord = null })
    }
    if (readingNote) ReadingNotePage(articleId = articleId, onBack = { readingNote = false })
    if (assistRead) AssistReadPage(onBack = { assistRead = false })
    inlineNote?.let { (title, text) ->
        ArticleNoteSheet(title, text, onDismiss = { inlineNote = null })
    }
    dictWord?.let { word ->
        var entry by remember(word) { mutableStateOf<WordEntry?>(null) }
        LaunchedEffect(word) { entry = WordDict.find(word.surface, word.lemma) }
        WordDictSheet(
            word = word,
            entry = entry,
            card = if (LocalDarkTheme.current) Color(0xFF1C1C1E) else Color.White,
            fg = if (LocalDarkTheme.current) Color(0xFFF2F2F2) else ArticleTitle,
            onDismiss = { dictWord = null },
        )
    }
}

/** `activity_base_detail`: MojiToolbar 44dp + the column title at 48dp. */
@Composable
private fun ArticleTopBar(title: String, onBack: () -> Unit, onMore: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(start = 6.dp)
                .size(44.dp)
                .clickableNoRipple(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            MojiBackGlyph()
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (colors.surface.luminance() > 0.5f) ArticleTitle else colors.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .size(44.dp)
                .clickableNoRipple(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            MoreDots(colors.onSurface)
        }
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun MoreDots(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val r = 1.6.dp.toPx()
        val x = size.width / 2
        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            drawCircle(color, r, Offset(x, size.height * f))
        }
    }
}

@Composable
private fun ArticleH5(
    meta: ArticleMeta,
    body: String,
    pageUrl: String,
    controller: WebViewController,
    onJsReady: () -> Unit,
    onToggleAudio: () -> Unit,
    onSelection: (WordInfo, Float) -> Unit,
    onNotice: (String) -> Unit,
    onTool: (Int) -> Unit,
    onArticleNote: (String, String) -> Unit,
    onCollectSentence: (String) -> Unit,
    onSentenceSeek: () -> Unit = {},
) {
    var stickyTools by remember(meta.id) { mutableStateOf(false) }
    val server = remember { runCatching { BookServer.baseUrl }.getOrDefault("") }
    val mockMedia = remember { Secrets.MOCK_MEDIA_BASE.trimEnd('/') }
    val dark = LocalDarkTheme.current
    val scope = rememberCoroutineScope()
    val coverUrl = remember(meta.id) {
        when {
            meta.coverFile.isNotBlank() -> "$server/article-cover/${meta.coverFile}"
            // Never hit oss.mojidict.com directly: the mirror lives under the
            // local mock (tools/fetch_moji_article_images.py).
            meta.coverId.isNotBlank() && mockMedia.isNotBlank() ->
                "$mockMedia/article/images/${meta.coverId}"
            else -> ""
        }
    }

    ComposeHtmlView(
        url = pageUrl,
        articleBridge = true,
        controller = controller,
        modifier = Modifier.fillMaxSize(),
        onMessage = { method, payload ->
            val (bridgedMethod, bridgedPayload) = unwrapArticleBridge(method, payload)
            when (bridgedMethod) {
                "onJsLoaded" -> {
                    controller.evaluateJs(articleInjection(meta, body, coverUrl, mockMedia, dark))
                    onJsReady()
                }
                // Pink play button in the H5 header (ArticleWebView.b.changeAudioStatus).
                "changeAudioStatus" -> onToggleAudio()
                // The H5 hands over the SRT timeline after rendering
                // (ArticleJsInterface.getSRTData).
                "getSRTData" -> ArticlePlayer.setTimeline(bridgedPayload)
                // Sentence tap while playing (ArticleJsInterface.onClickSRT).
                "onClickSRT", "clickSRT" -> {
                    bridgedPayload.toIntOrNull()?.let(ArticlePlayer::seekToIndex)
                    onSentenceSeek()
                }
                // window.setHightLightSRT() -> Api.smoothScrollTo(offsetTop).
                "smoothScrollTo" -> {
                    val top = bridgedPayload.toFloatOrNull() ?: 0f
                    controller.evaluateJs("window.scrollTo({top:$top,behavior:'smooth'})")
                }
                // Text selection -> popup_article_action_mode.
                "showMenu" -> {
                    val parts = bridgedPayload.split('\u0001')
                    val raw = parts.getOrNull(0).orEmpty()
                    val top = runCatching {
                        Json.parseToJsonElement(parts.getOrNull(1).orEmpty())
                            .jsonObject["top"]!!.jsonPrimitive.float
                    }.getOrDefault(0f)
                    val word = parseWordInfo(raw)
                    println("MOJI_PARSED surface=${word.surface} reading=${word.reading.map { it.code }} lemma=${word.lemma}")
                    if (word.surface.isNotBlank()) onSelection(word, top)
                }
                // H5 ge(articleId, isLogin) only binds option clicks after a
                // not-yet-voted status. Offline never tallies: 投票 just opens
                // the answer already stored in .voter_description.
                "getVoteStatus" -> {
                    val ids = voteIds(bridgedPayload)
                    scope.launch {
                        val json = voteStatusJson(ids)
                        val opened = openedVoteIds(ids)
                        controller.evaluateJs(
                            "javascript:updateVotes(${jsSingleQuoted(json)});${revealQuizJs(opened)}",
                        )
                    }
                }
                "actVote" -> {
                    val voted = parseActVote(bridgedPayload)
                    if (voted != null) {
                        scope.launch {
                            UserDataRepository.putSetting(voteSettingKey(voted.id), "1")
                            controller.evaluateJs("javascript:${revealQuizJs(listOf(voted.id))}")
                        }
                    }
                }
                "voteErrorTips" -> if (bridgedPayload.isNotBlank()) onNotice(bridgedPayload)
                // 工具栏图标：0 精读高亮、1 辅助阅读、3 阅读笔记
                // （ArticleDetailFragment.initView$lambda$32$lambda$6）。
                "btnClickTrigger" -> bridgedPayload.toIntOrNull()?.let(onTool)
                // 正文里的「注」：clickArticleNote(data-text, data-content)。
                "clickArticleNote" -> {
                    val parts = bridgedPayload.split('\u0001')
                    val title = parts.getOrNull(0).orEmpty()
                    val text = parts.getOrNull(1).orEmpty()
                    if (title.isNotBlank() || text.isNotBlank()) onArticleNote(title, text)
                }
                // 长按句子收藏，对位选区菜单里的「收藏」→ fav-addManySens。
                "collectSentence" -> {
                    val text = bridgedPayload.trim()
                    if (text.isNotBlank()) onCollectSentence(text)
                }
                // Toolbox shows only while scrolling up past the H5 tools row.
                "articleScroll" -> {
                    val parts = bridgedPayload.split('|')
                    val top = parts.getOrNull(0)?.toFloatOrNull()
                    val direction = parts.getOrNull(1)
                    if (top != null && direction != null) {
                        stickyTools = top <= 54f && direction == "up"
                    }
                }
                else -> Unit
            }
        },
    )
    // Original playback follows the SRT: the current sentence gets `--active`
    // and the H5 scrolls to it (the highlight function requests the scroll).
    LaunchedEffect(ArticlePlayer.highlightIndex) {
        val index = ArticlePlayer.highlightIndex
        if (index >= 0) controller.evaluateJs("window.setHightLightSRT($index)")
    }
    // The H5 only forwards sentence taps while `isPlayingAudio` is true
    // (window.setAudioStatus('1'|'0'), called by the original audio VM).
    LaunchedEffect(ArticlePlayer.playing) {
        controller.evaluateJs("window.setAudioStatus('${if (ArticlePlayer.playing) 1 else 0}')")
    }
    if (stickyTools) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.White),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(Res.drawable.ic_article_note),
                contentDescription = "笔记",
                modifier = Modifier.size(36.dp).clickableNoRipple { onTool(3) },
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painterResource(Res.drawable.ic_article_auxiliary),
                contentDescription = "辅助阅读",
                modifier = Modifier.size(36.dp).clickableNoRipple { onTool(1) },
            )
            Spacer(Modifier.width(20.dp))
        }
    }
}

private fun articleInjection(
    meta: ArticleMeta,
    body: String,
    coverUrl: String,
    mockMedia: String,
    dark: Boolean,
): String {
    val options = buildJsonObject {
        put("isNeedHideTitleAndTools", false)
        put("id", meta.id)
        // ge(articleId, isLogin) only requests vote status when this is true.
        // Offline has no account wall; a false value leaves the voter on
        // gotoLogin and never binds the options or the 投票 button.
        put("isLogin", true)
        put("theme", if (dark) "theme-dark" else "theme-light")
        put("coverHidden", coverUrl.isBlank())
        put("furiganaType", AppSettings.spell.code)
        put("localizedStringLanguage", AppSettings.language.tag)
        // "1" = collapsed translation with the original 「翻译」 label.
        put("translationState", "1")
        put("previousId", meta.previousId)
        put("nextId", meta.nextId)
        put("isBook", false)
        put("isVIP", false)
        put("isPro", true)
        put("transHideType", meta.transHideType)
        put("useJpFont", AppSettings.useJpFont)
        put("fontSize", 1.0)
        put("tags", "")
        put("isReadSuc", false)
        put("freeTransNum", 999)
        put("aiEnable", false)
        put("isOpenSentenceAnalysis", 1)
        put("title", meta.title)
        put("userName", meta.authorName)
        put("username", meta.authorName)
        put("updatedAt", meta.publishedAt)
        put("showPlayerButton", meta.audioId.isNotBlank())
        put("commentedNum", 0)
        put("showComment", false)
    }.toString()
    return buildString {
        append("(function(){try{")
        append("window.MOJiArticleDetailDidSetHTML(")
        append(jsString(options)).append(',').append(jsString(body)).append(",\"[]\");")
        if (coverUrl.isNotBlank()) {
            append("window.MOJiArticleDetailDidUpdateCoverContainer(")
            append(jsString(coverUrl)).append(");")
        }
        // ArticleWebView.z(i): show the note (3) and auxiliary (1) tools.
        append("if(window.MOJiArticleToolsCtrl){")
        append("window.MOJiArticleToolsCtrl('1','true');")
        append("window.MOJiArticleToolsCtrl('3','true');")
        append("}")
        // Report the tools row position while scrolling, like the native
        // onScrollChange listener feeding coordinateToolbox().
        append("var __t=document.getElementById('article-tools');")
        append("var __lastY=window.pageYOffset||0;")
        append("var __send=function(){try{")
        append("var __y=window.pageYOffset||0;")
        append("var __top=__t.getBoundingClientRect().top;")
        append("window.ArticleJsInterface.articleScroll(__top+'|'+(__y>__lastY?'down':'up'));")
        append("__lastY=__y;")
        append("}catch(e){}};")
        append("window.addEventListener('scroll',__send,{passive:true});__send();")
        // 注音只给词卡在没有 pron 时兜底显示。点词查找用词面，对位 clickContent。
        append("function __rubyOf(__n){var n=__n;while(n&&n.nodeType===3)n=n.parentNode;")
        append("while(n&&n!==document.body){if(n.nodeName==='RUBY')return n;n=n.parentNode;}return null;}")
        append("function __wordInfo(__n){var o={surface:'',reading:'',lemma:''};")
        append("try{var ruby=__rubyOf(__n);")
        append("if(ruby){var __b=ruby.querySelector('rb'),__t=ruby.querySelector('rt');")
        append("o.surface=__b?__b.textContent:'';")
        append("o.reading=__t?(__t.getAttribute('hiragana')||''):'';")
        append("o.lemma=__t?(__t.getAttribute('lemma')||''):'';}")
        append("else if(__n&&__n.nodeName==='SPAN'&&__n.className&&(''+__n.className).indexOf('moji-toolkit-org')>=0){")
        append("o.surface=__n.innerText;o.lemma=__n.getAttribute('lemma')||'';}")
        append("}catch(e){}")
        append("if(!o.lemma)o.lemma=o.surface;")
        append("return o;}")
        // 长按句子收藏。原版是选区菜单的「收藏」再确认；空态文案是「长按收藏」。
        // 取最近的字幕句，没有字幕时退回所在段落，去掉注音。
        append("var __justCollected=0;")
        append("function __plainText(__node){var t='',w=document.createTreeWalker(__node,NodeFilter.SHOW_TEXT,null),n;")
        append("while((n=w.nextNode())){var p=n.parentNode,skip=false;")
        append("while(p&&p!==__node){var nm=p.nodeName;if(nm==='RT'||nm==='RP'){skip=true;break;}p=p.parentNode;}")
        append("if(!skip)t+=n.nodeValue;}return t.replace(/\\s+/g,' ').trim();}")
        append("function __sentenceNode(__t){var n=__t;")
        append("while(n&&n!==document.body){if(n.getAttribute&&n.getAttribute('srt-start'))return n;n=n.parentNode;}")
        append("n=__t;while(n&&n!==document.body){if(n.nodeName==='P')return n;n=n.parentNode;}return null;}")
        append("function __collectSentence(__t){try{")
        append("var node=__sentenceNode(__t);if(!node)return;")
        append("var text=__plainText(node);if(!text||text.length<2)return;")
        append("__justCollected=Date.now();")
        append("try{window.getSelection().removeAllRanges();}catch(e){}")
        append("window.ArticleJsInterface.collectSentence(text);")
        append("}catch(e){}}")
        append("var __hold;")
        // Text selection / 点词 → showMenu。门禁对齐原版 H5 `ie`：
        // 播放中：只有点在当前句.--active 上才弹词卡；点其它字幕句只走 onClickSRT。
        // 注意时序：H5 点非当前句会 g(clickSRT) 再 setTimeout(q) 把 --active 挪过去；
        // 若 selectionchange 在跳播后仍开卡，会误判「已是当前句」。播放中禁用选区弹卡。
        append("function __under(t,cls){var n=t;")
        append("while(n&&n!==document&&n!==document.documentElement){")
        append("if(n.classList&&n.classList.contains(cls))return true;n=n.parentNode;}")
        append("return false;}")
        append("function __blockWordWhilePlay(t){")
        append("if(!window.isPlayingAudio)return false;")
        // 对位 ie: dt(.moji-article-srt) && playing && !parent(--active)
        append("return __under(t,'moji-article-srt')&&!__under(t,'--active');}")
        append("var __selT,__suppressWordUntil=0;")
        append("document.addEventListener('selectionchange',function(){")
        append("clearTimeout(__selT);")
        append("__selT=setTimeout(function(){try{")
        // 播放中点词只认 click；选区会在 onClickSRT 挪 --active 之后误弹。
        append("if(window.isPlayingAudio)return;")
        append("if(Date.now()<__suppressWordUntil)return;")
        append("if(Date.now()-__justCollected<800)return;")
        append("var __s=window.getSelection();if(!__s||__s.rangeCount<1)return;")
        append("var __node=__s.anchorNode;var __el=__node&&__node.nodeType===1?__node:__node&&__node.parentNode;")
        append("var __wi=__wordInfo(__el);var __tx=__s.toString().trim();")
        append("if(!__wi.surface)__wi={surface:__tx,reading:'',lemma:__tx};")
        append("if(!__wi.surface||__wi.surface.length>12)return;")
        append("var __r=__s.getRangeAt(0).getBoundingClientRect();")
        append("window.ArticleJsInterface.showMenu(JSON.stringify(__wi),JSON.stringify({top:__r.top,bottom:__r.bottom,left:__r.left,right:__r.right}));")
        append("}catch(e){}},60);")
        append("},true);")
        // Word tap: capture 里按「点击瞬间」的 --active 判断；非当前句清选区并短抑弹卡。
        append("var __ct=document.getElementById('article-content');")
        append("var __hx=0,__hy=0;")
        append("__ct&&__ct.addEventListener('touchstart',function(e){clearTimeout(__hold);var t=e.target;")
        append("var p=e.touches&&e.touches[0];__hx=p?p.clientX:0;__hy=p?p.clientY:0;")
        append("__hold=setTimeout(function(){__collectSentence(t);},550);},{passive:true});")
        append("__ct&&__ct.addEventListener('touchend',function(){clearTimeout(__hold);},{passive:true});")
        append("__ct&&__ct.addEventListener('touchcancel',function(){clearTimeout(__hold);},{passive:true});")
        append("__ct&&__ct.addEventListener('touchmove',function(e){var p=e.touches&&e.touches[0];if(!p)return;")
        append("if(Math.abs(p.clientX-__hx)>12||Math.abs(p.clientY-__hy)>12)clearTimeout(__hold);},{passive:true});")
        append("__ct&&__ct.addEventListener('click',function(e){")
        append("try{")
        append("if(__blockWordWhilePlay(e.target)){")
        append("__suppressWordUntil=Date.now()+500;")
        append("try{window.getSelection().removeAllRanges();}catch(x){}")
        append("return;}")
        append("if(Date.now()<__suppressWordUntil)return;")
        append("if(Date.now()-__justCollected<800)return;")
        append("var __wi=__wordInfo(e.target);")
        append("if(!__wi||!__wi.surface||__wi.surface.length>12)return;")
        append("var __rr=e.target.getBoundingClientRect();")
        append("window.ArticleJsInterface.showMenu(JSON.stringify(__wi),JSON.stringify({top:__rr.top,bottom:__rr.bottom,left:__rr.left,right:__rr.right}));")
        append("}catch(e){}},true);")
        // The captured body embeds absolute https://oss.mojidict.com/article/
        // images URLs. Rewrite their origin to the loopback mock (mirrored by
        // tools/fetch_moji_article_images.py); with no mock configured the
        // images are blanked instead of hitting the original CDN.
        append("var __oss='https://oss.mojidict.com/';")
        append("var __mock=")
        append(jsString(if (mockMedia.isBlank()) "" else "$mockMedia/"))
        append(';')
        append("function __fixImg(root){if(!root||!root.querySelectorAll)return;")
        append("root.querySelectorAll('img').forEach(function(im){")
        append("var s=im.getAttribute('src')||'';")
        append("if(s.indexOf(__oss)===0){")
        append("im.setAttribute('src',(__mock||'data:image/gif;base64,R0lGODlhAQABAAAAACw=')+s.slice(__oss.length));")
        append("}});}")
        append("var __imgRoot=document.getElementById('article-content');")
        append("__fixImg(__imgRoot);")
        append("try{var __i=__imgRoot.querySelector('img');")
        append("window.ArticleJsInterface.debugLog('img='+(__i?__i.getAttribute('src'):'none'));}catch(e){}")
        append("try{new MutationObserver(function(){__fixImg(__imgRoot);})")
        append(".observe(__imgRoot,{childList:true,subtree:true});}catch(e){}")
        append("}catch(e){console.error('MojiInject',e&&e.message)}})();")
    }
}

private fun jsString(value: String): String = buildString {
    append('"')
    value.forEach { c ->
        when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c.code < 0x20 || c == '\u2028' || c == '\u2029' ->
                append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
    }
    append('"')
}

/** H5 接口习惯收单引号字面量（`MOJiArticleDetailSetFontSize('1.2')`）。 */
private fun q(value: Any): String = "'$value'"

/** Bottom player shows only when the article has a real audio id (o5.W.d). */
private fun ArticleMeta.hasPlayableAudio(): Boolean =
    audioId.isNotBlank() && audioId != "null"

/**
 * iOS posts `{name, args}` on one `articleBridge` channel. Android already
 * arrives as the method name plus `\u0001`-joined arguments.
 */
private fun unwrapArticleBridge(method: String, payload: String): Pair<String, String> {
    if (method != "articleBridge") return method to payload
    val obj = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull()
        ?: return method to payload
    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return method to payload
    val args = obj["args"] as? JsonArray ?: return name to ""
    val joined = args.joinToString("\u0001") { arg ->
        when (arg) {
            is JsonArray -> arg.joinToString(",") { it.jsonPrimitive.content }
            is JsonPrimitive -> arg.content
            else -> ""
        }
    }
    return name to joined
}

private data class LocalVote(val id: String, val options: List<Int>)

private fun voteSettingKey(voteId: String) = "article_vote_$voteId"

private fun voteIds(payload: String): List<String> =
    payload.split('\u0001')
        .getOrNull(1)
        .orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.all { ch -> ch.isLetterOrDigit() } }

/** Always "not voted", so the H5 binds clicks and does not draw tallies. */
private fun voteStatusJson(ids: List<String>): String =
    JsonArray(ids.map { voteEntity(it) }).toString()

private suspend fun openedVoteIds(ids: List<String>): List<String> =
    ids.filter { UserDataRepository.setting(voteSettingKey(it))?.isNotBlank() == true }

/**
 * Shape consumed by H5 `updateVotes` before a choice.
 * `votedNums` must be an object: the binder indexes `votedNums[i]` and throws
 * if the field is missing, so the options never get a click. `isVoted` stays
 * false — a true value is what draws 票数 and 「共 N 人参与」.
 */
private fun voteEntity(id: String): JsonElement = buildJsonObject {
    put("objectId", id)
    put("isVoted", false)
    put("answer", buildJsonObject { put("options", buildJsonArray {}) })
    put("votedNums", buildJsonObject {})
    put("voterNum", 0)
}

/**
 * Opens the answer block already in the article HTML and marks the option
 * named by 「正确答案」. Does not add vote counts or a participant line.
 */
private fun revealQuizJs(ids: List<String>): String {
    val list = ids.joinToString(",") { "'$it'" }
    return """
        (function(){
          [$list].forEach(function(id){
            var e=document.querySelector('.mojiVote-'+id);
            if(!e) return;
            e.querySelectorAll('.voter_option').forEach(function(d){
              d.classList.add('pointer_events_none');
            });
            var btn=e.querySelector('.voter_button');
            if(btn) btn.style.display='none';
            e.querySelectorAll('.voter_number,.voter_rate,.voter_progress,.voter_totalNumber')
              .forEach(function(n){ n.remove(); });
            var desc=e.querySelector('.voter_description');
            if(desc){ desc.style.visibility='visible'; desc.style.height='auto'; }
            var raw=desc?(desc.innerText||''):'';
            var m=raw.match(/正确答案[:：]\s*([0-9０-９]+(?:\s*[、,，]\s*[0-9０-９]+)*)/);
            if(!m) return;
            m[1].split(/[、,，]/).forEach(function(s){
              var n=parseInt(s.replace(/[０-９]/g,function(c){
                return String.fromCharCode(c.charCodeAt(0)-65248);
              }),10);
              if(!n) return;
              var opt=e.querySelector('.voter_option[data-value="'+(n-1)+'"]');
              if(opt) opt.style.background='rgba(255,58,58,0.08)';
            });
          });
        })()
    """.trimIndent()
}

private fun parseActVote(raw: String): LocalVote? {
    val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    val id = obj["voteId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (id.isEmpty()) return null
    val options = (obj["options"] as? JsonArray)?.mapNotNull { element ->
        element.jsonPrimitive.content.toIntOrNull()
    }.orEmpty()
    return LocalVote(id, options)
}

/** Embed JSON inside the single-quoted argument original evaluateJavascript uses. */
private fun jsSingleQuoted(value: String): String = buildString {
    append('\'')
    value.forEach { c ->
        when (c) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            '\n', '\r' -> append(' ')
            else -> append(c)
        }
    }
    append('\'')
}

/** `{surface, reading, lemma}` from the injected script; plain text fallback. */
private fun parseWordInfo(raw: String): WordInfo {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("{")) return WordInfo(trimmed)
    val obj = runCatching { Json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
        ?: return WordInfo(trimmed)
    fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return WordInfo(
        surface = str("surface").ifBlank { trimmed },
        reading = str("reading"),
        lemma = str("lemma"),
    )
}

/**
 * `layout_audio_player_manager` (article mode): 14sp times, 2dp seek track,
 * 70x38 listen-read pill, 36/40/36 transport, 24dp hexagon settings + speed.
 */
@Composable
private fun ArticleAudioBar(
    meta: ArticleMeta,
    onOpenSettings: () -> Unit,
    onOpenShadowing: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val bg = if (dark) DarkBarBg else BarBg
    val fg = if (dark) DarkBarFg else ArticleTitle
    val sub = if (dark) Color(0xFF8B8787) else SubGray
    val pill = if (dark) PillBgDark else PillBg
    val seekTrack = if (dark) SeekTrackDark else SeekTrack

    val durationMs = ArticlePlayer.durationMs
    val positionMs = ArticlePlayer.positionMs
    val loaded = ArticlePlayer.loaded
    val playing = ArticlePlayer.playing
    val speed = ArticlePlayer.speed

    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatTime(positionMs),
                color = if (dark) fg else Color(0xFF3A3A3A),
                fontSize = 14.sp,
            )
            Spacer(Modifier.width(12.dp))
            SeekBar(
                modifier = Modifier.weight(1f),
                progress = if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                },
                enabled = loaded,
                played = SeekPlayed,
                track = seekTrack,
                onSeek = { fraction -> ArticlePlayer.seekToFraction(fraction) },
            )
            Spacer(Modifier.width(12.dp))
            Text(formatTime(durationMs), color = sub, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(54.dp)) {
            // 精听跟读 pill (70x38, radius 16) — entry point only; the original
            // hands the article to its intensive-reading activity.
            // 原版 `ic_common_listen_read` 无 _dark 资源（深灰笔画），深色下
            // 仍用浅底 pill，对位原版在深色壳上保留浅色胶囊的做法。
            Row(
                Modifier
                    .padding(start = 20.dp)
                    .align(Alignment.CenterStart)
                    .width(70.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PillBg)
                    .clickableNoRipple(onClick = onOpenShadowing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painterResource(Res.drawable.ic_common_listen_read),
                    contentDescription = "精听跟读",
                    modifier = Modifier.width(54.dp).height(26.dp),
                )
            }
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIcon(
                    when {
                        !loaded -> Res.drawable.ic_player_prev_unable
                        dark -> Res.drawable.ic_player_prev_dark
                        else -> Res.drawable.ic_player_prev
                    },
                    "上一句",
                    enabled = loaded,
                    size = 36.dp,
                    padding = 10.dp,
                ) { ArticlePlayer.seekToFraction(0f) }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(pill)
                        .clickableNoRipple(enabled = loaded) {
                            ArticlePlayer.toggle()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(playPauseIcon(playing = playing, dark = dark)),
                        contentDescription = if (playing) "暂停" else "播放",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                PlayerIcon(
                    when {
                        !loaded -> Res.drawable.ic_player_next_unable
                        dark -> Res.drawable.ic_player_next_dark
                        else -> Res.drawable.ic_player_next
                    },
                    "下一句",
                    enabled = loaded,
                    size = 36.dp,
                    padding = 10.dp,
                ) { ArticlePlayer.seekToFraction(1f) }
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp)
                    .size(40.dp)
                    .clickableNoRipple(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(
                        if (dark) Res.drawable.ic_player_set_dark else Res.drawable.ic_player_set,
                    ),
                    contentDescription = "播放设置",
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    "${formatSpeed(speed)}x",
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp),
                    fontSize = 10.sp,
                    color = fg,
                )
            }
        }
    }
}

@Composable
private fun SeekBar(
    modifier: Modifier,
    progress: Float,
    enabled: Boolean,
    played: Color,
    track: Color,
    onSeek: (Float) -> Unit,
) {
    var widthPx by remember { mutableStateOf(1) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: progress
    Box(
        modifier
            .height(20.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> onSeek((offset.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        drag?.let(onSeek)
                        drag = null
                    },
                    onHorizontalDrag = { change, amount ->
                        val next = ((drag ?: progress) + amount / widthPx).coerceIn(0f, 1f)
                        drag = next
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            drawLine(track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height)
            drawLine(
                played,
                Offset(0f, size.height / 2),
                Offset(size.width * shown, size.height / 2),
                size.height,
                cap = StrokeCap.Round,
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(played, radius = 5.dp.toPx(), center = Offset(size.width * shown, size.height / 2))
        }
    }
}

/** 对位原版 `ic_player_menu_{pause,start}` / `_play_dark` / `_pause_dark`。 */
private fun playPauseIcon(playing: Boolean, dark: Boolean): DrawableResource = when {
    playing && dark -> Res.drawable.ic_player_menu_pause_dark
    playing -> Res.drawable.ic_player_menu_pause
    dark -> Res.drawable.ic_player_menu_play_dark
    else -> Res.drawable.ic_player_menu_start
}

@Composable
private fun PlayerIcon(
    icon: DrawableResource,
    description: String,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    padding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Image(
        painterResource(icon),
        contentDescription = description,
        modifier = Modifier
            .size(size)
            .padding(padding)
            .clickableNoRipple(enabled = enabled, onClick = onClick),
    )
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
