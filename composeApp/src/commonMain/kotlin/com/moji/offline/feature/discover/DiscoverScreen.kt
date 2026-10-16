package com.moji.offline.feature.discover

/**
 * Discover tab, rebuilt to mirror MOJi's find tab: search bar on top, level filter
 * row, then the article feed. The card follows MOJi's item_tab_article spec
 * (120x68 cover / 15sp two-line title / cream level chip / icon stat rows) and uses
 * the icon assets extracted from the original APK.
 */

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.catalog.FeedArticle
import com.moji.offline.data.catalog.loadDiscoverFeed
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_find_level
import com.moji.offline.resources.ic_find_listen
import com.moji.offline.resources.ic_find_search
import com.moji.offline.ui.i18n.LocalAppStrings
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.MojiPink
import org.jetbrains.compose.resources.painterResource

private val LabelBg = Color(0xFFFFECDF)
private val LabelFg = Color(0xFFD89C48)
private val LevelFilters = listOf("N5", "N4", "N3", "N2", "N1")

@Composable
fun DiscoverRoute(
    onOpenPlan: (String) -> Unit,
    onOpenReading: (planId: String, topicId: String) -> Unit,
    onBrowsePlans: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = MaterialTheme.colorScheme

    var levelFilter by remember { mutableStateOf<String?>(null) }
    var feed by remember { mutableStateOf<List<FeedArticle>>(emptyList()) }
    LaunchedEffect(strings) { feed = loadDiscoverFeed(strings) }
    val shown = remember(feed, levelFilter) {
        if (levelFilter == null) {
            feed
        } else {
            feed.filter { it.level.equals(levelFilter, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "title") {
            Text(
                strings.discoverTitle,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item(key = "search") { SearchBar(strings.discoverSearchHint) }
        item(key = "filters") {
            Spacer(Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                item(key = "all") {
                    FilterChip(
                        label = strings.plansFilterAll,
                        selected = levelFilter == null,
                    ) { levelFilter = null }
                }
                items(LevelFilters, key = { it }) { level ->
                    FilterChip(label = level, selected = levelFilter == level) { levelFilter = level }
                }
            }
        }
        item(key = "feed-header") {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(text = strings.discoverFeedTitle)
                Text(
                    strings.planBrowse,
                    modifier = Modifier.clickableNoRipple(onClick = onBrowsePlans),
                    color = MojiPink,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        items(shown, key = { it.planId + ":" + it.topicId }) { article ->
            ArticleCard(article) { onOpenReading(article.planId, article.topicId) }
        }
    }
}

@Composable
private fun SearchBar(hint: String) {
    Surface(
        shape = RoundedCornerShape(19.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(38.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Image(
                painterResource(Res.drawable.ic_find_search),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                hint,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MojiPink else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.clickableNoRipple(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SectionTitle(modifier: Modifier = Modifier, text: String) {
    Text(
        text,
        modifier = modifier,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun ArticleCard(article: FeedArticle, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(article.cover)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                article.level,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                article.title,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                article.lengthLabel,
                fontSize = 10.sp,
                color = LabelFg,
                modifier = Modifier
                    .background(LabelBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (article.durationMs > 0) {
                    Image(
                        painterResource(Res.drawable.ic_find_listen),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        formatDuration(article.durationMs),
                        fontSize = 12.sp,
                        color = colors.onBackground.copy(alpha = 0.45f),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Image(
                    painterResource(Res.drawable.ic_find_level),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    article.level,
                    fontSize = 12.sp,
                    color = colors.onBackground.copy(alpha = 0.45f),
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
