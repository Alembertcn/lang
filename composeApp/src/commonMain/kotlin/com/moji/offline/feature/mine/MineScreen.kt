package com.moji.offline.feature.mine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.mine.MineHistoryItem
import com.moji.offline.data.mine.MineStats
import com.moji.offline.data.mine.MineStore
import com.moji.offline.data.settings.AppLanguageMode
import com.moji.offline.data.settings.AppSettings
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_article_translate
import com.moji.offline.resources.ic_article_translate_dark
import com.moji.offline.resources.ic_common_fonts
import com.moji.offline.resources.ic_common_fonts_dark
import com.moji.offline.resources.ic_common_setting_white
import com.moji.offline.resources.ic_find_listen
import com.moji.offline.resources.ic_mine_list_folder
import com.moji.offline.resources.ic_mine_list_folder_dark
import com.moji.offline.resources.img_learn_cover_1
import com.moji.offline.resources.img_learn_cover_2
import com.moji.offline.resources.img_learn_cover_3
import com.moji.offline.resources.mine_bg_default
import com.moji.offline.ui.MockImage
import com.moji.offline.ui.MojiNextArrow
import com.moji.offline.ui.i18n.LocalAppStrings
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.LocalDarkTheme
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// res/values/colors.xml：theme_background_color / Moji_bg_color_page(_dark)、
// Basic_Title_Color(_Dark)、Moji_text_color_secondary_dark、Moji_border_color(_dark)。
private val PageBg = Color(0xFFF1F1F1)
private val PageBgDark = Color(0xFF010101)
private val CardBg = Color(0xFFFFFFFF)
private val CardBgDark = Color(0xFF1C1C1E)
private val TitleFg = Color(0xFF3A3A3A)
private val TitleFgDark = Color(0xFFFAFAFA)
private val SecondaryFg = Color(0xFFACACAC)
private val DividerFg = Color(0xFFECECEC)
private val DividerFgDark = Color(0xFF3B3B3B)
private val HeaderMask = Color(0x66000000)

/** fragment_main_mine：AppBar 236dp、钉住工具条 55dp、barAlpha 在最后 78dp 渐变。 */
private val HeaderHeight = 236.dp
private val PinBarHeight = 55.dp
private val CollapseRamp = 78.dp

enum class MineDest { History, Favorites, Theme, Settings }

/**
 * 我的 tab —— 原版 `MineFragment`（base.apk 3.4.9）的离线裁剪版：
 * 236dp 折叠头图（默认书墙 + #66000000 遮罩）+ 钉住工具条（设置齿轮；
 * 扫码=OCR、消息=推送，按裁剪清单移除）+ 打卡卡 + 最近阅读 +
 * 收藏夹 / 主题 / 语言切换行。
 * 会员卡 / 推送 / 兑换码 / 更多应用 / 评分 / 帮助 / 关注我们 均按裁剪清单不做。
 * 设置 / 收藏夹 / 主题由 App 层全屏打开（盖住底栏）；本页内只叠阅读历史。
 * 语言切换仍是本页弹窗。
 */
@Composable
fun MineRoute(
    onOpenArticle: (String, List<String>) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    start: MineDest? = null,
) {
    var stats by remember { mutableStateOf(MineStats(0, 0, 0)) }
    var history by remember { mutableStateOf<List<MineHistoryItem>>(emptyList()) }
    var nick by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var dest by remember { mutableStateOf(start?.takeIf { it == MineDest.History }) }
    var showProfile by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        stats = MineStore.stats()
        history = MineStore.history()
        nick = MineStore.nick()
        bio = MineStore.bio()
    }

    Box(Modifier.fillMaxSize()) {
        when (dest) {
            MineDest.History -> MineHistoryPage(
                onBack = { dest = null },
                onOpenArticle = { onOpenArticle(it, emptyList()) },
            )
            else -> MineHome(
                stats = stats,
                history = history,
                nick = nick,
                bio = bio,
                onOpenProfile = { showProfile = true },
                onOpenHistory = { dest = MineDest.History },
                onOpenFavorites = onOpenFavorites,
                onOpenTheme = onOpenTheme,
                onOpenLanguage = { showLanguage = true },
                onOpenSettings = onOpenSettings,
                onOpenArticle = { onOpenArticle(it, emptyList()) },
            )
        }
        if (showProfile) {
            MineProfileSheet(
                nick = nick,
                bio = bio,
                onDismiss = { showProfile = false },
                onSaved = {
                    showProfile = false
                    reload++
                },
            )
        }
        if (showLanguage) {
            OptionDialog(
                title = LocalAppStrings.current.menuLanguage,
                options = AppLanguageMode.entries.map { it.label },
                selected = AppSettings.language.label,
                onPick = { index ->
                    scope.launch { AppSettings.setLanguage(AppLanguageMode.entries[index]) }
                },
                onDismiss = { showLanguage = false },
            )
        }
    }
}

