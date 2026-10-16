package com.moji.offline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// 图标暂用首字占位，后续替换为设计图标资源
private enum class MainTab(val label: String) {
    Discover("发现"),
    Study("学习"),
    Library("阅读库"),
    Mine("我的"),
}

@Composable
fun App() {
    MaterialTheme {
        var selectedTab by remember { mutableStateOf(MainTab.Discover) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Text(tab.label.take(1)) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("${selectedTab.label}（待实现）", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
