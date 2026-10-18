package com.moji.offline.feature.article

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moji.offline.data.settings.ArticleFontSize
import com.moji.offline.data.settings.TranslationDisplayedMode
import com.moji.offline.resources.Res
import com.moji.offline.resources.default_icon_selected
import com.moji.offline.resources.ic_article_translate
import com.moji.offline.resources.ic_article_translate_dark
import com.moji.offline.resources.ic_common_close_black
import com.moji.offline.resources.ic_common_close_dark
import com.moji.offline.resources.ic_common_explanation
import com.moji.offline.resources.ic_common_explanation_dark
import com.moji.offline.resources.ic_common_fonts
import com.moji.offline.resources.ic_common_fonts_dark
import com.moji.offline.resources.ic_news_coll_default
import com.moji.offline.resources.ic_news_coll_pressed
import com.moji.offline.resources.ic_news_coll_white
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.LocalDarkTheme
import com.moji.offline.ui.theme.mojiSp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * 短文详情右上角「⋮」→ 更多面板，照搬原版
 * BaseDetailFragment.showArticleDetailSettingDialog()（base.apk 3.4.9）。
 *
 * 按功能裁剪清单只保留 4 行：精讲模式 / 收藏 / 译文显示方式 / 字号；
 * 分享、注音方式、添加书签不做。原版「字号」行右侧的 VIP 药丸
 * （ic_command_vip）也去掉：本地版 isPro=true，全部能力直接可用。
 *
 * 几何来自 res/layout/item_simple_icon_text.xml + 真机像素测量
 * （Honor 1060px @3.0）：面板 #F8F8F8 顶圆角 16、标题行 58dp + 0.5dp 分隔线、
 * 卡片左右 16dp、行高 64dp、图标 24dp 起于 16dp、文本起于 56dp、
 * 行内分隔线起于 56dp、开关 64x32 距卡右缘 18dp。
 */

// res/values/colors.xml：Moji_bg_color_secondary_component / Moji_text_color_primary /
// Moji_border_color(_dark)；行底色是 L4.w 里写死的 #ffffff / #1c1c1e。
private val PanelBg = Color(0xFFF8F8F8)
private val PanelBgDark = Color(0xFF121412)
private val CardBg = Color(0xFFFFFFFF)
private val CardBgDark = Color(0xFF1C1C1E)
private val RowText = Color(0xFF3A3A3A)
private val RowTextDark = Color(0xFFF2F2F2)
private val Hairline = Color(0xFFECECEC)
private val HairlineDark = Color(0xFF3B3B3B)
private val SheetPink = Color(0xFFE0315B)
private val SwitchOffTrack = Color(0xFFACACAC)

/** 一行在卡片里的位置：决定圆角与是否画下分隔线（同原版 isTop/isBottom）。 */
private enum class RowPos { Single, Top, Middle, Bottom }

/**
 * 更多面板。原版四行的点击回调都先 dialog.dismiss() 再执行动作
 * （收藏 = c4.e default 分支、译文 = I3.d case 3、精讲开关 =
 * accountui.ui.fragment.c case 3、字号 = E0 case 3），所以这里统一由
 * 调用方在回调里关掉本面板，二级弹层随后弹出。
 */
@Composable
fun MoreMenuSheet(
    explanationOpen: Boolean,
    favorited: Boolean,
    translationMode: TranslationDisplayedMode,
    fontSizeLevel: ArticleFontSize,
    onDismiss: () -> Unit,
    onToggleExplanation: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onPickTranslationMode: () -> Unit,
    onPickFontSize: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    BottomPanel(title = "更多", onDismiss = onDismiss) {
        MoreRow(
            icon = if (dark) {
                Res.drawable.ic_common_explanation_dark
            } else {
                Res.drawable.ic_common_explanation
            },
            text = "精讲模式",
            pos = RowPos.Top,
            end = { MojiSwitch(checked = explanationOpen, onChange = onToggleExplanation) },
            onClick = { onToggleExplanation(!explanationOpen) },
        )
        MoreRow(
            icon = when {
                favorited -> Res.drawable.ic_news_coll_pressed
                dark -> Res.drawable.ic_news_coll_white
                else -> Res.drawable.ic_news_coll_default
            },
            text = "收藏",
            pos = RowPos.Middle,
            onClick = onToggleFavorite,
        )
        MoreRow(
            icon = if (dark) {
                Res.drawable.ic_article_translate_dark
            } else {
                Res.drawable.ic_article_translate
            },
            // 原版这一行的文本就是当前模式名（getStringByValue），不是「译文显示方式」
            text = translationMode.label,
            pos = RowPos.Middle,
            onClick = onPickTranslationMode,
        )
        MoreRow(
            icon = if (dark) Res.drawable.ic_common_fonts_dark else Res.drawable.ic_common_fonts,
            text = "字号（${fontSizeLevel.label}）",
            pos = RowPos.Bottom,
            onClick = onPickFontSize,
        )
    }
}