@Composable
private fun MineHome(
    stats: MineStats,
    history: List<MineHistoryItem>,
    nick: String,
    bio: String,
    onOpenProfile: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val dark = LocalDarkTheme.current
    val pageBg = if (dark) PageBgDark else PageBg
    val titleFg = if (dark) TitleFgDark else TitleFg
    val density = LocalDensity.current
    val statusPx = WindowInsets.statusBars.getTop(density)
    val statusDp = with(density) { statusPx.toDp() }
    val headerPx = with(density) { HeaderHeight.toPx() }
    val pinPx = with(density) { PinBarHeight.toPx() }
    val rampPx = with(density) { CollapseRamp.toPx() }
    val scroll = rememberScrollState()
    // exitUntilCollapsed：头部上移到只剩 55dp 工具条后停住。
    val collapsed = min(scroll.value.toFloat(), headerPx - pinPx)
    val barAlpha = ((collapsed - (headerPx - pinPx - rampPx)) / rampPx).coerceIn(0f, 1f)
    // 头图展开时状态栏图标转白；离开本页恢复。
    androidx.compose.runtime.SideEffect {
        com.moji.offline.ui.MineHeaderStatus.lightIcons = barAlpha < 0.5f
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { com.moji.offline.ui.MineHeaderStatus.lightIcons = false }
    }

    Box(Modifier.fillMaxSize().background(pageBg)) {
        // ---- 列表内容 ----
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(statusDp + HeaderHeight))
            ReadTimeCard(stats, history)
            if (history.isNotEmpty()) {
                RecentReadingBlock(history, onMore = onOpenHistory, onOpenArticle = onOpenArticle)
            }
            MineRow(
                icon = Res.drawable.ic_mine_list_folder,
                iconDark = Res.drawable.ic_mine_list_folder_dark,
                title = strings.mineFavorites,
                showDivider = true,
                onClick = onOpenFavorites,
            )
            MineRow(
                icon = Res.drawable.ic_common_fonts,
                iconDark = Res.drawable.ic_common_fonts_dark,
                title = strings.settingsTheme,
                showDivider = true,
                onClick = onOpenTheme,
            )
            MineRow(
                icon = Res.drawable.ic_article_translate,
                iconDark = Res.drawable.ic_article_translate_dark,
                title = strings.menuLanguage,
                value = AppSettings.language.label,
                showDivider = false,
                onClick = onOpenLanguage,
            )
            Spacer(Modifier.height(16.dp))
        }

        // ---- 折叠头部（背景 + 遮罩 + 头像资料行） ----
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusDp + HeaderHeight)
                .offset { IntOffset(0, -collapsed.roundToInt()) },
        ) {
            Image(
                painterResource(Res.drawable.mine_bg_default),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.matchParentSize().background(HeaderMask))
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - barAlpha }
                    .clickableNoRipple(onClick = onOpenProfile),
            ) {
                Spacer(Modifier.height(statusDp))
                Spacer(Modifier.height(PinBarHeight))
                Spacer(Modifier.height(45.dp))
                Row(
                    Modifier.fillMaxWidth().padding(start = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFEFF2))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { PersonGlyph(Modifier.size(34.dp)) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            nick.ifBlank { strings.mineNickDefault },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TitleFgDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            bio.ifBlank { strings.mineBioHint },
                            fontSize = 14.sp,
                            color = TitleFgDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MojiNextArrow()
                    Spacer(Modifier.width(32.dp))
                }
                Spacer(Modifier.weight(1f))
                // temp_bottom：头部底缘 20dp 页面色，让打卡卡压在接缝上。
                Box(Modifier.fillMaxWidth().height(20.dp).background(pageBg))
            }
        }

        // ---- 钉住工具条：折叠后浮现的头像+昵称，右侧设置齿轮 ----
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusDp + PinBarHeight)
                .background(pageBg.copy(alpha = barAlpha)),
        ) {
            Box(Modifier.statusBarsPadding().height(PinBarHeight).fillMaxWidth()) {
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = barAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFEFEFF2)),
                        contentAlignment = Alignment.Center,
                    ) { PersonGlyph(Modifier.size(14.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        nick.ifBlank { strings.mineNickDefault },
                        fontSize = 14.sp,
                        color = titleFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 100.dp),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(36.dp)
                        .clickableNoRipple(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(Res.drawable.ic_common_setting_white),
                        contentDescription = strings.settingsTitle,
                        modifier = Modifier.size(36.dp).padding(6.dp),
                        colorFilter = ColorFilter.tint(
                            if (barAlpha > 0.5f) titleFg else Color.White,
                        ),
                    )
                }
            }
        }
    }
}

