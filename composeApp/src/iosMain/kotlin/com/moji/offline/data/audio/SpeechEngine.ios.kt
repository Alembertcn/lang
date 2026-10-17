package com.moji.offline.data.audio

import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechBoundary

actual class SpeechEngine actual constructor() {
    private val synth = AVSpeechSynthesizer()

    actual fun speak(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance(string = value)
        val voice = AVSpeechSynthesisVoice.voiceWithLanguage("ja-JP") ?: return false
        utterance.voice = voice
        synth.speakUtterance(utterance)
        return true
    }

    actual fun stop() {
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}

actual fun openTtsVoiceInstaller(): Boolean = false
