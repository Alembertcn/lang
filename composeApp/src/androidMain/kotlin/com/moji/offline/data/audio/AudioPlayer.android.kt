package com.moji.offline.data.audio

import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

/** MOJi uses Media3/ExoPlayer; a plain MediaPlayer covers the single-file bar. */
private class AndroidAudioPlayer : AudioPlayer {
    @Volatile private var player: MediaPlayer? = null

    override var onCompletion: (() -> Unit)? = null

    override suspend fun load(source: String): Long = withContext(Dispatchers.Main) {
        release()
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            player = mp
            cont.invokeOnCancellation {
                runCatching { mp.release() }
                if (player === mp) player = null
            }
            try {
                mp.setDataSource(source)
                mp.setOnPreparedListener {
                    if (cont.isActive) cont.resume(mp.duration.toLong().coerceAtLeast(0))
                }
                mp.setOnCompletionListener { onCompletion?.invoke() }
                mp.setOnErrorListener { _, what, extra ->
                    if (cont.isActive) {
                        runCatching { mp.release() }
                        if (player === mp) player = null
                        cont.resumeWithException(IllegalStateException("音频播放失败 what=$what extra=$extra"))
                    }
                    true
                }
                mp.prepareAsync()
            } catch (error: Exception) {
                runCatching { mp.release() }
                player = null
                cont.resumeWithException(error)
            }
        }
    }

    override fun play() {
        player?.let { runCatching { it.start() } }
    }

    override fun pause() {
        player?.let { mp -> runCatching { if (mp.isPlaying) mp.pause() } }
    }

    override fun seekTo(ms: Long) {
        player?.let { mp -> runCatching { mp.seekTo(ms, MediaPlayer.SEEK_CLOSEST) } }
    }

    override fun positionMs(): Long =
        player?.let { mp -> runCatching { mp.currentPosition.toLong() }.getOrNull() } ?: 0L

    override fun release() {
        val mp = player ?: return
        player = null
        runCatching { mp.release() }
    }
}
