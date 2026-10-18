package com.moji.offline.data.audio

import com.moji.offline.config.Secrets
import com.moji.offline.data.settings.AppSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 词卡发音对位原版 `tts-fetch`：按词条 id + 声优 id 播放 mp3。
 * 文件在本机 mock `$MOCK_MEDIA_BASE/tts/{voiceId}/{wordId}.mp3`。
 * 还没有文件时，mock 在这次请求里用千问合成这一条并落盘。
 * 设置页试听用声优示例 `$MOCK_MEDIA_BASE/tts/sample/{voiceId}.mp3`。
 */
object WordAudio {

    private val player = createAudioPlayer()
    private val speakLock = Mutex()

    suspend fun speak(wordId: String): Boolean {
        val id = wordId.trim()
        if (id.isEmpty()) return false
        return play("tts/${AppSettings.wordVoice.id}/$id.mp3")
    }

    /** 例句发音。原版 `MojiWordDetailWebView.I` 用 tarType 103 + 例句 id。 */
    suspend fun speakExample(exampleId: String): Boolean {
        val id = exampleId.trim()
        if (id.isEmpty()) return false
        return play("tts/103/${AppSettings.wordVoice.id}/$id.mp3")
    }

    suspend fun playSample(): Boolean = play("tts/sample/${AppSettings.wordVoice.id}.mp3")

    fun stop() {
        runCatching { player.pause() }
    }

    private suspend fun play(path: String): Boolean = speakLock.withLock {
        val base = Secrets.MOCK_MEDIA_BASE.trimEnd('/')
        if (base.isEmpty()) return false
        val duration = runCatching { player.load("$base/$path") }.getOrNull() ?: return false
        if (duration <= 0) return false
        player.setSpeed(AppSettings.voiceSpeed.rate)
        player.play()
        true
    }
}
