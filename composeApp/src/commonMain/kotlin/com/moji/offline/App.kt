package com.moji.offline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.domain.model.AppLanguage
import com.moji.offline.domain.model.ThemeMode
import com.moji.offline.feature.discover.DiscoverRoute
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_home_find_def
import com.moji.offline.resources.ic_home_find_sel
import com.moji.offline.resources.ic_home_mine_def
import com.moji.offline.resources.ic_home_mine_sel
import com.moji.offline.resources.ic_home_reader_def
import com.moji.offline.resources.ic_home_reader_sel
import com.moji.offline.resources.ic_home_sub_def
import com.moji.offline.resources.ic_home_sub_sel
import com.moji.offline.ui.i18n.LocalAppStrings
import com.moji.offline.ui.i18n.stringsFor
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.MojiPink
import com.moji.offline.ui.theme.MojiTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Bottom tabs mirror the original app: 发现 / 阅读器 / 学习 / 我的 with the exact
 * ic_home_* def/sel icon pairs extracted from the APK (28dp icon + 10sp label).
 */
private enum class MainTab(
    val label: String,
    val defIcon: DrawableResource,
    val selIcon: DrawableResource,
) {
    Discover("发现", Res.drawable.ic_home_find_def, Res.drawable.ic_home_find_sel),
    Reader("阅读器", Res.drawable.ic_home_reader_def, Res.drawable.ic_home_reader_sel),
    Study("学习", Res.drawable.ic_home_sub_def, Res.drawable.ic_home_sub_sel),
    Mine("我的", Res.drawable.ic_home_mine_def, Res.drawable.ic_home_mine_sel),
}

@Composable
fun App() {
    val strings = stringsFor(AppLanguage.CHINESE)
    MojiTheme(themeMode = ThemeMode.SYSTEM) {
        CompositionLocalProvider(LocalAppStrings provides strings) {
            var selectedTab by remember { mutableStateOf(MainTab.Discover) }
            Scaffold(
                bottomBar = {
                    MojiBottomBar(selectedTab) { selectedTab = it }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (selectedTab) {
                        MainTab.Discover -> DiscoverRoute(
                            onOpenPlan = {},
                            onOpenReading = { _, _ -> },
                            onBrowsePlans = {},
                        )
                        else -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${selectedTab.label}（待实现）",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MojiBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickableNoRipple { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painterResource(if (active) tab.selIcon else tab.defIcon),
                    contentDescription = tab.label,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label,
                    fontSize = 10.sp,
                    color = if (active) MojiPink else colors.onSurface.copy(alpha = 0.55f),
                )
            }
        }
    }
}
