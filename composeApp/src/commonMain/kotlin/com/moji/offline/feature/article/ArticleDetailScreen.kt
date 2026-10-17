package com.moji.offline.feature.article

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.config.Secrets
import com.moji.offline.data.article.ArticleCatalog
import com.moji.offline.data.article.ArticleMeta
import com.moji.offline.data.article.ArticlePlayer
import com.moji.offline.data.reader.BookServer
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_article_auxiliary
import com.moji.offline.resources.ic_article_note
import com.moji.offline.resources.ic_common_listen_read
import com.moji.offline.resources.ic_player_menu_pause
import com.moji.offline.resources.ic_player_next
import com.moji.offline.resources.ic_player_next_unable
import com.moji.offline.resources.ic_player_menu_start
import com.moji.offline.resources.ic_player_prev
import com.moji.offline.resources.ic_player_prev_unable
import com.moji.offline.resources.ic_player_set
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.PlatformBackHandler
import com.moji.offline.ui.webview.ComposeHtmlView
import com.moji.offline.ui.webview.WebViewController
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import kotlin.math.roundToLong

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
private val SeekPlayed = Color(0xFFE0315B)
private val BarBg = Color(0xFFFFFFFF)
private val PillBg = Color(0xFFF8F8F8)
private val DarkBarBg = Color(0xFF1C1C1E)
private val DarkBarFg = Color(0xFFF2F2F2)

@Composable
fun ArticleDetailRoute(
    articleId: String,
    onBack: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onBack)
    var meta by remember(articleId) { mutableStateOf<ArticleMeta?>(null) }
    var body by remember(articleId) { mutableStateOf<String?>(null) }
    var pageUrl by remember(articleId) { mutableStateOf<String?>(null) }
    var failure by remember(articleId) { mutableStateOf<String?>(null) }

    LaunchedEffect(articleId) {
        val port = runCatching { BookServer.ensureStarted() }.getOrNull()
        if (port == null) {
            failure = "本机内容服务启动失败"
            return@LaunchedEffect
        }
        meta = ArticleCatalog.meta(articleId)
        body = ArticleCatalog.body(articleId)
        if (meta == null || body == null) {
            failure = "没有找到这篇文章的离线缓存"
            return@LaunchedEffect
        }
        pageUrl = "http://127.0.0.1:$port/android_asset/web/mojiread/article/android/index.html"
    }

    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        ArticleTopBar(
            title = meta?.columnTitle.orEmpty(),
            onBack = onBack,
        )
        val current = meta
        val html = body
        val url = pageUrl
        // Hands the track to the app-scoped session: leaving the article keeps
        // playing and the global mini player appears above the tabs.
        LaunchedEffect(current) {
            current?.let(ArticlePlayer::setTrack)
        }
        // The original article H5 is a white page; the WebView itself stays
        // transparent so the reader can share it, so paint the page here.
        Box(Modifier.weight(1f).background(Color.White)) {
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
                        onToggleAudio = { ArticlePlayer.toggle() },
                    )
            }
        }
        val m = meta
        if (m != null) ArticleAudioBar(meta = m)
    }
}

/** `activity_base_detail`: MojiToolbar 44dp + the column title at 48dp. */
@Composable
private fun ArticleTopBar(title: String, onBack: () -> Unit) {
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
            BackChevron(colors.onSurface)
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
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            MoreDots(colors.onSurface)
        }
        Spacer(Modifier.width(6.dp))
    }
}

