package com.moji.offline.feature.mine

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.audio.WordAudio
import com.moji.offline.data.audio.SpeechEngine
import com.moji.offline.data.mine.MineStore
import com.moji.offline.data.settings.AppSettings
import com.moji.offline.data.settings.WordVoice
import com.moji.offline.data.settings.SpellMode
import com.moji.offline.data.settings.UserCache
import com.moji.offline.data.settings.VoiceSpeed
import com.moji.offline.data.settings.formatCacheSize
import com.moji.offline.domain.model.ThemeMode
import com.moji.offline.resources.Res
import com.moji.offline.resources.ic_user_profile_more
import com.moji.offline.resources.bg_setting_mode_dark
import com.moji.offline.resources.bg_setting_mode_light
import com.moji.offline.resources.mine_about_logo
import com.moji.offline.ui.MojiBackButton
import com.moji.offline.ui.MojiToolbar
import com.moji.offline.ui.PlatformBackHandler
import com.moji.offline.ui.i18n.LocalAppStrings
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.LocalDarkTheme
import com.moji.offline.ui.theme.MojiPink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * 我的 → 设置。原版是独立 Preference 页
 * （`mojidict_preference_setting.xml`），不是短文「更多」里的字号/译文。
 *
 * 按裁剪清单和「酌情保留」只留：发音、注音模式、常亮模式、清除缓存、关于、
 * 退出登录。主题与语言切换提到「我的」首页菜单（裁剪后菜单过空）。
 * 账号与安全、阅读档案、视频缓存、个性化推荐、反馈和隐私清单依赖账号或
 * 原版网页，不做。日语声优用千问 TTS 音色。
 */
private enum class SettingsPage { Home, Pronunciation, About }

private val PageBg = Color(0xFFF1F1F1)
private val PageBgDark = Color(0xFF010101)
private val CardBg = Color(0xFFFFFFFF)
private val CardBgDark = Color(0xFF1C1C1E)
private val TitleFg = Color(0xFF3A3A3A)
private val TitleFgDark = Color(0xFFFAFAFA)
private val SecondaryFg = Color(0xFFACACAC)
private val DividerFg = Color(0xFFECECEC)
private val DividerFgDark = Color(0xFF3B3B3B)

@Composable
fun MineSettingsPage(onBack: () -> Unit) {
    var page by remember { mutableStateOf(SettingsPage.Home) }
    PlatformBackHandler {
        if (page == SettingsPage.Home) onBack() else page = SettingsPage.Home
    }
    when (page) {
        SettingsPage.Home -> SettingsHome(
            onBack = onBack,
            onOpen = { page = it },
        )
        SettingsPage.Pronunciation -> PronunciationPage(onBack = { page = SettingsPage.Home })
        SettingsPage.About -> AboutPage(onBack = { page = SettingsPage.Home })
    }
}

