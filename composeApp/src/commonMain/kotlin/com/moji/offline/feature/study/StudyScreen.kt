package com.moji.offline.feature.study

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.catalog.FindColumn
import com.moji.offline.data.catalog.FindFeedStore
import com.moji.offline.data.db.UserDataRepository
import com.moji.offline.data.study.StudyCatalog
import com.moji.offline.data.study.StudyColumn
import com.moji.offline.data.study.StudyData
import com.moji.offline.data.study.StudyPlan
import com.moji.offline.resources.Res
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

private val StudyPink = Color(0xFFE0315B)
private val StudyTitle = Color(0xFF030303)
private val StudyGray = Color(0xFF8B8787)
private val StudyTrack = Color(0xFFEDEDED)

/**
 * 学习 tab — port of `StudyFragment` (base.apk 3.4.9):
 * MojiToolbar「我的学习」+ 统计卡（本月学习 N 天 / X 小时 / 全部）+
 * ViewPager 两个页签：计划（MyStudyPlanFragment）/ 订阅（SubscribeHomeFragment）。
 *
 * Data comes from the captured cache via [StudyCatalog]; column recommendations
 * reuse the Discover feed (`FindFeedStore`) since this account has no
 * subscriptions.
 */
@Composable
fun StudyRoute(
    onOpenArticle: (String, List<String>) -> Unit,
    onBrowsePlans: () -> Unit,
) {
    var data by remember { mutableStateOf<StudyData?>(null) }
    var feed by remember { mutableStateOf<List<FindColumn>>(emptyList()) }
    var tab by remember { mutableStateOf(0) }
    var openPlan by remember { mutableStateOf<StudyPlan?>(null) }
    var openColumn by remember { mutableStateOf<FindColumn?>(null) }
    var joinedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var completedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        val loaded = StudyCatalog.data()
        data = loaded
        UserDataRepository.seedFromServer(
            serverPlans = loaded.plans,
            nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        )
        joinedIds = UserDataRepository.joinedPlanIds()
        completedIds = UserDataRepository.allProgress()
            .filter { it.completed == 1L }
            .map { it.article_id }
            .toSet()
        feed = runCatching { FindFeedStore.feed().columns }.getOrDefault(emptyList())
    }

    fun local(plan: StudyPlan): StudyPlan {
        val ids = plan.chapters.flatMap { c -> c.articles.map { it.id } }
        val done = ids.count { it in completedIds }
        val remaining = (plan.artNum - done).coerceAtLeast(0)
        return plan.copy(
            joined = plan.id in joinedIds,
            done = done,
            daysLeft = remaining,
        )
    }

    val dark = LocalDarkTheme.current
    val bg = if (dark) Color(0xFF0E0E11) else Color(0xFFF5F5F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val fg = if (dark) Color(0xFFF2F2F2) else StudyTitle

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(bg)) {
            TopBar(fg)
            Column(Modifier.fillMaxSize()) {
                data?.let { StatsCard(it, card, fg) }
                TabRow(tab, fg, onSelect = { tab = it })
                Box(Modifier.weight(1f)) {
                    val current = data
                    if (current != null) {
                        if (tab == 0) {
                            PlanList(
                                plans = current.catalogue.filter { it.id in joinedIds }.map(::local),
                                catalogueCount = current.catalogue.size,
                                card = card,
                                fg = fg,
                                onOpen = { openPlan = it },
                                onOpenCatalogue = onBrowsePlans,
                            )
                        } else {
                            SubscribeList(
                                subscribed = current.subscribed,
                                columns = feed,
                                card = card,
                                fg = fg,
                                onOpenColumn = { openColumn = it },
                            )
                        }
                    }
                }
            }
        }
    }

    openPlan?.let { plan ->
        val ids = plan.chapters.flatMap { c -> c.articles.map { it.id } }
        PlanSheet(
            plan = plan,
            card = card,
            fg = fg,
            onDismiss = { openPlan = null },
            onJoin = {
                scope.launch {
                    UserDataRepository.joinPlan(
                        planId = plan.id,
                        title = plan.title,
                        dailyMinutes = 10,
                        nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                    )
                    refresh++
                    openPlan = plan.copy(joined = true, daysLeft = plan.artNum)
                }
            },
        ) { id ->
            openPlan = null
            onOpenArticle(id, ids)
        }
    }
    openColumn?.let { column ->
        ColumnSheet(column = column, card = card, fg = fg, onDismiss = { openColumn = null }) { id ->
            openColumn = null
            onOpenArticle(id, column.articles.map { it.id })
        }
    }
}

/**
 * 原版 `ReadPlanListActivity`：独立页盖住底栏。从学习空态「查看推荐计划」/
 * 「全部计划 N >」进入。
 */
