package com.moji.offline.data.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moji.offline.data.db.UserDataRepository
import kotlin.math.abs

/**
 * 原版 `com.mojidict.read.entities.ArticleFontsSizeMode`：
 * DEFAULT 1.0f / MIDDLE 1.2f / LARGE 1.3f / EXTRA_LARGE 1.5f，
 * SharedPreferences key article_fonts_size（Float，默认 1.0）。
 */
enum class ArticleFontSize(val size: Float, val label: String) {
    DEFAULT(1.0f, "默认"),
    MIDDLE(1.2f, "中"),
    LARGE(1.3f, "大"),
    EXTRA_LARGE(1.5f, "特大"),
    ;

    companion object {
        /** 与原版 getFontsSizeMode(float) 一致：匹配不到就回落默认档。 */
        fun ofSize(size: Float): ArticleFontSize =
            entries.firstOrNull { abs(it.size - size) < 0.001f } ?: DEFAULT
    }
}

/**
 * 原版 TranslationDisplayedMode：持久化的是字符串 value
 * （show / click / hidden，默认 CLICK_SHOW），而 H5 侧
 * MOJiReadingArticleDetailVCSwitchTranslation('0'|'1'|'2') 收的是 index
 * （原版 String.valueOf(i)，见 ArticleDetailFragment.clickTranslate$lambda$34）。
 */
enum class TranslationDisplayedMode(val value: String, val index: Int, val label: String) {
    ALWAYS_SHOW("show", 0, "显示译文"),
    CLICK_SHOW("click", 1, "点击显示译文"),
    HIDE("hidden", 2, "隐藏译文"),
    ;

    companion object {
        fun ofValue(value: String?): TranslationDisplayedMode =
            entries.firstOrNull { it.value == value } ?: CLICK_SHOW
    }
}

/**
 * 短文详情「更多」面板里的三项显示设置，落在本地 SQLite 的 user_setting 表
 * （对位原版 SharedPreferences，key 沿用原名；原版会拼接账号 id，本地版只有
 * 一个用户，直接用裸 key）。
 *
 * 默认值取自 settings/e.java：article_fonts_size = 1.0f、
 * translation_displayed_mode = CLICK_SHOW、key_is_read_explanation_open = true。
 */
object ArticleDisplaySettings {

    const val KEY_FONTS_SIZE = "article_fonts_size"
    const val KEY_TRANSLATION_MODE = "translation_displayed_mode"
    const val KEY_EXPLANATION_OPEN = "key_is_read_explanation_open"

    /** 字号倍率（1.0 / 1.2 / 1.3 / 1.5），直接喂给 H5 的 --font-size。 */
    var fontsSize: Float by mutableFloatStateOf(1.0f)
        private set

    var translationMode: TranslationDisplayedMode
        by mutableStateOf(TranslationDisplayedMode.CLICK_SHOW)
        private set

    /** 精讲模式开关：true 时 H5 显示 .moji-sentence-analysis。 */
    var explanationOpen: Boolean by mutableStateOf(true)
        private set

    /** 是否已经从库里读过一次（避免首帧用默认值闪一下）。 */
    var loaded: Boolean by mutableStateOf(false)
        private set

    val fontSizeLevel: ArticleFontSize get() = ArticleFontSize.ofSize(fontsSize)

    suspend fun load() {
        runCatching {
            UserDataRepository.setting(KEY_FONTS_SIZE)?.toFloatOrNull()?.let { fontsSize = it }
            translationMode = TranslationDisplayedMode.ofValue(
                UserDataRepository.setting(KEY_TRANSLATION_MODE),
            )
            UserDataRepository.setting(KEY_EXPLANATION_OPEN)?.let {
                explanationOpen = it.toBooleanStrictOrNull() ?: true
            }
        }
        loaded = true
    }

    /** 原版 setWebFontSize(f) → MOJiArticleDetailSetFontSize('1.2')。 */
    suspend fun setFontsSize(size: Float) {
        fontsSize = size
        UserDataRepository.putSetting(KEY_FONTS_SIZE, size.toString())
    }

    suspend fun setTranslationMode(mode: TranslationDisplayedMode) {
        translationMode = mode
        UserDataRepository.putSetting(KEY_TRANSLATION_MODE, mode.value)
    }

    suspend fun setExplanationOpen(open: Boolean) {
        explanationOpen = open
        UserDataRepository.putSetting(KEY_EXPLANATION_OPEN, open.toString())
    }
}
