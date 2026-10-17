package com.moji.offline.data.article

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moji.offline.config.Secrets
import com.moji.offline.data.audio.createAudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * App-scoped article audio, mirroring MOJi's singleton audio ViewModel
 * (`AudioPlayerControllerView` in `activity_main` observes it).
 *
 * One continuous file per article + the captured `audioDuration`; the same
 * session feeds the in-article bar and the global mini player above the tabs.
 */
object ArticlePlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = createAudioPlayer()
    private var loadJob: Job? = null

    var meta by mutableStateOf<ArticleMeta?>(null)
        private set

    /** Mini player appears on the first play and stays until closed. */
    var visible by mutableStateOf(false)
        private set

    var playing by mutableStateOf(false)
        private set

    var loaded by mutableStateOf(false)
        private set

    var positionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var speed by mutableStateOf(1.0f)
        private set

    /** Sentence currently highlighted in the H5 (`window.setHightLightSRT`). */
    var highlightIndex by mutableStateOf(-1)
        private set

    /** Sentences from the H5's `getSRTData` payload (SRT timeline). */
    var sentences: List<SrtSentence> by mutableStateOf(emptyList())
        private set

    enum class LoopMode { NONE, SENTENCE, ARTICLE }

    var loopMode by mutableStateOf(LoopMode.NONE)
        private set

    /** Sentence pinned by 单句循环 (original `Q4.q.f6096d`). */
    private var loopSentenceIndex: Int = -1

    init {
        player.onCompletion = {
            when (loopMode) {
                LoopMode.SENTENCE -> {
                    val entry = sentences.getOrNull(highlightIndex)
                    if (entry != null) {
                        positionMs = entry.startMs
                        player.seekTo(entry.startMs)
                        player.play()
                    } else {
                        playing = false
                    }
                }
                LoopMode.ARTICLE -> {
                    positionMs = 0L
                    player.seekTo(0L)
                    player.play()
                }
                LoopMode.NONE -> {
                    playing = false
                    positionMs = durationMs
                }
            }
        }
        scope.launch {
            while (true) {
                delay(200)
                if (!playing || !loaded) continue
                positionMs = player.positionMs()
                syncHighlight()
                loopCurrentSentenceIfNeeded()
                if (durationMs > 0 && positionMs >= durationMs) {
                    playing = false
                }
            }
        }
    }

    /** Prepares [next] unless it is already the current track. */
    fun setTrack(next: ArticleMeta) {
        if (meta?.id == next.id) return
        loadJob?.cancel()
        player.pause()
        player.release()
        loaded = false
        playing = false
        visible = false
        positionMs = 0L
        durationMs = next.audioDuration * 1000L
        meta = next

        val url = audioUrl(next)
        if (url.isBlank()) return
        loadJob = scope.launch {
            runCatching {
                val reported = player.load(url)
                if (reported > 0) durationMs = reported
                loaded = true
            }
        }
    }

    fun toggle() {
        if (!loaded) return
        if (playing) {
            player.pause()
            playing = false
        } else {
            player.play()
            playing = true
            visible = true
        }
    }

    fun seekToFraction(fraction: Float) {
        if (!loaded || durationMs <= 0) return
        positionMs = (durationMs * fraction.coerceIn(0f, 1f)).toLong()
        player.seekTo(positionMs)
        syncHighlight()
    }

    /** SRT callback from the H5 (array of {startTime,endTime} in seconds). */
    fun setTimeline(json: String) {
        sentences = runCatching {
            val array = Json.parseToJsonElement(json) as JsonArray
            array.map { element ->
                val obj = element.jsonObject
                val start = seconds(obj["startTime"] as? JsonPrimitive)
                val end = seconds(obj["endTime"] as? JsonPrimitive)
                val text = (obj["contentText"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                SrtSentence(start, end, text)
            }
        }.getOrDefault(emptyList())
        highlightIndex = -1
        loopSentenceIndex = -1
        syncHighlight()
    }

    /** Sentence tap (`ArticleJsInterface.onClickSRT`) seeks to that sentence. */
    fun seekToIndex(index: Int) {
        val entry = sentences.getOrNull(index) ?: return
        positionMs = entry.startMs
        player.seekTo(entry.startMs)
        highlightIndex = index
        if (loopMode == LoopMode.SENTENCE) loopSentenceIndex = index
    }

    /** 朗读 from the word/sentence menu: jump to the sentence holding [text]. */
    fun seekToText(text: String): Boolean {
        val needle = text.trim()
        if (needle.isEmpty()) return false
        val index = sentences.indexOfFirst { it.text.replace(" ", "").contains(needle.replace(" ", "")) }
        if (index < 0) return false
        seekToIndex(index)
        if (!playing) toggle()
        return true
    }

    fun changeLoopMode(mode: LoopMode) {
        loopMode = mode
        loopSentenceIndex = when (mode) {
            LoopMode.SENTENCE -> {
                val index = if (highlightIndex >= 0) highlightIndex
                else sentences.indexOfLast { positionMs >= it.startMs }
                if (index >= 0) highlightIndex = index
                index
            }
            else -> -1
        }
    }

    fun changeSpeed(value: Float) {
        speed = value
        player.setSpeed(value)
    }

    /** Close from the mini player: stops playback and removes the track. */
    fun close() {
        loadJob?.cancel()
        player.pause()
        player.release()
        meta = null
        visible = false
        playing = false
        loaded = false
        positionMs = 0L
        durationMs = 0L
        sentences = emptyList()
        highlightIndex = -1
        loopSentenceIndex = -1
    }

    private fun syncHighlight() {
        if (sentences.isEmpty()) return
        // 单句循环 pins the sentence (original keeps f6096d until the mode or
        // sentence changes), so the highlight must not walk to the next line.
        if (loopMode == LoopMode.SENTENCE && loopSentenceIndex >= 0) {
            highlightIndex = loopSentenceIndex
            return
        }
        val index = sentences.indexOfLast { positionMs >= it.startMs }
        if (index >= 0 && index != highlightIndex) highlightIndex = index
    }

    /**
     * Original `Q4.q.b(positionMs)`: on every progress tick, if the position
     * left the current sentence's [start, end] window, seek back to its start.
     * This is what makes 单句循环 loop at the sentence boundary instead of
     * waiting for the whole file to finish.
     */
    private fun loopCurrentSentenceIfNeeded() {
        if (loopMode != LoopMode.SENTENCE) return
        val index = if (loopSentenceIndex >= 0) loopSentenceIndex else highlightIndex
        val entry = sentences.getOrNull(index) ?: return
        if (index != highlightIndex) highlightIndex = index
        if (positionMs < entry.startMs || positionMs > entry.endMs) {
            positionMs = entry.startMs
            player.seekTo(entry.startMs)
            if (!playing) {
                player.play()
                playing = true
            }
        }
    }

    private fun seconds(value: JsonPrimitive?): Long {
        val text = value?.contentOrNull ?: return 0L
        return ((text.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }

    private fun audioUrl(meta: ArticleMeta): String {
        val base = Secrets.MOCK_MEDIA_BASE.trimEnd('/')
        if (base.isBlank() || meta.audioId.isBlank()) return ""
        return "$base/article/audio/${meta.audioId}"
    }
}

data class SrtSentence(val startMs: Long, val endMs: Long, val text: String)
