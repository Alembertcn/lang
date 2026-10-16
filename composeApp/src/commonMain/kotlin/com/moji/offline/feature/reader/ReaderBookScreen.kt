package com.moji.offline.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moji.offline.data.catalog.ReaderBook
import com.moji.offline.data.reader.BookPackage
import com.moji.offline.data.reader.BookServer
import com.moji.offline.data.reader.ReaderChapter
import com.moji.offline.resources.Res
import com.moji.offline.ui.i18n.LocalAppStrings
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.LocalDarkTheme
import com.moji.offline.ui.webview.ComposeHtmlView
import com.moji.offline.ui.webview.WebViewController
import com.moji.offline.ui.webview.platformName
import kotlinx.coroutines.launch

private enum class ReaderBarIcon { Back, Toc, More }

/**
 * Original-path book reader (mirrors MOJi v3.4.9):
 *
 *  - the unpacked epub is served byte-for-byte by a loopback HTTP server
 *    ([BookServer], the NanoHTTPD equivalent);
 *  - each chapter is the epub's own XHTML, loaded with
 *    `loadDataWithBaseURL("http://127.0.0.1:{port}/books/{id}/{chapterDir}")`
 *    so `index.css`, `flow*.css`, images and the engine scripts referenced by
 *    the chapter resolve from the server — zero content rewriting;
 *  - outer pager = one chapter per page (ReaderPageFragment), inner pager =
 *    pages inside the chapter (ReaderWebViewPager), and pager motion is
 *    pixel-synced into the WebView (the original `onPageScrolled → scrollTo`).
 */
@Composable
fun ReaderBookRoute(
    book: ReaderBook,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current
    var chapters by remember(book.id) { mutableStateOf<List<ReaderChapter>>(emptyList()) }
    var showToc by remember { mutableStateOf(false) }
    var pageLabel by remember { mutableStateOf("") }

    val chapterPager = rememberPagerState(pageCount = { chapters.size.coerceAtLeast(1) })

    LaunchedEffect(book.id) {
        runCatching { BookServer.ensureStarted() }
        chapters = BookPackage.load(book.id)?.chapters.orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colors.background)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderBarIcon(ReaderBarIcon.Back, onBack)
            Spacer(Modifier.weight(1f))
            ReaderBarIcon(ReaderBarIcon.Toc) { showToc = true }
            ReaderBarIcon(ReaderBarIcon.More) { /* 字号/主题设置（后续） */ }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (chapters.isEmpty()) {
                Text(
                    strings.readerLoading,
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 14.sp,
                    color = colors.onBackground.copy(alpha = 0.5f),
                )
            } else {
                HorizontalPager(
                    state = chapterPager,
                    modifier = Modifier.fillMaxSize(),
                ) { chapterIndex ->
                    ChapterPage(
                        bookId = book.id,
                        chapter = chapters[chapterIndex],
                        isActive = chapterPager.currentPage == chapterIndex,
                        onPageInfo = { current, total ->
                            pageLabel = if (total > 0) "$current/$total" else ""
                        },
                    )
                }
            }
            if (pageLabel.isNotEmpty()) {
                Text(
                    pageLabel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = colors.onBackground.copy(alpha = 0.4f),
                )
            }
        }
    }

    if (showToc) {
        TocDialog(
            chapters = chapters,
            onDismiss = { showToc = false },
            onSelect = { index ->
                showToc = false
                scope.launch {
                    chapterPager.animateScrollToPage(index.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
                }
            },
        )
    }
}

