package com.moji.offline.data.audio

import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechBoundary

actual class SpeechEngine actual constructor() {
    private val synth = AVSpeechSynthesizer()

    actual fun speak(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance(string = value)
        AVSpeechSynthesisVoice.voiceWithLanguage("ja-JP")?.let { utterance.voice = it }
        synth.speakUtterance(utterance)
    }

    actual fun stop() {
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}
