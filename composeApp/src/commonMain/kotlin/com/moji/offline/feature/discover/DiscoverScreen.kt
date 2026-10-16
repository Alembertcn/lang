package com.moji.offline.feature.discover

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.catalog.FindColumn
import com.moji.offline.data.catalog.FindColumnArticle
import com.moji.offline.data.catalog.FindFeed
import com.moji.offline.data.catalog.FindFeedStore
import com.moji.offline.resources.Res
import com.moji.offline.resources.find_arrow_right
import com.moji.offline.resources.find_icon_search
import com.moji.offline.ui.i18n.AppStrings
import com.moji.offline.ui.i18n.LocalAppStrings
import com.moji.offline.ui.interaction.clickableNoRipple
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.Instant

// Colors sampled from the original Discover ▽ 专栏推荐 strip (see REVERSE.md).
private val BarPink = Color(0xFFF5E4E9)
private val TabStripPink = Color(0xFFF2E4E8)
private val TabUnderlinePink = Color(0xFFCE425D)
private val TabSelectedText = Color(0xFF3A3A3A)
private val TabUnselectedText = Color(0xFFACACAC)
private val MetaGray = Color(0xFF8A8F98)
private val VipPillBg = Color(0xFFFFF1DC)
private val VipPillText = Color(0xFFCE8A2E)

/**
 * Discover tab = the original's 专栏推荐 section only (per user request):
 * pinned 「发现」bar + pinned category strip (专栏推荐/考试/时事/生活/文学/娱乐)
 * + column cards (cover, title, 2 latest articles with VIP pill/time/cover).
 *
 * Data: reference/moji-api/find (readingCnt-privateRecmColumns + category 子目录),
 * bundled by tools/fetch_find_assets.py.
 */
@Composable
fun DiscoverRoute(
    onOpenPlan: (String) -> Unit,
    onOpenReading: (planId: String, topicId: String) -> Unit,
    onBrowsePlans: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var feed by remember { mutableStateOf(FindFeed()) }
    LaunchedEffect(Unit) { feed = FindFeedStore.feed() }

    val tabs = remember(feed, strings) {
        listOf(strings.findColumnRecommend) + feed.categories.map { it.tab }
    }
    // One page per tab, mirroring the original ViewPager.
    val pageColumns = remember(feed) {
        buildList {
            add(feed.columns)
            feed.categories.forEach { add(it.columns) }
        }
    }

    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DiscoverTopBar(strings.discoverTitle)
        if (tabs.isEmpty()) {
            Spacer(Modifier.fillMaxSize())
        } else {
            // ViewPager semantics: swipe left/right to switch categories; the tab
            // strip follows the pager and taps animate the pager (same as the
            // original Discover ViewPager + tab strip pair).
            val pagerState = rememberPagerState(pageCount = { tabs.size })
            CategoryTabStrip(
                tabs = tabs,
                selected = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
            // Per-page scroll state, kept alive like ViewPager's offscreen pages.
            val listStates = remember(tabs.size) { List(tabs.size) { LazyListState() } }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                ColumnPage(
                    columns = pageColumns.getOrElse(page) { emptyList() },
                    strings = strings,
                    listState = listStates[page.coerceIn(0, listStates.lastIndex)],
                    onOpenArticle = { id -> onOpenReading("", id) },
                )
            }
        }
    }
}

@Composable
private fun ColumnPage(
    columns: List<FindColumn>,
    strings: AppStrings,
    listState: LazyListState,
    onOpenArticle: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(count = columns.size, key = { index -> columns[index].id.ifEmpty { "col$index" } }) { index ->
            ColumnCard(
                column = columns[index],
                strings = strings,
                modifier = Modifier.padding(horizontal = 16.dp),
                onOpenArticle = onOpenArticle,
            )
        }
    }
}

/* --------------------------------------------------------------- top chrome */

@Composable
private fun DiscoverTopBar(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BarPink)
            .statusBarsPadding()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TabSelectedText)
        Spacer(Modifier.weight(1f))
        Image(
            painterResource(Res.drawable.find_icon_search),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(20.dp),
        )
    }
}

@Composable
private fun CategoryTabStrip(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(TabStripPink),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        items(count = tabs.size) { index ->
            val isSelected = index == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickableNoRipple { onSelect(index) },
            ) {
                Text(
                    tabs[index],
                    fontSize = if (isSelected) 17.sp else 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) TabSelectedText else TabUnselectedText,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(if (isSelected) 30.dp else 0.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) TabUnderlinePink else Color.Transparent),
                )
            }
        }
    }
}

/* ------------------------------------------------------------- column card */

@Composable
private fun ColumnCard(
    column: FindColumn,
    strings: AppStrings,
    modifier: Modifier = Modifier,
    onOpenArticle: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteFileImage(
                    path = "files/find/${column.cover}",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    column.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Image(
                    painterResource(Res.drawable.find_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            column.articles.forEach { article ->
                Spacer(Modifier.height(14.dp))
                ColumnArticleRow(article, strings, onOpenArticle)
            }
        }
    }
}

@Composable
private fun ColumnArticleRow(article: FindColumnArticle, strings: AppStrings, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple { onOpen(article.id) },
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                article.title,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (article.vip) {
                    Surface(shape = RoundedCornerShape(4.dp), color = VipPillBg) {
                        Text(
                            strings.findVipTrans,
                            fontSize = 10.sp,
                            color = VipPillText,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(newsDateLabel(article.timeMs, strings), fontSize = 13.sp, color = MetaGray)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (article.cover.isNotEmpty()) {
            RemoteFileImage(
                path = "files/find/${article.cover}",
                modifier = Modifier
                    .width(88.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

private fun newsDateLabel(dateMs: Long, strings: AppStrings): String {
    if (dateMs <= 0) return ""
    val zone = TimeZone.currentSystemDefault()
    val dt = Instant.fromEpochMilliseconds(dateMs).toLocalDateTime(zone)
    val now = Clock.System.now().toLocalDateTime(zone)
    val hhmm = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    val dayDiff = now.date.toEpochDays() - dt.date.toEpochDays()
    return when (dayDiff) {
        0L -> "${strings.findToday} $hhmm"
        1L -> "${strings.findYesterday} $hhmm"
        else -> "${dt.month.ordinal + 1}/${dt.day} $hhmm"
    }
}

/* ------------------------------------------------------------------ shared */

/** Image from composeResources by path; soft placeholder while loading. */
@Composable
private fun RemoteFileImage(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = runCatching { Res.readBytes(path).decodeToImageBitmap() }.getOrNull()
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
