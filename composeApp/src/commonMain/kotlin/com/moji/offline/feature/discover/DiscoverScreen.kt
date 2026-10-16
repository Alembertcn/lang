package com.moji.offline.feature.discover

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.catalog.DiscoverPlanCard
import com.moji.offline.data.catalog.FindColumn
import com.moji.offline.data.catalog.FindColumnArticle
import com.moji.offline.data.catalog.FindFeed
import com.moji.offline.data.catalog.FindNews
import com.moji.offline.data.catalog.FindShortArticle
import com.moji.offline.data.catalog.FindFeedStore
import com.moji.offline.data.catalog.discoverPlanCards
import com.moji.offline.data.catalog.planCoverPath
import com.moji.offline.resources.Res
import com.moji.offline.resources.find_arrow_right
import com.moji.offline.resources.find_bg_top
import com.moji.offline.resources.find_bg_top_pink
import com.moji.offline.resources.find_clock_enable
import com.moji.offline.resources.find_edit
import com.moji.offline.resources.find_icon_all
import com.moji.offline.resources.find_icon_length
import com.moji.offline.resources.find_icon_level
import com.moji.offline.resources.find_icon_listen
import com.moji.offline.resources.find_icon_play
import com.moji.offline.resources.find_icon_preparation
import com.moji.offline.resources.find_icon_refresh
import com.moji.offline.resources.find_icon_search
import com.moji.offline.ui.i18n.AppStrings
import com.moji.offline.ui.i18n.LocalAppStrings
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Colors sampled from the original light-theme Discover tab (see REVERSE.md).
private val FindPink = Color(0xFFE23A54)
private val CircleLabelPink = Color(0xFFD6336C)
private val StickyPink = Color(0xFFF5E4E9)
private val MetaGray = Color(0xFF8A8F98)
private val SearchHintGray = Color(0xFF9AA0A6)

/**
 * Discover tab mirroring the original `FindFragment`:
 * pink header (reading time / check-in / JP level, search + categories, community
 * circles) → 短文推荐 card → 阅读计划 rail → NOW news → 专栏推荐 columns.
 */
@Composable
fun DiscoverRoute(
    onOpenPlan: (String) -> Unit,
    onOpenReading: (planId: String, topicId: String) -> Unit,
    onBrowsePlans: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current

    var feed by remember { mutableStateOf(FindFeed()) }
    var batch by remember { mutableStateOf(0) }
    var columnTab by remember { mutableStateOf(0) }
    val plans = remember { discoverPlanCards() }
    LaunchedEffect(Unit) { feed = FindFeedStore.feed() }

    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 360 }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "header") { DiscoverHeader(feed, strings, dark) }
            item(key = "short") {
                ShortReadsSection(
                    articles = feed.shortArticles,
                    batch = batch,
                    strings = strings,
                    onRefresh = { batch++ },
                    onOpen = { id -> onOpenReading("", id) },
                )
            }
            item(key = "plans") {
                PlansSection(plans, strings) { plan -> onOpenPlan(plan.id) }
            }
            item(key = "now") { NowSection(feed.news, strings, onMore = onBrowsePlans) }
            item(key = "columns") {
                ColumnsSection(
                    feed = feed,
                    tab = columnTab,
                    strings = strings,
                    onTab = { columnTab = it },
                    onOpenArticle = { id -> onOpenReading("", id) },
                )
            }
        }

        if (collapsed) {
            StickyTitleBar(strings.discoverTitle)
        }
    }
}

/* ------------------------------------------------------------------ header */

