package com.moji.offline.data.audio

import androidx.compose.runtime.Composable

actual fun createAudioRecorder(): AudioRecorder = object : AudioRecorder {
    override suspend fun start(): String? = null
    override suspend fun stop(): String? = null
}

@Composable
actual fun rememberMicPermission(onGranted: () -> Unit): () -> Unit = {
    // AVAudioRecorder wiring is part of the iOS pass; keep the button inert
    // instead of silently recording nothing.
}
