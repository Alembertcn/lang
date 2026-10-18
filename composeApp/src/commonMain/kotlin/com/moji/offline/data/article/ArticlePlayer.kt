package com.moji.offline.data.article

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moji.offline.config.Secrets
import com.moji.offline.data.audio.createAudioPlayer
import com.moji.offline.data.db.UserDataRepository
import com.moji.offline.data.mine.MineStore
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
 *
 * 会话落盘对位原版 MMKV：`play_sound_object_id` / `play_current_position` /
 * `play_total_duration` / `play_speed` / `play_mode`；杀掉进程再进仍能还原
 * 迷你播放器与进度（不自动续播，需用户点播放）。
 */
object ArticlePlayer {

    /** 原版 MMKV key（SharedPreferences / MMKV 同名）。 */
    private const val KEY_OBJECT_ID = "play_sound_object_id"
    private const val KEY_POSITION = "play_current_position"
    private const val KEY_DURATION = "play_total_duration"
    private const val KEY_SPEED = "play_speed"
    private const val KEY_MODE = "play_mode"
    private const val KEY_QUEUE = "play_queue_ids"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = createAudioPlayer()
    private var loadJob: Job? = null
    private var restoring = false
    private var lastRecordedMs = 0L
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

    /** Play queue (plan chapters / column articles), like MOJi's playlist. */
    var queue: List<ArticleMeta> by mutableStateOf(emptyList())
        private set

    var queueIndex by mutableStateOf(-1)
        private set