@Composable
private fun DiscoverHeader(feed: FindFeed, strings: AppStrings, dark: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0xFFF8E3E7)),
        )
        Image(
            painterResource(Res.drawable.find_bg_top_pink),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painterResource(Res.drawable.find_bg_top),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
        )
        // Light theme fades into the page; dark theme keeps a crisp edge (a fade
        // to the dark background would muddy the cloud artwork).
        if (!dark) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.78f to Color.Transparent,
                            1f to colors.background,
                        ),
                    ),
            )
        }
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(Res.drawable.find_clock_enable),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    strings.findReadMinutesFormat.replace("%d", feed.readMinutes.toString()),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2B2B2B),
                )
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(50), color = FindPink) {
                    Text(
                        strings.findClockIn,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    strings.findLevelFormat.replace("%s", "N5~N4"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2B2B2B),
                )
                Spacer(Modifier.width(6.dp))
                Image(
                    painterResource(Res.drawable.find_edit),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Image(
                            painterResource(Res.drawable.find_icon_search),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            strings.findSearchHint,
                            fontSize = 15.sp,
                            color = SearchHintGray,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painterResource(Res.drawable.find_icon_all),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(Color(0xFF3A3A3A)),
                    )
                    Text(
                        strings.findCategory,
                        fontSize = 12.sp,
                        color = Color(0xFF2B2B2B),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                feed.circles.forEach { circle ->
                    val path = if (dark && circle.dark.isNotEmpty()) circle.dark else circle.light
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickableNoRipple { },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RemoteFileImage(
                            path = "files/find/$path",
                            modifier = Modifier.size(52.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickyTitleBar(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StickyPink)
            .statusBarsPadding()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2B2B2B))
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

/* -------------------------------------------------------- 短文推荐 (short) */

@Composable
private fun ShortReadsSection(
    articles: List<FindShortArticle>,
    batch: Int,
    strings: AppStrings,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val window = remember(articles, batch) {
        if (articles.isEmpty()) {
            emptyList()
        } else {
            (0 until minOf(3, articles.size)).map { articles[(batch * 3 + it) % articles.size] }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(strings.findShortRecommend)
        val featured = window.firstOrNull() ?: return
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp),
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.padding(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    RemoteFileImage(
                        path = "files/find/${featured.cover}",
                        modifier = Modifier.fillMaxSize(),
                        onClick = { onOpen(featured.id) },
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painterResource(Res.drawable.find_icon_listen),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    featured.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableNoRipple { onOpen(featured.id) },
                )
                Spacer(Modifier.height(8.dp))
                ShortMetaRow(featured, strings)

                window.drop(1).forEach { item ->
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                fontSize = 16.sp,
                                lineHeight = 23.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clickableNoRipple { onOpen(item.id) },
                            )
                            Spacer(Modifier.height(10.dp))
                            ShortMetaRow(item, strings)
                        }
                        Spacer(Modifier.width(12.dp))
                        RemoteFileImage(
                            path = "files/find/${item.cover}",
                            modifier = Modifier
                                .width(96.dp)
                                .height(62.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            onClick = { onOpen(item.id) },
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickableNoRipple(onClick = onRefresh)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painterResource(Res.drawable.find_icon_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.findChangeBatch, fontSize = 13.sp, color = MetaGray)
                }
            }
        }
    }
}

@Composable
private fun ShortMetaRow(item: FindShortArticle, strings: AppStrings) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.level.isNotEmpty()) {
            MetaChip(Res.drawable.find_icon_level, item.level, FindPink)
            Spacer(Modifier.width(12.dp))
        }
        if (item.tag.isNotEmpty()) {
            MetaChip(Res.drawable.find_icon_preparation, item.tag, MetaGray)
            Spacer(Modifier.width(12.dp))
        }
        MetaChip(Res.drawable.find_icon_length, lengthLabel(item.length, strings), MetaGray)
    }
}

@Composable
private fun MetaChip(icon: org.jetbrains.compose.resources.DrawableResource, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            colorFilter = ColorFilter.tint(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = color)
    }
}

private fun lengthLabel(key: String, strings: AppStrings): String = when (key) {
    "short" -> strings.planLengthShort
    "long" -> strings.planLengthLong
    else -> strings.planLengthMedium
}

/* --------------------------------------------------------- 阅读计划 (plans) */

@Composable
private fun PlansSection(plans: List<DiscoverPlanCard>, strings: AppStrings, onOpen: (DiscoverPlanCard) -> Unit) {
    SectionTitle(strings.findReadingPlans)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(plans, key = { it.id }) { plan ->
            PlanCard(plan, strings, onClick = { onOpen(plan) })
        }
    }
}