/** The original back glyph is a 24dp chevron inside the 44dp touch target. */
@Composable
private fun BackChevron(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawLine(
            color = color,
            start = Offset(w * 0.62f, h * 0.22f),
            end = Offset(w * 0.38f, h * 0.5f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.38f, h * 0.5f),
            end = Offset(w * 0.62f, h * 0.78f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
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
    onToggleAudio: () -> Unit,
) {
    val controller = remember(meta.id) { WebViewController() }
    var stickyTools by remember(meta.id) { mutableStateOf(false) }
    val server = remember { runCatching { BookServer.baseUrl }.getOrDefault("") }
    val coverUrl = remember(meta.id) {
        when {
            meta.coverFile.isNotBlank() -> "$server/article-cover/${meta.coverFile}"
            meta.coverId.isNotBlank() ->
                "https://oss.mojidict.com/article/images/${meta.coverId}?x-oss-process=style/article"
            else -> ""
        }
    }

    ComposeHtmlView(
        url = pageUrl,
        articleBridge = true,
        controller = controller,
        modifier = Modifier.fillMaxSize(),
        onMessage = { method, payload ->
            when (method) {
                "onJsLoaded" -> controller.evaluateJs(articleInjection(meta, body, coverUrl))
                // Pink play button in the H5 header (ArticleWebView.b.changeAudioStatus).
                "changeAudioStatus" -> onToggleAudio()
                // The H5 hands over the SRT timeline after rendering
                // (ArticleJsInterface.getSRTData).
                "getSRTData" -> ArticlePlayer.setTimeline(payload)
                // Sentence tap while playing (ArticleJsInterface.onClickSRT).
                "onClickSRT", "clickSRT" ->
                    payload.toIntOrNull()?.let(ArticlePlayer::seekToIndex)
                // window.setHightLightSRT() -> Api.smoothScrollTo(offsetTop).
                "smoothScrollTo" -> {
                    val top = payload.toFloatOrNull() ?: 0f
                    controller.evaluateJs("window.scrollTo({top:$top,behavior:'smooth'})")
                }
                // Original ArticleDetailFragment.coordinateToolbox(): the native
                // 56dp toolbox shows only while scrolling up past the H5 tools
                // row; any downward scroll hides it so it never covers text.
                "articleScroll" -> {
                    val parts = payload.split('|')
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
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painterResource(Res.drawable.ic_article_auxiliary),
                contentDescription = "辅助阅读",
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(20.dp))
        }
    }
}

private fun articleInjection(meta: ArticleMeta, body: String, coverUrl: String): String {
    val options = buildJsonObject {
        put("isNeedHideTitleAndTools", false)
        put("id", meta.id)
        put("isLogin", false)
        put("theme", "theme-light")
        put("coverHidden", coverUrl.isBlank())
        put("furiganaType", 0)
        put("localizedStringLanguage", "zh-Hans")
        // "1" = collapsed translation with the original 「翻译」 label.
        put("translationState", "1")
        put("previousId", meta.previousId)
        put("nextId", meta.nextId)
        put("isBook", false)
        put("isVIP", false)
        put("isPro", true)
        put("transHideType", meta.transHideType)
        put("useJpFont", true)
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

/**
 * `layout_audio_player_manager` (article mode): 14sp times, 2dp seek track,
 * 70x38 listen-read pill, 36/40/36 transport, 24dp hexagon settings + speed.
 */
@Composable
private fun ArticleAudioBar(meta: ArticleMeta) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bg = if (dark) DarkBarBg else BarBg
    val fg = if (dark) DarkBarFg else ArticleTitle
    val sub = if (dark) Color(0xFF8B8787) else SubGray

    var showSpeed by remember(meta.id) { mutableStateOf(false) }
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
                track = SeekTrack,
                onSeek = { fraction -> ArticlePlayer.seekToFraction(fraction) },
            )
            Spacer(Modifier.width(12.dp))
            Text(formatTime(durationMs), color = sub, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(54.dp)) {
            // 精听跟读 pill (70x38, radius 16) — entry point only; the original
            // hands the article to its intensive-reading activity.
            Row(
                Modifier
                    .padding(start = 20.dp)
                    .align(Alignment.CenterStart)
                    .width(70.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PillBg),
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
                    if (loaded) Res.drawable.ic_player_prev else Res.drawable.ic_player_prev_unable,
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
                        .background(PillBg)
                        .clickableNoRipple(enabled = loaded) {
                            ArticlePlayer.toggle()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(
                            if (playing) Res.drawable.ic_player_menu_pause else Res.drawable.ic_player_menu_start,
                        ),
                        contentDescription = if (playing) "暂停" else "播放",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                PlayerIcon(
                    if (loaded) Res.drawable.ic_player_next else Res.drawable.ic_player_next_unable,
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
                    .clickableNoRipple { showSpeed = !showSpeed },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(Res.drawable.ic_player_set),
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
        if (showSpeed) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(0.6f, 0.8f, 1.0f, 1.2f, 1.4f).forEach { value ->
                    val selected = speed == value
                    Text(
                        "${formatSpeed(value)}x",
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) SeekPlayed else PillBg)
                            .clickableNoRipple {
                                ArticlePlayer.changeSpeed(value)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (selected) Color.White else fg,
                        fontSize = 14.sp,
                    )
                }
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

@Composable
private fun PlayerIcon(
    icon: org.jetbrains.compose.resources.DrawableResource,
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