@Composable
fun StudyPlanCatalogueRoute(
    onBack: () -> Unit,
    onOpenArticle: (String, List<String>) -> Unit,
) {
    var data by remember { mutableStateOf<StudyData?>(null) }
    var openPlan by remember { mutableStateOf<StudyPlan?>(null) }
    var joinedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var completedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        val loaded = StudyCatalog.data()
        data = loaded
        joinedIds = UserDataRepository.joinedPlanIds()
        completedIds = UserDataRepository.allProgress()
            .filter { it.completed == 1L }
            .map { it.article_id }
            .toSet()
    }

    fun local(plan: StudyPlan): StudyPlan {
        val ids = plan.chapters.flatMap { c -> c.articles.map { it.id } }
        val done = ids.count { it in completedIds }
        val remaining = (plan.artNum - done).coerceAtLeast(0)
        return plan.copy(
            joined = plan.id in joinedIds,
            done = done,
            daysLeft = remaining,
        )
    }

    val dark = LocalDarkTheme.current
    val bg = if (dark) Color(0xFF0E0E11) else Color(0xFFF5F5F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val fg = if (dark) Color(0xFFF2F2F2) else StudyTitle

    Box(Modifier.fillMaxSize()) {
        data?.let { current ->
            PlanCatalogue(
                plans = current.catalogue.map(::local),
                bg = bg,
                card = card,
                fg = fg,
                onBack = onBack,
                onOpenPlan = { openPlan = it },
            )
        }
    }

    openPlan?.let { plan ->
        val ids = plan.chapters.flatMap { c -> c.articles.map { it.id } }
        PlanSheet(
            plan = plan,
            card = card,
            fg = fg,
            onDismiss = { openPlan = null },
            onJoin = {
                scope.launch {
                    UserDataRepository.joinPlan(
                        planId = plan.id,
                        title = plan.title,
                        dailyMinutes = 10,
                        nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                    )
                    refresh++
                    openPlan = plan.copy(joined = true, daysLeft = plan.artNum)
                }
            },
        ) { id ->
            openPlan = null
            onOpenArticle(id, ids)
        }
    }
}