/** One chapter: the epub's own XHTML in its WebView, paged by the original engine. */
@Composable
private fun ChapterPage(
    bookId: String,
    chapter: ReaderChapter,
    isActive: Boolean,
    onPageInfo: (current: Int, total: Int) -> Unit,
) {
    val dark = LocalDarkTheme.current
    val controller = remember(chapter.index) { WebViewController() }
    var html by remember(chapter.index) { mutableStateOf<String?>(null) }
    var totalPages by remember(chapter.index) { mutableStateOf(0) }

    val pagerState = rememberPagerState(pageCount = { totalPages.coerceAtLeast(1) })

    LaunchedEffect(bookId, chapter.index) {
        html = runCatching {
            Res.readBytes("files/books/${bookId}/${chapter.resourcePath}").decodeToString()
        }.getOrNull()
    }

    LaunchedEffect(pagerState, totalPages, isActive) {
        snapshotFlow { pagerState.currentPage }.collect { current ->
            if (isActive) onPageInfo(current + 1, totalPages)
        }
    }

    val analysisBg = if (dark) "#1B1D1C" else "#FFF7E6"
    val analysisFg = if (dark) "#B9C7A8" else "#8B6914"
    val contentColor = if (dark) "#C9C6C0" else "#2B2B2B"

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val page = html
        if (page != null) {
            key(page) {
                ComposeHtmlView(
                    html = page,
                    baseUrl = "${BookServer.baseUrl}/books/$bookId/${chapter.dir}",
                    controller = controller,
                    onMessage = { method, payload ->
                        when (method) {
                            "onJsLoaded" -> {
                                val info = "{\"analysisBgColor\":\"$analysisBg\"," +
                                    "\"analysisColor\":\"$analysisFg\"," +
                                    "\"contentColor\":\"$contentColor\"," +
                                    "\"furiganaType\":2,\"fontSize\":1," +
                                    // Original reader shows the 「析」 paragraph
                                    // markers; they also occupy inline space on the
                                    // last line of a paragraph, which affects line
                                    // wrapping / pagination parity.
                                    "\"analysisParagraphButton\":true" +
                                    if (dark) ",\"themeName\":\"theme-dark\"}" else "}"
                                controller.evaluateJs(
                                    "javascript:Main('$info', '$platformName', '', '')",
                                )
                            }
                            "setHorizontalPageCount" -> {
                                // The engine sends Int.MAX_VALUE while it is
                                // measured at zero width (offscreen pages);
                                // ignore anything outside the sane range.
                                payload.toIntOrNull()?.takeIf { it in 1..2000 }?.let { engineCount ->
                                    // Chrome stretches CSS columns when the
                                    // container is not an exact multiple of the
                                    // page step, while the engine (and the
                                    // original reader) assume step == clientWidth.
                                    // Snap the container onto the exact grid and
                                    // use the real column count.
                                    controller.evaluateJsWithResult(snapGridJs(engineCount)) { raw ->
                                        val n = Regex("\"n\":(\\d+)")
                                            .find(raw.replace("\\\"", "\""))
                                            ?.groupValues?.get(1)?.toIntOrNull()
                                        totalPages = n?.takeIf { it in 1..2000 } ?: engineCount
                                        println("[MojiGeo] snap engine=$engineCount $raw")
                                    }
                                }
                            }
                            else -> Unit
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Gesture master for the pages inside this chapter.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { /* transparent page */ }

        // Pixel-sync pager motion into the WebView — exact port of the original
        // `X4.a.onPageScrolled` + `ReaderWebView2.onLayout` math:
        //   w   = ceil(viewWidthPx / density) * density   (snap to whole CSS px)
        //   x   = ceil(page * w) + offsetPx
        val density = with(LocalDensity.current) { this.density }
        val step = kotlin.math.ceil(pageWidthPx / density) * density
        LaunchedEffect(pagerState, pageWidthPx, step) {
            snapshotFlow {
                pagerState.currentPage to pagerState.currentPageOffsetFraction
            }.collect { (pageIndex, offsetFraction) ->
                val x = kotlin.math.ceil(pageIndex * step) + offsetFraction * pageWidthPx
                controller.scrollWebViewTo(x)
            }
        }

        // Geometry probe (temporary, [MojiGeo]): dump the real column geometry
        // whenever the pager settles, so alignment fixes are based on facts.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPage }
                .collect { (inProgress, page) ->
                    if (!inProgress) {
                        controller.evaluateJsWithResult(GEO_JS) { raw ->
                            println("[MojiGeo] page=$page nativeStep=$step $raw")
                        }
                    }
                }
        }
    }
}

private const val GEO_JS =
    "(function(){var b=document.body;var cs=getComputedStyle(b);" +
        "var sx=window.scrollX||document.documentElement.scrollLeft||0;" +
        "var cw=document.documentElement.clientWidth;" +
        "var sw=document.documentElement.scrollWidth;" +
        "var els=document.querySelectorAll('p,div,h1,h2,h3,h4,h5,h6,img,section');" +
        "var seen={},lefts=[];" +
        "for(var i=0;i<els.length;i++){var r=els[i].getBoundingClientRect();" +
        "if(r.width<2&&r.height<2)continue;" +
        "var k=Math.round(r.left+sx);if(!(k in seen)){seen[k]=1;lefts.push(k);}}" +
        "lefts.sort(function(a,b){return a-b});" +
        "var diffs=[];for(var i=1;i<lefts.length;i++){var d=lefts[i]-lefts[i-1];if(d>40)diffs.push(d);}" +
        "diffs.sort(function(a,b){return a-b});" +
        "var step=diffs.length?diffs[Math.floor(diffs.length/2)]:0;" +
        "return JSON.stringify({cw:cw,sw:sw,sx:Math.round(sx)," +
        "padL:parseFloat(cs.paddingLeft)||0,padR:parseFloat(cs.paddingRight)||0," +
        "colW:parseFloat(cs.columnWidth)||0,colGap:parseFloat(cs.columnGap)||0," +
        "lefts:lefts.slice(0,8),step:step,dpr:window.devicePixelRatio});})()"

/**
 * The engine lays text out as CSS columns and assumes one page step equals
 * `document.documentElement.clientWidth`. Chrome only honours that when the
 * scrolling container is an exact multiple of the step — otherwise it stretches
 * the columns (step becomes `columnWidth + columnGap` > clientWidth) and page
 * boundaries stop matching the native pager. This snaps the container width onto
 * the exact grid and returns the true column count (measured on that grid).
 */
private fun snapGridJs(engineCount: Int): String =
    "(function(){" +
        "var d=document.documentElement;var cw=d.clientWidth;" +
        // Narrow the container to one step so the columns overflow it and
        // scrollWidth reports the true content extent (N * cw - gap).
        "d.style.width=cw+'px';" +
        "var sw=d.scrollWidth;" +
        "var n=Math.max(1,Math.round(sw/cw));" +
        "d.style.width=(n*cw)+'px';" +
        "return JSON.stringify({cw:cw,sw:sw,n:n});})()"

@Composable
private fun ReaderBarIcon(icon: ReaderBarIcon, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickableNoRipple(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(19.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.7.dp.toPx()
            when (icon) {
                ReaderBarIcon.Back -> {
                    drawLine(color, Offset(w * 0.64f, h * 0.10f), Offset(w * 0.18f, h * 0.5f), stroke, StrokeCap.Round)
                    drawLine(color, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.64f, h * 0.90f), stroke, StrokeCap.Round)
                }
                ReaderBarIcon.Toc -> {
                    for (i in 0..2) {
                        val y = h * (0.22f + i * 0.28f)
                        drawLine(color, Offset(w * 0.10f, y), Offset(w * 0.90f, y), stroke, StrokeCap.Round)
                    }
                }
                ReaderBarIcon.More -> {
                    for (i in 0..2) {
                        drawCircle(color, radius = 1.3.dp.toPx(), center = Offset(w * 0.5f, h * (0.18f + i * 0.32f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun TocDialog(
    chapters: List<ReaderChapter>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(vertical = 14.dp)) {
                Text(
                    strings.readerTocTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(chapters, key = { it.index }) { chapter ->
                        Text(
                            chapter.title,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableNoRipple { onSelect(chapter.index) }
                                .padding(horizontal = 22.dp, vertical = 13.dp),
                        )
                    }
                }
            }
        }
    }
}
