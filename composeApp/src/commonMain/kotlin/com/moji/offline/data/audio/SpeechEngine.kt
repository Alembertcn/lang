package com.moji.offline.data.audio

/** On-device Japanese TTS for the word menu's 朗读 (no network). */
expect class SpeechEngine() {
    fun speak(text: String)
    fun stop()
}
