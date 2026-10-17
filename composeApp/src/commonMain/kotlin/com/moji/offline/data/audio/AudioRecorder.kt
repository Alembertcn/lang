package com.moji.offline.data.audio

import androidx.compose.runtime.Composable

/**
 * 跟读 recording (MOJi `ArticleIntensiveActivity` uses MediaRecorder through
 * `RecordingButtonContainer`; we keep the same "record one take per sentence"
 * model).
 */
interface AudioRecorder {
    /** Starts a new take; returns the output path, or null when unavailable. */
    suspend fun start(): String?

    /** Stops and returns the recorded path (null when nothing was recorded). */
    suspend fun stop(): String?
}

expect fun createAudioRecorder(): AudioRecorder

/** Returns a `request()` lambda: asks for RECORD_AUDIO, then calls [onGranted]. */
@Composable
expect fun rememberMicPermission(onGranted: () -> Unit): () -> Unit