    private var autoPlayWhenLoaded = false
    private var lastPersistMs = 0L

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
                    // MOJi's playlist advances to the next audio work.
                    if (queueIndex in 0 until queue.lastIndex) {
                        persistProgress(completed = true)
                        playAt(queueIndex + 1)
                    } else {
                        persistProgress(completed = true)
                        playing = false
                        positionMs = durationMs
                    }
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
                persistProgressIfDue()
                if (durationMs > 0 && positionMs >= durationMs) {
                    playing = false
                }
            }
        }
    }

    /**
     * 播放列表弹层打开时：若当前只是单曲队列，按栏目/计划展开完整列表。
     */
    fun expandQueueIfNeeded() {
        val current = meta ?: return
        if (queue.size > 1) return
        scope.launch {
            val expanded = ArticleQueue.resolve(current.id, queue.map { it.id })
            if (expanded.size <= 1) return@launch
            val index = expanded.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            queue = expanded
            queueIndex = index
        }
    }

    /** Sets the queue [items] and starts on [startId]'s entry. */
    fun setQueue(items: List<ArticleMeta>, startId: String) {
        if (items.isEmpty()) return
        queue = items
        val index = items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        queueIndex = index
        val wasVisible = visible
        setTrack(items[index])
        if (wasVisible) visible = true
        persistSession()
    }

    /**
     * 冷启动还原上次迷你播放器会话（对位原版 MainActivity 读 MMKV）。
     * 只铺轨 + 定位，不自动 play。
     *
     * 优先读 `play_sound_object_id`；没有则回落到最近一条未完成的
     * `article_progress`（原版进度表），保证杀进程后仍能看到迷你条。
     */
    suspend fun restoreSession() {
        if (meta != null || restoring) return
        restoring = true
        try {
            val sessionId = UserDataRepository.setting(KEY_OBJECT_ID)?.trim().orEmpty()
            val latest = UserDataRepository.allProgress()
                .filter { it.completed == 0L && it.position_ms >= 1_000L }
                .maxByOrNull { it.updated_at }
            val id = sessionId.ifEmpty { latest?.article_id.orEmpty() }
            if (id.isEmpty()) return
            val queueIds = UserDataRepository.setting(KEY_QUEUE)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val items = buildList {
                val seen = mutableSetOf<String>()
                for (qid in queueIds.ifEmpty { listOf(id) }) {
                    if (!seen.add(qid)) continue
                    ArticleCatalog.meta(qid)?.let(::add)
                }
                if (none { it.id == id }) {
                    ArticleCatalog.meta(id)?.let(::add)
                }
            }
            val current = items.firstOrNull { it.id == id } ?: return
            queue = items
            queueIndex = items.indexOfFirst { it.id == id }.coerceAtLeast(0)
            UserDataRepository.setting(KEY_SPEED)?.toFloatOrNull()?.let {
                speed = it.coerceIn(0.5f, 2.0f)
            }
            UserDataRepository.setting(KEY_MODE)?.let { raw ->
                loopMode = runCatching { LoopMode.valueOf(raw) }.getOrDefault(LoopMode.NONE)
            }
            val savedPos = UserDataRepository.setting(KEY_POSITION)?.toLongOrNull()
                ?: latest?.takeIf { it.article_id == id }?.position_ms
                ?: 0L
            autoPlayWhenLoaded = false
            loadTrack(current, resumeMs = savedPos.coerceAtLeast(0L), showMini = true)
        } finally {
            restoring = false
        }
    }

    /** Switches to the queue entry at [index] and keeps playing. */
    fun playAt(index: Int) {
        val item = queue.getOrNull(index) ?: return
        queueIndex = index
        autoPlayWhenLoaded = true
        setTrack(item)
        visible = true
        if (loaded) {
            player.play()
            playing = true
        }
    }

    fun nextTrack() {
        if (queueIndex >= 0 && queueIndex < queue.lastIndex) playAt(queueIndex + 1)
    }

    fun previousTrack() {
        if (queueIndex > 0) playAt(queueIndex - 1)
    }

    /** Prepares [next] unless it is already the current track. */
    fun setTrack(next: ArticleMeta) {
        if (meta?.id == next.id) return
        persistProgress(completed = false)
        loadTrack(next, resumeMs = null, showMini = false)
    }

    private fun loadTrack(next: ArticleMeta, resumeMs: Long?, showMini: Boolean) {
        loadJob?.cancel()
        player.pause()
        player.release()
        loaded = false
        lastRecordedMs = 0L
        playing = false
        visible = showMini
        positionMs = 0L
        durationMs = next.audioDuration * 1000L
        meta = next
        player.setSpeed(speed)

        val url = audioUrl(next)
        if (url.isBlank()) return
        loadJob = scope.launch {
            runCatching {
                val reported = player.load(url)
                if (reported > 0) durationMs = reported
                loaded = true
                val target = when {
                    resumeMs != null && resumeMs in 1_000 until (durationMs - 1_000).coerceAtLeast(0) ->
                        resumeMs
                    else -> {
                        val saved = UserDataRepository.progress(next.id)
                        if (saved != null && saved.completed == 0L &&
                            saved.position_ms in 1_000 until (durationMs - 1_000).coerceAtLeast(0)
                        ) {
                            saved.position_ms
                        } else {
                            0L
                        }
                    }
                }
                if (target > 0L) {
                    positionMs = target
                    lastRecordedMs = target
                    player.seekTo(target)
                }
                if (autoPlayWhenLoaded) {
                    autoPlayWhenLoaded = false
                    player.play()
                    playing = true
                    visible = true
                }
                persistSession()
            }
        }
    }

    fun toggle() {
        if (!loaded) return
        if (playing) {
            player.pause()
            playing = false
            persistProgress(completed = false)
        } else {
            player.play()
            playing = true
            visible = true
            persistSession()
        }
    }

    fun seekToFraction(fraction: Float) {
        if (!loaded || durationMs <= 0) return
        positionMs = (durationMs * fraction.coerceIn(0f, 1f)).toLong()
        player.seekTo(positionMs)
        syncHighlight()
        persistSession()
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
        persistSession()
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
        persistSession()
    }

    fun changeSpeed(value: Float) {
        speed = value
        player.setSpeed(value)
        persistSession()
    }

    /** Close from the mini player: stops playback and removes the track. */
    fun close() {
        persistProgress(completed = false)
        loadJob?.cancel()
        player.pause()
        player.release()
        meta = null
        visible = false
        playing = false
        loaded = false
        lastRecordedMs = 0L
        positionMs = 0L
        durationMs = 0L
        sentences = emptyList()
        queue = emptyList()
        queueIndex = -1
        autoPlayWhenLoaded = false
        highlightIndex = -1
        loopSentenceIndex = -1
        clearSession()
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

    /** Saves the current position (throttled to ~5s while playing). */
    private fun persistProgressIfDue() {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (now - lastPersistMs < 5_000) return
        lastPersistMs = now
        persistProgress(completed = false)
    }

    private fun persistProgress(completed: Boolean) {
        val m = meta ?: return
        if (!loaded || durationMs <= 0) return
        val position = if (completed) durationMs else positionMs
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        lastPersistMs = now
        // 我的页的打卡 / 历史 / 累积时长都来自这次增量（单次最多计 6s，
        // 与 5s 的落盘节流对齐；拖进度条的前跳不计入）。
        val deltaSec = (position - lastRecordedMs).coerceIn(0, 6_000) / 1_000
        lastRecordedMs = position
        scope.launch {
            UserDataRepository.saveProgress(
                articleId = m.id,
                positionMs = position,
                durationMs = durationMs,
                completed = completed || position >= durationMs - 1_000,
                nowMs = now,
            )
            UserDataRepository.recordReading(
                articleId = m.id,
                title = m.title,
                cover = MineStore.coverSource(m.id),
                deltaSec = deltaSec,
                nowMs = now,
            )
            persistSessionLocked(m, position)
        }
    }

    private fun persistSession() {
        val m = meta ?: return
        if (!visible && !playing) return
        val position = positionMs
        val dur = durationMs
        val spd = speed
        val mode = loopMode.name
        val ids = queue.map { it.id }.ifEmpty { listOf(m.id) }.joinToString(",")
        scope.launch {
            UserDataRepository.putSetting(KEY_OBJECT_ID, m.id)
            UserDataRepository.putSetting(KEY_POSITION, position.coerceAtLeast(0L).toString())
            UserDataRepository.putSetting(KEY_DURATION, dur.coerceAtLeast(0L).toString())
            UserDataRepository.putSetting(KEY_SPEED, spd.toString())
            UserDataRepository.putSetting(KEY_MODE, mode)
            UserDataRepository.putSetting(KEY_QUEUE, ids)
        }
    }

    private suspend fun persistSessionLocked(m: ArticleMeta, position: Long) {
        UserDataRepository.putSetting(KEY_OBJECT_ID, m.id)
        UserDataRepository.putSetting(KEY_POSITION, position.coerceAtLeast(0L).toString())
        UserDataRepository.putSetting(KEY_DURATION, durationMs.coerceAtLeast(0L).toString())
        UserDataRepository.putSetting(KEY_SPEED, speed.toString())
        UserDataRepository.putSetting(KEY_MODE, loopMode.name)
        val ids = queue.map { it.id }.ifEmpty { listOf(m.id) }
        UserDataRepository.putSetting(KEY_QUEUE, ids.joinToString(","))
    }

    private fun clearSession() {
        scope.launch {
            UserDataRepository.putSetting(KEY_OBJECT_ID, "")
            UserDataRepository.putSetting(KEY_POSITION, "0")
            UserDataRepository.putSetting(KEY_DURATION, "0")
            UserDataRepository.putSetting(KEY_QUEUE, "")
        }
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
