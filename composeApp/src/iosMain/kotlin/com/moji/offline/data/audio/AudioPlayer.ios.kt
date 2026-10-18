package com.moji.offline.data.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

actual fun createAudioPlayer(): AudioPlayer = IosAudioPlayer()

/** The article bar streams the same single mp3 the original ExoPlayer plays. */
@OptIn(ExperimentalForeignApi::class)
private class IosAudioPlayer : AudioPlayer {
    private var player: AVPlayer? = null
    private var speed = 1.0f

    override var onCompletion: (() -> Unit)? = null

    override suspend fun load(source: String): Long = withContext(Dispatchers.Main) {
        release()
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, error = null)
            setActive(true, error = null)
        }
        val nsUrl = NSURL(string = source)
        val item = AVPlayerItem(uRL = nsUrl)
        val p = AVPlayer(playerItem = item)
        p.rate = speed
        player = p
        // Duration is usually only known once the item is ready; the UI also
        // gets it from the captured audioDuration.
        0L
    }

    override fun play() {
        // AVPlayer.play() 等价于把 rate 设回 1，必须放在设速之前。
        player?.let {
            it.play()
            it.rate = speed
        }
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekTo(ms: Long) {
        player?.seekToTime(CMTimeMakeWithSeconds(ms / 1000.0, 600))
    }

    override fun positionMs(): Long =
        player?.currentTime()?.let { (CMTimeGetSeconds(it) * 1000).toLong() } ?: 0L

    override fun setSpeed(rate: Float) {
        speed = rate.coerceIn(0.5f, 2.0f)
        player?.rate = speed
    }

    override fun release() {
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        player = null
    }
}
