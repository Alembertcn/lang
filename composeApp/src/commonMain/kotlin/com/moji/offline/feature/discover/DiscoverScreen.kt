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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.ColorFilter
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
import com.moji.offline.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Palette taken from the original APK resources (not screenshots):
 *
 *  - bar:     same token as the 我的学习 bar (colorScheme.surface), per user
 *             request 2026-10-17 (was find_title_bar_color #F9E3E9 / dark #161618)
 *  - page:    color/find_default_magic_color (pink wash on white) / find_default_magic_color_dark #0E0E11
 *  - card:    color/Moji_bg_color_container (white) / (dark, code-swapped) #2C2C2F
 *  - tabs:    layout/fragment_new_find.xml TabLayout: height 38dp, paddingBottom 6dp,
 *             paddingEnd 16dp, marginStart 16dp, tabMinWidth 58dp, tabMaxWidth 200dp,
 *             tabTextColor #FFACACAC, tabSelectedTextColor @color/color_3a3a3a (night:
 *             swapped in code by FindFragment.onThemeChange()/loadTheme()).
 *  - indicator: drawable/tab_indicator_bg_find_category — 28dp x 3dp pill, radius 17dp,
 *             2dp above the bottom, color @color/Basic_Primary_Color #FFE0315B.
 *  - text style: style/TabLayoutNoteTextStyle — 16sp.
 */
private data class ColumnsPalette(
    val barBg: Color,
    val stripBg: Color,
    val pageBg: Color,
    val cardBg: Color,
    val selectedTab: Color,
    val unselectedTab: Color,
    val underline: Color,
    val title: Color,
    val meta: Color,
    val pillBg: Color,
    val pillText: Color,
)

@Composable
private fun columnsPalette(): ColumnsPalette = if (LocalDarkTheme.current) {
    ColumnsPalette(
        barBg = MaterialTheme.colorScheme.surface,
        stripBg = Color(0xFF0E0E11),
        pageBg = Color(0xFF0E0E11),
        cardBg = Color(0xFF2C2C2F),
        selectedTab = Color(0xFFFAFAFA),
        unselectedTab = Color(0xFFACACAC),
        underline = Color(0xFFE0315B),
        title = Color(0xFFFAFAFA),
        meta = Color(0xFFACACAC),
        pillBg = Color(0xFFFCECE0),
        pillText = Color(0xFFCF9E57),
    )
} else {
    ColumnsPalette(
        barBg = MaterialTheme.colorScheme.surface,
        stripBg = Color(0xFFFFFFFF),
        pageBg = Color(0xFFF8F8F8),
        cardBg = Color(0xFFFFFFFF),
        selectedTab = Color(0xFF3A3A3A),
        unselectedTab = Color(0xFFACACAC),
        underline = Color(0xFFE0315B),
        title = Color(0xFF3A3A3A),
        meta = Color(0xFFACACAC),
        pillBg = Color(0xFFFFF1DC),
        pillText = Color(0xFFCE8A2E),
    )
}

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
    val palette = columnsPalette()

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.pageBg),
    ) {
        DiscoverTopBar(strings.discoverTitle, palette)
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
                palette = palette,
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
                    palette = palette,
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
    palette: ColumnsPalette,
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
                palette = palette,
                modifier = Modifier.padding(horizontal = 16.dp),
                onOpenArticle = onOpenArticle,
            )
        }
    }
}

/* --------------------------------------------------------------- top chrome */

@Composable
private fun DiscoverTopBar(title: String, palette: ColumnsPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.barBg)
            .statusBarsPadding()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.selectedTab)
        Spacer(Modifier.weight(1f))
        Image(
            painterResource(Res.drawable.find_icon_search),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.selectedTab),
            modifier = Modifier
                .padding(end = 16.dp)
                .size(20.dp),
        )
    }
}

@Composable
private fun CategoryTabStrip(
    tabs: List<String>,
    selected: Int,
    palette: ColumnsPalette,
    onSelect: (Int) -> Unit,
) {
    // Matches layout/fragment_new_find.xml: height 38dp, paddingBottom 6dp,
    // marginStart 16dp + TabLayout's own 12dp tab padding, tabMinWidth 58dp.
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(palette.stripBg)
            .padding(bottom = 6.dp),
        contentPadding = PaddingValues(start = 28.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(count = tabs.size) { index ->
            val isSelected = index == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(min = 58.dp)
                    .clickableNoRipple { onSelect(index) }
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    tabs[index],
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) palette.selectedTab else palette.unselectedTab,
                )
                Spacer(Modifier.height(2.dp))
                // drawable/tab_indicator_bg_find_category: 28dp x 3dp pill.
                Box(
                    Modifier
                        .width(if (isSelected) 28.dp else 0.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isSelected) palette.underline else Color.Transparent),
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
    palette: ColumnsPalette,
    modifier: Modifier = Modifier,
    onOpenArticle: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.cardBg,
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
                    color = palette.title,
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
                ColumnArticleRow(article, strings, palette, onOpenArticle)
            }
        }
    }
}

@Composable
private fun ColumnArticleRow(
    article: FindColumnArticle,
    strings: AppStrings,
    palette: ColumnsPalette,
    onOpen: (String) -> Unit,
) {
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
                color = palette.title,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (article.vip) {
                    Surface(shape = RoundedCornerShape(4.dp), color = palette.pillBg) {
                        Text(
                            strings.findVipTrans,
                            fontSize = 10.sp,
                            color = palette.pillText,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(newsDateLabel(article.timeMs, strings), fontSize = 13.sp, color = palette.meta)
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
