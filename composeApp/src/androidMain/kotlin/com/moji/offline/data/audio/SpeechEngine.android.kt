package com.moji.offline.data.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.moji.offline.AppContext
import java.io.File
import java.util.Locale

/**
 * Word pronunciation, mirroring MOJi's `com.mojitec.hcbase.audioplayer`:
 * Android TextToSpeech pinned to Japanese, synthesised to a file and then
 * played through the media pipeline (the original keeps a `File` on its TTS
 * request `B5.f` and hands it to the audio player).
 *
 * Some phones ship a TTS engine without Japanese voice data; in that case we
 * report "unavailable" instead of letting the engine fall back to the system
 * locale (which reads the word in Chinese).
 */
actual class SpeechEngine actual constructor() {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    private var ready = false
    private var japanese = false
    private var pending: String? = null

    private val file: File by lazy { File(AppContext.context.cacheDir, "moji_word_tts.wav") }

    init {
        tts = TextToSpeech(AppContext.context) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                Log.d(TAG, "init failed status=$status")
                return@TextToSpeech
            }
            val availability = engine.isLanguageAvailable(Locale.JAPANESE)
            japanese = availability >= TextToSpeech.LANG_AVAILABLE
            if (japanese) {
                engine.language = Locale.JAPANESE
                // Prefer an on-device Japanese voice (the original requests
                // B5.e.JAPANESE rather than the phone default locale).
                val voice = engine.voices
                    ?.filter { it.locale.language == "ja" }
                    ?.sortedBy { if (it.isNetworkConnectionRequired) 1 else 0 }
                    ?.firstOrNull()
                if (voice != null) engine.voice = voice
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onError(utteranceId: String?) {
                        Log.d(TAG, "synthesis error id=$utteranceId")
                    }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != WORD_ID) return
                        main.post { playFile() }
                    }
                })
            }
            ready = true
            Log.d(TAG, "init ok availability=$availability voice=${engine.voice?.name}")
            pending?.let { speak(it) }
            pending = null
        }
    }

    actual fun speak(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        if (!ready) {
            pending = value
            return true
        }
        if (!japanese) {
            Log.d(TAG, "japanese voice unavailable; refusing to speak with default locale")
            return false
        }
        val engine = tts ?: return false
        runCatching { player?.release() }
        player = null
        runCatching { file.delete() }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
        }
        val result = engine.synthesizeToFile(value, params, file, WORD_ID)
        Log.d(TAG, "synthesize '$value' result=$result")
        return result == TextToSpeech.SUCCESS
    }

    private fun playFile() {
        if (!file.exists() || file.length() == 0L) {
            Log.d(TAG, "synthesised file missing")
            return
        }
        runCatching {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        }.onFailure { Log.d(TAG, "play failed $it") }
    }

    actual fun stop() {
        runCatching { tts?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private companion object {
        const val TAG = "MojiSpeech"
        const val WORD_ID = "moji_word"
    }
}

actual fun openTtsVoiceInstaller(): Boolean = runCatching {
    val context = AppContext.context
    val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
}.getOrDefault(false)