@Composable
private fun TopBar(fg: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("我的学习", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** `fragment_study` header card: 本月学习 N 天 · X 小时 + 全部. */
@Composable
private fun StatsCard(data: StudyData, card: Color, fg: Color) {
    val stats = data.stats
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text("本月学习", color = fg.copy(alpha = 0.55f), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${stats.days}", color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(" 天", color = fg, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.width(14.dp))
            val tenths = kotlin.math.round(stats.hours * 10f).toInt()
            Text(
                "${tenths / 10}.${tenths % 10} 小时",
                color = fg.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 5.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "全部",
                color = fg.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(fg.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TabRow(selected: Int, fg: Color, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        listOf("计划", "订阅").forEachIndexed { index, label ->
            val active = selected == index
            Column(
                Modifier
                    .padding(horizontal = 22.dp)
                    .clickableNoRipple { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (active) fg else fg.copy(alpha = 0.45f),
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(22.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) StudyPink else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun PlanList(
    plans: List<StudyPlan>,
    catalogueCount: Int,
    card: Color,
    fg: Color,
    onOpen: (StudyPlan) -> Unit,
    onOpenCatalogue: () -> Unit,
) {
    if (plans.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("暂无加入任何阅读计划～", color = fg.copy(alpha = 0.55f), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "查看推荐计划",
                color = StudyPink,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(StudyPink.copy(alpha = 0.08f))
                    .clickableNoRipple(onClick = onOpenCatalogue)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // 原版入口在发现页「阅读计划」段的「全部 %d >」；该段已裁剪，
            // 这里把同样的入口放到计划页顶部。
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableNoRipple(onClick = onOpenCatalogue)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("全部计划", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text("$catalogueCount", color = fg.copy(alpha = 0.4f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Chevron(fg.copy(alpha = 0.35f))
            }
        }
        items(plans, key = { it.id }) { plan -> PlanCard(plan, card, fg) { onOpen(plan) } }
    }
}

/** `item_main_study_plan`: 18sp title, 还剩 N 天 / percent, 8dp bar, count. */
@Composable
private fun PlanCard(plan: StudyPlan, card: Color, fg: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .clickableNoRipple(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                plan.title,
                modifier = Modifier.weight(1f),
                color = fg,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Chevron(fg.copy(alpha = 0.35f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (plan.daysLeft > 0) "还剩 ${plan.daysLeft} 天学完" else "计划已完成",
                color = fg.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            Text("${(plan.progress * 100).toInt()}%", color = fg.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(StudyTrack)) {
            Box(
                Modifier
                    .fillMaxWidth(plan.progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(StudyPink),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已学 ${plan.done} / ${plan.artNum} 篇", color = fg.copy(alpha = 0.45f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SubscribeList(
    subscribed: List<StudyColumn>,
    columns: List<FindColumn>,
    card: Color,
    fg: Color,
    onOpenColumn: (FindColumn) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (subscribed.isEmpty()) {
            item {
                Text(
                    "暂无订阅专栏",
                    color = fg.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            item {
                Text(
                    "推荐订阅",
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        items(columns, key = { it.id }) { column ->
            ColumnCard(column, card, fg) { onOpenColumn(column) }
        }
    }
}

@Composable
private fun ColumnCard(column: FindColumn, card: Color, fg: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(card)
            .clickableNoRipple(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(path = column.cover, size = 44)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                column.title,
                color = fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (column.subtitle.isNotBlank()) {
                Text(
                    column.subtitle,
                    color = fg.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            "${column.articles.size} 篇最新",
            color = fg.copy(alpha = 0.4f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PlanSheet(
    plan: StudyPlan,
    card: Color,
    fg: Color,
    onDismiss: () -> Unit,
    onJoin: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    Overlay(card, fg, onDismiss) {
        Text(plan.title, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (plan.joined) "还剩 ${plan.daysLeft} 天学完 · 已学 ${plan.done}/${plan.artNum} 篇"
            else "共 ${plan.artNum} 篇 · 未加入",
            color = fg.copy(alpha = 0.5f),
            fontSize = 12.sp,
        )
        if (!plan.joined) {
            Spacer(Modifier.height(12.dp))
            Text(
                "加入计划",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(StudyPink)
                    .clickableNoRipple(onClick = onJoin)
                    .padding(vertical = 11.dp),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.height(360.dp)) {
            plan.chapters.forEach { chapter ->
                item {
                    Text(
                        chapter.title,
                        color = fg.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(chapter.articles, key = { it.id }) { article ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickableNoRipple { onOpenArticle(article.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            article.title.ifBlank { article.id },
                            modifier = Modifier.weight(1f),
                            color = fg,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (article.done) {
                            Text("已学", color = StudyPink, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnSheet(
    column: FindColumn,
    card: Color,
    fg: Color,
    onDismiss: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    Overlay(card, fg, onDismiss) {
        Text(column.title, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (column.subtitle.isNotBlank()) {
            Text(column.subtitle, color = fg.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (column.articles.isEmpty()) {
            Text("该专栏暂无本地缓存的文章", color = fg.copy(alpha = 0.55f), fontSize = 13.sp)
        } else {
            LazyColumn(Modifier.height(360.dp)) {
                items(column.articles, key = { it.id }) { article ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickableNoRipple { onOpenArticle(article.id) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            article.title.ifBlank { article.id },
                            color = fg,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

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

@Composable
private fun Chevron(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        drawLine(
            color,
            Offset(size.width * 0.35f, size.height * 0.22f),
            Offset(size.width * 0.65f, size.height * 0.5f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * 0.65f, size.height * 0.5f),
            Offset(size.width * 0.35f, size.height * 0.78f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * `ReadPlanListActivity` — the plan catalogue reached from the original
 * Discover「阅读计划」section ("全部 %d >") and the empty Study page.
 */
@Composable
private fun PlanCatalogue(
    plans: List<StudyPlan>,
    bg: Color,
    card: Color,
    fg: Color,
    onBack: () -> Unit,
    onOpenPlan: (StudyPlan) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.fillMaxSize().padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clickableNoRipple(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(24.dp)) {
                        drawLine(
                            fg,
                            Offset(size.width * 0.62f, size.height * 0.22f),
                            Offset(size.width * 0.38f, size.height * 0.5f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            fg,
                            Offset(size.width * 0.38f, size.height * 0.5f),
                            Offset(size.width * 0.62f, size.height * 0.78f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                Text("阅读计划", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(plans, key = { it.id }) { plan ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(card)
                        .clickableNoRipple { onOpenPlan(plan) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(path = plan.coverFile, size = 88, width = 88, height = 62)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                plan.title,
                                modifier = Modifier.weight(1f),
                                color = fg,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (plan.joined) {
                                Text(
                                    "已加入",
                                    color = StudyPink,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(StudyPink.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            plan.desc,
                            color = fg.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildString {
                                if (plan.levels.isNotEmpty()) append(plan.levels.joinToString("/"))
                                if (plan.levels.isNotEmpty()) append(" · ")
                                append("${formatCount(plan.participants)}人参与 · ${plan.artNum} 篇")
                            },
                            color = fg.copy(alpha = 0.35f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun formatCount(value: Int): String =
    if (value >= 10_000) {
        val tenths = kotlin.math.round(value / 1000f).toInt()
        "${tenths / 10}.${tenths % 10}万"
    } else {
        value.toString()
    }

@Composable
private fun Cover(path: String, size: Int, width: Int = size, height: Int = size) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = if (path.isBlank()) null else runCatching {
            Res.readBytes("files/find/$path").decodeToImageBitmap()
        }.getOrNull()
    }
    Box(
        Modifier
            .width(width.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE9ECF2)),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(width.dp).height(height.dp),
            )
        }
    }
}
