package com.moji.offline.data.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moji.offline.data.db.UserDataRepository
import com.moji.offline.domain.model.ThemeMode
import kotlin.math.abs

/**
 * 我的 → 设置。字段对位 base.apk `res/xml/mojidict_preference_setting.xml`
 * 里保留的项，值落在本地 user_setting（原版 SharedPreferences）。
 *
 * 发音语速：快 1.3 / 中 1.0 / 慢 0.7，
 * key `key_sound_play_speed`。注音对位文章 H5 `FURIGANA_TYPE`
 * （HIRAGANA 0 / ROMAJI 1 / NONE 2）。
 */
object AppSettings {

    const val KEY_THEME = "theme_setting"
    const val KEY_LANGUAGE = "language_mode_setting"
    const val KEY_SPELL = "spell_mode_setting"
    const val KEY_BRIGHT = "bright_mode_setting"
    const val KEY_SPEED = "key_sound_play_speed"
    const val KEY_VOICE = "qwen_tts_voice"
    const val KEY_JP_FONT = "use_jp_font"

    var themeMode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var language: AppLanguageMode by mutableStateOf(AppLanguageMode.HANS)
        private set

    var spell: SpellMode by mutableStateOf(SpellMode.HIRAGANA)
        private set

    var bright: Boolean by mutableStateOf(false)
        private set

    var voiceSpeed: VoiceSpeed by mutableStateOf(VoiceSpeed.MEDIUM)
        private set

    /** 原版声优。存过的千问音色 id 对不上时回到七海。 */
    var wordVoice: WordVoice by mutableStateOf(WordVoice.NANAMI)
        private set

    /** 原版主题页「启用标准日文字体」，文章注入 `useJpFont`，默认开。 */
    var useJpFont: Boolean by mutableStateOf(true)
        private set

    suspend fun load() {
        runCatching {
            UserDataRepository.setting(KEY_THEME)?.let {
                themeMode = runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM)
            }
            language = AppLanguageMode.ofTag(UserDataRepository.setting(KEY_LANGUAGE))
            UserDataRepository.setting(KEY_SPELL)?.toIntOrNull()?.let {
                spell = SpellMode.ofCode(it)
            }
            UserDataRepository.setting(KEY_BRIGHT)?.let {
                bright = it.toBooleanStrictOrNull() ?: false
            }
            UserDataRepository.setting(KEY_SPEED)?.toFloatOrNull()?.let {
                voiceSpeed = VoiceSpeed.ofRate(it)
            }
            wordVoice = WordVoice.ofId(UserDataRepository.setting(KEY_VOICE)) ?: WordVoice.NANAMI
            UserDataRepository.setting(KEY_JP_FONT)?.let {
                useJpFont = it.toBooleanStrictOrNull() ?: true
            }
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        themeMode = mode
        UserDataRepository.putSetting(KEY_THEME, mode.name)
    }

    suspend fun setLanguage(mode: AppLanguageMode) {
        language = mode
        UserDataRepository.putSetting(KEY_LANGUAGE, mode.tag)
    }

    suspend fun setSpell(mode: SpellMode) {
        spell = mode
        UserDataRepository.putSetting(KEY_SPELL, mode.code.toString())
    }

    suspend fun setBright(on: Boolean) {
        bright = on
        UserDataRepository.putSetting(KEY_BRIGHT, on.toString())
    }

    suspend fun setVoiceSpeed(speed: VoiceSpeed) {
        voiceSpeed = speed
        UserDataRepository.putSetting(KEY_SPEED, speed.rate.toString())
    }

    suspend fun setWordVoice(voice: WordVoice) {
        wordVoice = voice
        UserDataRepository.putSetting(KEY_VOICE, voice.id)
    }

    suspend fun setUseJpFont(on: Boolean) {
        useJpFont = on
        UserDataRepository.putSetting(KEY_JP_FONT, on.toString())
    }
}

/** 原版 `setting_page_language_value_zh` / `_tra`，文章 H5 `LANGUAGE_ZH`。 */
enum class AppLanguageMode(val tag: String, val label: String) {
    HANS("zh-Hans", "简体中文"),
    HANT("zh-Hant", "繁體中文"),
    ;

    companion object {
        fun ofTag(tag: String?): AppLanguageMode =
            entries.firstOrNull { it.tag == tag } ?: HANS
    }
}

/** 文章 H5 `window.FURIGANA_TYPE`。 */
enum class SpellMode(val code: Int, val label: String) {
    HIRAGANA(0, "假名"),
    ROMAJI(1, "罗马音"),
    NONE(2, "不注音"),
    ;

    companion object {
        fun ofCode(code: Int): SpellMode = entries.firstOrNull { it.code == code } ?: HIRAGANA
    }
}

/** 原版 `tts-voice-actors-fetch` 的四个在用声优，默认七海。 */
enum class WordVoice(val id: String, val label: String) {
    NANAMI("f002", "七海"),
    KEITA("m001", "恵太"),
    AOI("f004", "葵"),
    NAOKI("m002", "直樹"),
    ;

    companion object {
        fun ofId(id: String?): WordVoice? =
            entries.firstOrNull { it.id == id?.trim() }
    }
}

/** 语速行顺序与原版 `activity_sound_setting` 的 Tab 一致：快 / 中 / 慢。 */
enum class VoiceSpeed(val rate: Float, val label: String) {
    FAST(1.3f, "快"),
    MEDIUM(1.0f, "中"),
    SLOW(0.7f, "慢"),
    ;

    companion object {
        fun ofRate(rate: Float): VoiceSpeed =
            entries.minByOrNull { abs(it.rate - rate) } ?: MEDIUM
    }
}
