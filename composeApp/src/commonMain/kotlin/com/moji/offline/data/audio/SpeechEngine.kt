package com.moji.offline.data.audio

/** On-device Japanese TTS for the word menu's 朗读 (no network). */
expect class SpeechEngine() {
    /** Returns false when the device has no Japanese voice to speak with. */
    fun speak(text: String): Boolean
    fun stop()
}

/** Opens the platform TTS voice-data installer (no-op on iOS). */
expect fun openTtsVoiceInstaller(): Boolean
