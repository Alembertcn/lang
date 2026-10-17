package com.moji.offline.data.audio

import android.speech.tts.TextToSpeech
import com.moji.offline.AppContext
import java.util.Locale

actual class SpeechEngine actual constructor() {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    init {
        tts = TextToSpeech(AppContext.context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.JAPANESE
                ready = true
                pending?.let { speak(it) }
                pending = null
            }
        }
    }

    actual fun speak(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val engine = tts ?: return
        if (!ready) {
            pending = value
            return
        }
        engine.stop()
        engine.speak(value, TextToSpeech.QUEUE_FLUSH, null, "moji-word")
    }

    actual fun stop() {
        runCatching { tts?.stop() }
    }
}