/** 译文显示方式：同一套面板、3 行可选（SelectableIconTextEntity + L4.t）。 */
@Composable
fun TranslationModeSheet(
    selected: TranslationDisplayedMode,
    onSelect: (TranslationDisplayedMode) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomPanel(title = "译文显示方式", onDismiss = onDismiss) {
        val modes = TranslationDisplayedMode.entries
        modes.forEachIndexed { index, mode ->
            MoreRow(
                // 选中才画勾，未选中留空位（文本位置不动），同 L4.t 的 setImageDrawable(null)
                icon = if (mode == selected) Res.drawable.default_icon_selected else null,
                text = mode.label,
                pos = when {
                    modes.size == 1 -> RowPos.Single
                    index == 0 -> RowPos.Top
                    index == modes.lastIndex -> RowPos.Bottom
                    else -> RowPos.Middle
                },
                onClick = { onSelect(mode) },
            )
        }
    }
}

/**
 * 字号弹层：dialog_fonts_size_set.xml + DialogC0788k。
 * 4 个 64x40 档位按钮 spread_inside 均分，选中态 1dp 粉描边
 * （selector_audio_player_setting_btn：radius10 + stroke Basic_Primary_Color）。
 */
@Composable
fun FontSizeSheet(
    selected: ArticleFontSize,
    onSelect: (ArticleFontSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    Box(Modifier.fillMaxSize()) {
        SheetScrim(onDismiss)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(if (dark) PanelBgDark else PanelBg)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        ) {
            Text(
                "选择字体大小",
                modifier = Modifier.padding(top = 20.dp),
                color = if (dark) RowTextDark else RowText,
                // dialog_fonts_size_set.xml：textSize 18dp
                fontSize = mojiSp(18),
                fontWeight = FontWeight.Bold,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ArticleFontSize.entries.forEach { level ->
                    FontChip(label = level.label, selected = level == selected, dark = dark) {
                        onSelect(level)
                    }
                }
            }
        }
    }
}

@Composable
private fun FontChip(label: String, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(width = 64.dp, height = 40.dp)
            .clip(shape)
            .background(if (dark) CardBgDark else CardBg)
            .then(if (selected) Modifier.border(1.dp, SheetPink, shape) else Modifier)
            .clickableNoRipple(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // dialog_fonts_size_set.xml 档位按钮：textSize 14dp
        Text(label, color = if (dark) RowTextDark else RowText, fontSize = mojiSp(14))
    }
}

/** 通用底部面板：遮罩 + 圆角卡 + 标题行（18sp bold + ✕）+ 0.5dp 分隔线。 */
@Composable
private fun BottomPanel(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dark = LocalDarkTheme.current
    Box(Modifier.fillMaxSize()) {
        SheetScrim(onDismiss)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(if (dark) PanelBgDark else PanelBg)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.padding(start = 16.dp),
                    color = if (dark) RowTextDark else RowText,
                    // item_title_view_type.xml：textSize 18dp bold
                    fontSize = mojiSp(18),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                // item_title_view_type：关闭 ImageView 40×40 + padding 8 → 可见 24dp
                Box(
                    Modifier
                        .padding(end = 16.dp)
                        .size(40.dp)
                        .clickableNoRipple(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(
                            if (dark) {
                                Res.drawable.ic_common_close_dark
                            } else {
                                Res.drawable.ic_common_close_black
                            },
                        ),
                        contentDescription = "关闭",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (dark) HairlineDark else Hairline),
            )
            content()
        }
    }
}

/** item_simple_icon_text：64dp 行，24dp 图标 + 16sp 文本 + 可选右控件 + 0.5dp 分隔线。 */
@Composable
private fun MoreRow(
    icon: DrawableResource?,
    text: String,
    pos: RowPos,
    onClick: () -> Unit,
    end: @Composable () -> Unit = {},
) {
    val dark = LocalDarkTheme.current
    val shape = when (pos) {
        RowPos.Single -> RoundedCornerShape(16.dp)
        RowPos.Top -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        RowPos.Bottom -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        RowPos.Middle -> RoundedCornerShape(0.dp)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = if (pos == RowPos.Top || pos == RowPos.Single) 16.dp else 0.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(shape)
                .background(if (dark) CardBgDark else CardBg)
                .clickableNoRipple(onClick = onClick),
        ) {
            if (icon != null) {
                Image(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(24.dp),
                )
            }
            Text(
                text,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 56.dp, end = 8.dp),
                color = if (dark) RowTextDark else RowText,
                // item_simple_icon_text.xml：textSize 16dp
                fontSize = mojiSp(16),
            )
            Box(Modifier.align(Alignment.CenterEnd)) { end() }
            if (pos == RowPos.Top || pos == RowPos.Middle) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 56.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(if (dark) HairlineDark else Hairline),
                )
            }
        }
    }
}

/**
 * 原版开关：switch_51_32_e0315b_track_selector + switch_thumb_28。
 * track 64x32（圆角 16，on #E0315B / off #ACACAC），thumb 32dp 位、可见 28dp 白圆。
 */
@Composable
private fun MojiSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .padding(end = 18.dp)
            .size(width = 64.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) SheetPink else SwitchOffTrack)
            .clickableNoRipple { onChange(!checked) },
    ) {
        Box(
            Modifier
                .size(32.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun SheetScrim(onDismiss: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = source, indication = null, onClick = onDismiss),
    )
}
