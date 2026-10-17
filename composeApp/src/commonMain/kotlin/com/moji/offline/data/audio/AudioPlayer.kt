package com.moji.offline.data.audio

/** Plays one continuous article audio file (MOJi's single-file + SRT model). */
interface AudioPlayer {
    /** Loads [source] (http URL or local path) and returns its duration in ms. */
    suspend fun load(source: String): Long

    fun play()

    fun pause()

    fun seekTo(ms: Long)

    fun positionMs(): Long

    /** MOJi playback speeds 0.6–1.4. */
    fun setSpeed(rate: Float)

    var onCompletion: (() -> Unit)?

    fun release()
}

expect fun createAudioPlayer(): AudioPlayer