@Composable
private fun PlanCard(plan: DiscoverPlanCard, strings: AppStrings, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier
            .width(300.dp)
            .clickableNoRipple(onClick = onClick),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp),
            ) {
                RemoteFileImage(
                    path = planCoverPath(plan.id),
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to Color(0xB3000000),
                            ),
                        ),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Text(
                        strings.findLearningFormat.replace("%d", plan.participants.toString()),
                        fontSize = 10.sp,
                        color = Color(0xFF2B2B2B),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        plan.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painterResource(Res.drawable.find_icon_level),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(plan.level, fontSize = 11.sp, color = Color.White.copy(alpha = 0.92f))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            strings.findLessonCountFormat.replace("%d", plan.artNum.toString()),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                plan.topics.take(4).forEach { (title, lengthKey) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            lengthLabel(lengthKey, strings),
                            fontSize = 11.sp,
                            color = MetaGray,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.findPlanAllFormat.replace("%d", plan.artNum.toString()), fontSize = 13.sp, color = MetaGray)
                    Image(
                        painterResource(Res.drawable.find_arrow_right),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------- NOW (news) */

@Composable
private fun NowSection(news: List<FindNews>, strings: AppStrings, onMore: () -> Unit) {
    SectionTitle(
        strings.findNowTitle,
        trailing = strings.findMore,
        onTrailing = onMore,
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            news.take(3).forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(newsDateLabel(item.dateMs, strings), fontSize = 12.sp, color = MetaGray)
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .width(96.dp)
                            .height(62.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.topic, fontSize = 11.sp, color = MetaGray)
                    }
                }
            }
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

/* --------------------------------------------------- 专栏推荐 (columns) */

@Composable
private fun ColumnsSection(
    feed: FindFeed,
    tab: Int,
    strings: AppStrings,
    onTab: (Int) -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    val tabs = listOf(strings.findColumnRecommend) + feed.categories.map { it.tab }
    val columns = if (tab == 0) feed.columns else feed.categories.getOrNull(tab - 1)?.columns.orEmpty()

    Spacer(Modifier.height(22.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(tabs.size) { index ->
            val selected = index == tab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickableNoRipple { onTab(index) },
            ) {
                Text(
                    tabs[index],
                    fontSize = if (selected) 17.sp else 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(if (selected) 24.dp else 0.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) FindPink else Color.Transparent),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    columns.forEach { column ->
        ColumnCard(column, strings, onOpenArticle)
        Spacer(Modifier.height(12.dp))
    }
    if (columns.isEmpty()) {
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ColumnCard(column: FindColumn, strings: AppStrings, onOpenArticle: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteFileImage(
                    path = "files/find/${column.cover}",
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp)),
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
                    modifier = Modifier.size(14.dp),
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
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFF1DC)) {
                        Text(
                            strings.findVipTrans,
                            fontSize = 10.sp,
                            color = Color(0xFFD08A2C),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(newsDateLabel(article.timeMs, strings), fontSize = 12.sp, color = MetaGray)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (article.cover.isNotEmpty()) {
            RemoteFileImage(
                path = "files/find/${article.cover}",
                modifier = Modifier
                    .width(96.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

/* ------------------------------------------------------------------ shared */

@Composable
private fun SectionTitle(text: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null && onTrailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickableNoRipple(onClick = onTrailing),
            ) {
                Text(trailing, fontSize = 13.sp, color = MetaGray)
                Image(
                    painterResource(Res.drawable.find_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/**
 * Image from composeResources by path (feed images are bundled by
 * tools/fetch_find_assets.py). Falls back to a soft placeholder while loading.
 */
@Composable
private fun RemoteFileImage(
    path: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = runCatching { Res.readBytes(path).decodeToImageBitmap() }.getOrNull()
    }
    val clickable = if (onClick != null) Modifier.clickableNoRipple(onClick = onClick) else Modifier
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier.then(clickable),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier
                .then(clickable)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