@Composable
private fun SettingsHome(onBack: () -> Unit, onOpen: (SettingsPage) -> Unit) {
    val strings = LocalAppStrings.current
    val (pageBg, card, titleFg) = colors()
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var cacheLabel by remember { mutableStateOf("占用空间：…") }
    LaunchedEffect(Unit) {
        AppSettings.load()
        cacheLabel = "占用空间：" + formatCacheSize(withContext(Dispatchers.Default) { UserCache.sizeBytes() })
    }

    Column(Modifier.fillMaxSize().background(pageBg)) {
        MojiToolbar(strings.settingsTitle, background = pageBg, leading = { MojiBackButton(onBack) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionLabel("功能设置")
            Column(Modifier.fillMaxWidth().background(card)) {
                NavRow("发音", onClick = { onOpen(SettingsPage.Pronunciation) })
            }
            SectionLabel("系统设置")
            Column(Modifier.fillMaxWidth().background(card)) {
                ValueRow("注音模式", AppSettings.spell.label) { picker = Picker.Spell }
                Hairline()
                SwitchRow("常亮模式", AppSettings.bright) { on ->
                    scope.launch { AppSettings.setBright(on) }
                }
                Hairline()
                ValueRow("清除缓存", cacheLabel) { confirm = Confirm.Cache }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().background(card)) {
                ValueRow("关于MOJi阅读", "1.0.0", onClick = { onOpen(SettingsPage.About) })
            }
            Spacer(Modifier.height(40.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(270.dp)
                        .height(45.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MojiPink)
                        .clickableNoRipple { confirm = Confirm.Logout },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("退出登录", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Spacer(Modifier.height(21.dp))
        }
    }

    when (picker) {
        Picker.Spell -> OptionDialog(
            title = "选择内容的注音模式",
            options = SpellMode.entries.map { it.label },
            selected = AppSettings.spell.label,
            onPick = { index ->
                scope.launch { AppSettings.setSpell(SpellMode.entries[index]) }
            },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
    when (confirm) {
        Confirm.Cache -> ConfirmDialog(
            message = "是否清理缓存？",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.Default) { UserCache.clear() }
                    cacheLabel = "占用空间：" + formatCacheSize(
                        withContext(Dispatchers.Default) { UserCache.sizeBytes() },
                    )
                }
            },
            onDismiss = { confirm = null },
        )
        Confirm.Logout -> ConfirmDialog(
            message = "确认退出登录？",
            onConfirm = {
                scope.launch { MineStore.saveProfile("", "") }
            },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

private enum class Picker { Spell }
private enum class Confirm { Cache, Logout }

@Composable
private fun PronunciationPage(onBack: () -> Unit) {
    val (pageBg, card, titleFg) = colors()
    val scope = rememberCoroutineScope()
    val speech = remember { SpeechEngine() }
    var pickingVoice by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            speech.stop()
            WordAudio.stop()
        }
    }
    Column(Modifier.fillMaxSize().background(pageBg)) {
        MojiToolbar("发音设置", background = pageBg, leading = { MojiBackButton(onBack) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionLabel("日语声优")
            Column(Modifier.fillMaxWidth().background(card)) {
                ValueRow("日语声优", AppSettings.wordVoice.label) { pickingVoice = true }
            }
            SectionLabel("词条发音")
            Column(Modifier.fillMaxWidth().background(card)) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("语速", fontSize = 16.sp, color = titleFg)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "试听",
                        fontSize = 14.sp,
                        color = MojiPink,
                        modifier = Modifier.clickableNoRipple {
                            scope.launch {
                                val sample = "いつもmojiを使ってくれて、ありがとう！"
                                if (!WordAudio.playSample()) speech.speak(sample)
                            }
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    SpeedTabs(
                        selected = AppSettings.voiceSpeed,
                        onPick = { speed -> scope.launch { AppSettings.setVoiceSpeed(speed) } },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickingVoice) {
        OptionDialog(
            title = "日语声优",
            options = WordVoice.entries.map { it.label },
            selected = AppSettings.wordVoice.label,
            onPick = { index ->
                scope.launch { AppSettings.setWordVoice(WordVoice.entries[index]) }
            },
            onDismiss = { pickingVoice = false },
        )
    }
}

@Composable
private fun SpeedTabs(selected: VoiceSpeed, onPick: (VoiceSpeed) -> Unit) {
    val dark = LocalDarkTheme.current
    val track = if (dark) Color(0xFF2C2C2E) else Color(0xFFF1F1F1)
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(track)
            .padding(2.dp),
    ) {
        VoiceSpeed.entries.forEach { speed ->
            val on = speed == selected
            Box(
                Modifier
                    .width(36.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) if (dark) Color(0xFF3A3A3C) else Color.White else Color.Transparent)
                    .clickableNoRipple { onPick(speed) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    speed.label,
                    fontSize = 13.sp,
                    color = if (on) MojiPink else SecondaryFg,
                )
            }
        }
    }
}

/** 「我的」首页 → 主题。从设置页挪出，补菜单空档；App 层全屏盖住底栏。 */
@Composable
fun MineThemePage(onBack: () -> Unit) {
    val (pageBg, card, titleFg) = colors()
    val scope = rememberCoroutineScope()
    var manual by remember { mutableStateOf(ThemeMode.LIGHT) }
    var hint by remember { mutableStateOf<String?>(null) }
    PlatformBackHandler(onBack = onBack)
    LaunchedEffect(AppSettings.themeMode) {
        if (AppSettings.themeMode != ThemeMode.SYSTEM) manual = AppSettings.themeMode
    }
    val auto = AppSettings.themeMode == ThemeMode.SYSTEM
    Column(Modifier.fillMaxSize().background(pageBg)) {
        MojiToolbar("主题", background = pageBg, leading = { MojiBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ThemeCard(
                    label = "纯白风格",
                    preview = Res.drawable.bg_setting_mode_light,
                    selected = !auto && AppSettings.themeMode == ThemeMode.LIGHT,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (auto) {
                            hint = "您已开启自动切换个性皮肤，如需设置，请先关闭。"
                        } else {
                            hint = null
                            scope.launch { AppSettings.setTheme(ThemeMode.LIGHT) }
                        }
                    },
                )
                Spacer(Modifier.width(24.dp))
                ThemeCard(
                    label = "暗黑风格",
                    preview = Res.drawable.bg_setting_mode_dark,
                    selected = !auto && AppSettings.themeMode == ThemeMode.DARK,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (auto) {
                            hint = "您已开启自动切换个性皮肤，如需设置，请先关闭。"
                        } else {
                            hint = null
                            scope.launch { AppSettings.setTheme(ThemeMode.DARK) }
                        }
                    },
                )
            }
            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                Text(hint!!, fontSize = 12.sp, color = SecondaryFg)
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(card)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自动切换", fontSize = 16.sp, color = titleFg, modifier = Modifier.weight(1f))
                        Switch(
                            checked = auto,
                            onCheckedChange = { on ->
                                hint = null
                                scope.launch {
                                    AppSettings.setTheme(if (on) ThemeMode.SYSTEM else manual)
                                }
                            },
                            colors = pinkSwitch(),
                        )
                    }
                    Text("随系统自动切换纯白/暗黑风格", fontSize = 12.sp, color = SecondaryFg)
                }
                Hairline()
                SwitchRow("启用标准日文字体", AppSettings.useJpFont) { on ->
                    scope.launch { AppSettings.setUseJpFont(on) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeCard(
    label: String,
    preview: DrawableResource,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val (_, _, titleFg) = colors()
    val painter = painterResource(preview)
    // 原版资源在 drawable-xxhdpi：浅色 54px、深色 48px。104dp 会放大到约 300px，所以发糊。
    val side = (painter.intrinsicSize.width / 3f).dp
    Column(modifier.clickableNoRipple(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter,
            contentDescription = label,
            modifier = Modifier.size(side),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = titleFg)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .size(20.dp)
                .border(1.5.dp, if (selected) MojiPink else SecondaryFg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(MojiPink))
            }
        }
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    val strings = LocalAppStrings.current
    val (pageBg, _, titleFg) = colors()
    Column(Modifier.fillMaxSize().background(pageBg)) {
        MojiToolbar("关于MOJi阅读", background = pageBg, leading = { MojiBackButton(onBack) })
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Image(
                painterResource(Res.drawable.mine_about_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("MOJi阅读", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = titleFg)
            Spacer(Modifier.height(20.dp))
            Text(
                strings.mineAboutBody,
                fontSize = 13.sp,
                color = SecondaryFg,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun colors(): Triple<Color, Color, Color> {
    val dark = LocalDarkTheme.current
    return Triple(
        if (dark) PageBgDark else PageBg,
        if (dark) CardBgDark else CardBg,
        if (dark) TitleFgDark else TitleFg,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = SecondaryFg,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    ValueRow(title, value = null, onClick = onClick)
}

@Composable
private fun ValueRow(title: String, value: String?, onClick: () -> Unit) {
    val (_, _, titleFg) = colors()
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickableNoRipple(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, color = titleFg)
        Spacer(Modifier.weight(1f))
        if (!value.isNullOrBlank()) {
            Text(value, fontSize = 14.sp, color = SecondaryFg, maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
        Image(
            painterResource(Res.drawable.ic_user_profile_more),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val (_, _, titleFg) = colors()
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, color = titleFg, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = pinkSwitch())
    }
}

@Composable
private fun pinkSwitch() = SwitchDefaults.colors(
    checkedTrackColor = MojiPink,
    checkedThumbColor = Color.White,
)

@Composable
private fun Hairline() {
    Box(
        Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp)
            .background(if (LocalDarkTheme.current) DividerFgDark else DividerFg),
    )
}

@Composable
internal fun OptionDialog(
    title: String,
    options: List<String>,
    selected: String,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val (_, card, titleFg) = colors()
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickableNoRipple(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(card)
                .clickableNoRipple { }
                .padding(vertical = 8.dp),
        ) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = titleFg,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, label ->
                    Text(
                        label,
                        fontSize = 15.sp,
                        color = if (label == selected) MojiPink else titleFg,
                        modifier = Modifier.fillMaxWidth().clickableNoRipple {
                            onPick(index)
                            onDismiss()
                        }.padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (_, card, titleFg) = colors()
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickableNoRipple(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(card)
                .clickableNoRipple { }
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(message, fontSize = 16.sp, color = titleFg, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "取消",
                    fontSize = 15.sp,
                    color = SecondaryFg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickableNoRipple(onClick = onDismiss).padding(vertical = 8.dp),
                )
                Text(
                    "确定",
                    fontSize = 15.sp,
                    color = MojiPink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickableNoRipple {
                        onConfirm()
                        onDismiss()
                    }.padding(vertical = 8.dp),
                )
            }
        }
    }
}