/** item_main_mine_read_time：80dp 白卡 + 打卡天数 + 三张旋转封面。 */
@Composable
private fun ReadTimeCard(stats: MineStats, history: List<MineHistoryItem>) {
    val strings = LocalAppStrings.current
    val dark = LocalDarkTheme.current
    val titleFg = if (dark) TitleFgDark else TitleFg
    Box(Modifier.fillMaxWidth().height(93.dp).padding(top = 5.dp)) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (dark) CardBgDark else CardBg),
        ) {
            Column(Modifier.padding(start = 20.dp, top = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${stats.clockInDays}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleFg,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        strings.mineDayUnit,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleFg,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    strings.mineClockInLabel,
                    fontSize = 12.sp,
                    color = SecondaryFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 80.dp),
                )
            }
        }
        // 原版三张旋转封面（45°/15°/45°）；有最近阅读就用真实封面。
        val specs = listOf(
            CoverSpec(48.dp, 45f, 65.dp, 20.dp, Res.drawable.img_learn_cover_1),
            CoverSpec(60.dp, 15f, 40.dp, 10.dp, Res.drawable.img_learn_cover_2),
            CoverSpec(72.dp, 45f, 15.dp, 8.dp, Res.drawable.img_learn_cover_3),
        )
        specs.forEachIndexed { index, spec ->
            val source = history.getOrNull(index)?.cover.orEmpty()
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = spec.end, top = spec.top)
                    .size(spec.size)
                    .graphicsLayer { rotationZ = spec.rotation },
            ) {
                if (source.isNotBlank()) {
                    MockImage(source, Modifier.matchParentSize().clip(RoundedCornerShape(6.dp)))
                } else {
                    Image(
                        painterResource(spec.fallback),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize().clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

private class CoverSpec(
    val size: Dp,
    val rotation: Float,
    val end: Dp,
    val top: Dp,
    val fallback: DrawableResource,
)

/** item_find_history：标题 + 「更多」药丸 + 横滑最近阅读。 */
@Composable
private fun RecentReadingBlock(
    history: List<MineHistoryItem>,
    onMore: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val dark = LocalDarkTheme.current
    val titleFg = if (dark) TitleFgDark else TitleFg
    val pillFg = if (dark) Color(0xFFE8E6E1) else Color(0xFF333333)
    Column(Modifier.fillMaxWidth().height(188.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.mineRecent, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = titleFg)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x1AACACAC))
                    .clickableNoRipple(onClick = onMore)
                    .padding(start = 16.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.mineMore, fontSize = 12.sp, color = pillFg)
                Spacer(Modifier.width(2.dp))
                MojiNextArrow(Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(history, key = { it.articleId }) { item ->
                Column(
                    Modifier.width(120.dp).clickableNoRipple { onOpenArticle(item.articleId) },
                ) {
                    Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(6.dp))) {
                        MockImage(item.cover, Modifier.matchParentSize())
                        Image(
                            painterResource(Res.drawable.ic_find_listen),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.title,
                        fontSize = 15.sp,
                        color = titleFg,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

/** item_main_mine_normal_list：24dp 图标 + 16sp 标题 + 可选右侧文案 + 右箭头 + 0.5dp 分隔线。 */
@Composable
private fun MineRow(
    icon: DrawableResource,
    iconDark: DrawableResource,
    title: String,
    showDivider: Boolean,
    onClick: () -> Unit,
    value: String? = null,
) {
    val dark = LocalDarkTheme.current
    Column(Modifier.fillMaxWidth().clickableNoRipple(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(if (dark) iconDark else icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                fontSize = 16.sp,
                color = if (dark) TitleFgDark else TitleFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (!value.isNullOrBlank()) {
                Text(
                    value,
                    fontSize = 14.sp,
                    color = SecondaryFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
            }
            MojiNextArrow()
        }
        if (showDivider) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(0.5.dp)
                    .background(if (dark) DividerFgDark else DividerFg),
            )
        }
    }
}

/** 本地单用户头像占位：中性人形（原版为账号头像照片）。 */
@Composable
private fun PersonGlyph(modifier: Modifier = Modifier, color: Color = Color(0xFFB9B9BE)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.20f, center = Offset(w / 2, h * 0.32f))
        drawCircle(color, radius = w * 0.38f, center = Offset(w / 2, h * 1.02f))
    }
}
