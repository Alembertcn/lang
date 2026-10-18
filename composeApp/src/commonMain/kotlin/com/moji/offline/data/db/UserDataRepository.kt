package com.moji.offline.data.db

import com.moji.offline.data.study.StudyPlan
import com.moji.offline.db.Article_favorite
import com.moji.offline.db.Article_progress
import com.moji.offline.db.Reading_history
import com.moji.offline.db.Reading_note
import com.moji.offline.db.Word_favorite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Thin repository over [MojiDb] for the user-related state the offline build
 * keeps locally: joined plans, playback progress, settings, favorites, history
 * and reading notes.
 */
object UserDataRepository {

    private const val SRC_ARTICLE = 210L
    private const val TARGET_WORD = 102L
    private const val TARGET_SENTENCE = 120L
    private const val NOTE_PAGE_SIZE = 20L

    suspend fun joinedPlanIds(): Set<String> = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.selectJoinedPlans().executeAsList().map { it.id }.toSet() }
            .getOrDefault(emptySet())
    }

    suspend fun isPlanJoined(planId: String): Boolean = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.isPlanJoined(planId).executeAsOne() > 0L }.getOrDefault(false)
    }

    suspend fun joinPlan(planId: String, title: String, dailyMinutes: Int, nowMs: Long) {
        withContext(Dispatchers.Default) {
            runCatching {
                MojiDb.userData.joinPlan(planId, title, dailyMinutes.toLong(), nowMs)
            }
        }
    }

    suspend fun leavePlan(planId: String) {
        withContext(Dispatchers.Default) {
            runCatching { MojiDb.userData.leavePlan(planId) }
        }
    }

    /** Word-card star: the original keeps favorites per dictionary headword. */
    suspend fun isWordFavorite(wordId: String): Boolean = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.isFavorite(wordId).executeAsOne() > 0L }.getOrDefault(false)
    }

    suspend fun addWordFavorite(
        wordId: String,
        spell: String,
        pron: String,
        excerpt: String,
        nowMs: Long,
    ) {
        withContext(Dispatchers.Default) {
            runCatching {
                MojiDb.userData.upsertFavorite(wordId, spell, pron, excerpt, nowMs)
            }
        }
    }

    suspend fun removeWordFavorite(wordId: String) {
        withContext(Dispatchers.Default) {
            runCatching { MojiDb.userData.deleteFavorite(wordId) }
        }
    }

    suspend fun progress(articleId: String): Article_progress? = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.selectProgress(articleId).executeAsOneOrNull() }.getOrNull()
    }

    suspend fun allProgress(): List<Article_progress> = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.allProgress().executeAsList() }.getOrDefault(emptyList())
    }

    /** 我的页「最近阅读」：本地 reading_history，按最近阅读倒序。 */
    suspend fun recentHistory(): List<Reading_history> = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.selectRecentHistory().executeAsList() }
            .getOrDefault(emptyList())
    }

    suspend fun removeHistory(articleId: String) {
        withContext(Dispatchers.Default) {
            runCatching { MojiDb.userData.deleteHistory(articleId) }
        }
    }

    /** 打卡日（yyyy-MM-dd）倒序，供「累积阅读打卡 N 天」与连续天数。 */
    suspend fun checkInDays(): List<String> = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.selectCheckIns().executeAsList().map { it.day } }
            .getOrDefault(emptyList())
    }

    suspend fun totalReadingSeconds(): Long = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.totalDuration().executeAsOne() }.getOrDefault(0L)
    }

    /**
     * 记一次阅读：历史 + 阅读事件 + 当日打卡。原版打卡/统计走账号接口，
     * 离线版全部落本地 SQLite（实施计划「数据层」）。
     */
    suspend fun recordReading(
        articleId: String,
        title: String,
        cover: String,
        deltaSec: Long,
        nowMs: Long,
    ) {
        withContext(Dispatchers.Default) {
            runCatching {
                MojiDb.userData.upsertHistory(articleId, title, cover, nowMs, 0.0)
                if (deltaSec > 0) MojiDb.userData.insertEvent(articleId, null, deltaSec, nowMs)
                MojiDb.userData.upsertCheckIn(dayKey(nowMs), 0)
            }
        }
    }

    /** 本地时区的 yyyy-MM-dd；打卡按天去重。 */
    private fun dayKey(nowMs: Long): String {
        val local = Instant.fromEpochMilliseconds(nowMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.year.toString().padStart(4, '0')}-${local.monthNumber.toString().padStart(2, '0')}-${local.dayOfMonth.toString().padStart(2, '0')}"
    }

    suspend fun saveProgress(
        articleId: String,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
        nowMs: Long,
    ) {
        withContext(Dispatchers.Default) {
            runCatching {
                MojiDb.userData.upsertProgress(
                    article_id = articleId,
                    position_ms = positionMs,
                    duration_ms = durationMs,
                    completed = if (completed) 1L else 0L,
                    updated_at = nowMs,
                )
            }
        }
    }

    suspend fun setting(key: String): String? = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.selectSetting(key).executeAsOneOrNull() }.getOrNull()
    }

    suspend fun putSetting(key: String, value: String) {
        withContext(Dispatchers.Default) {
            runCatching { MojiDb.userData.upsertSetting(key, value) }
        }
    }

    /** 短文收藏（对位原版 article_fav，供二次进入回显收藏态）。 */
    suspend fun isArticleFavorite(articleId: String): Boolean = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.isArticleFavorite(articleId).executeAsOne() > 0L }
            .getOrDefault(false)
    }

    suspend fun addArticleFavorite(articleId: String, title: String, nowMs: Long) {
        withContext(Dispatchers.Default) {
            runCatching { MojiDb.userData.addArticleFavorite(articleId, title, nowMs) }
        }
    }

    suspend fun removeArticleFavorite(articleId: String) {
        withContext(Dispatchers.Default) {
            runCatching { MojiDb.userData.removeArticleFavorite(articleId) }
        }
    }

    /** 收藏夹：短文收藏列表（收藏时间倒序）。 */
    suspend fun articleFavorites(): List<Article_favorite> = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.selectArticleFavorites().executeAsList() }
            .getOrDefault(emptyList())
    }

    /** 收藏夹：单词收藏列表（收藏时间倒序）。 */
    suspend fun wordFavorites(): List<Word_favorite> = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.selectFavorites().executeAsList() }
            .getOrDefault(emptyList())
    }

    /**
     * 查词成功后记一条单词笔记。对位 `addWordReadingNote` → `readNote-add`
     * （srcType=210 文章，targetType=102，targetId=词条 id）。
     * 同一篇同一词只插一次。
     */
    suspend fun addWordReadingNote(
        articleId: String,
        wordId: String,
        title: String,
        excerpt: String,
    ) {
        if (articleId.isBlank() || wordId.isBlank()) return
        withContext(Dispatchers.Default) {
            runCatching {
                MojiDb.userData.insertReadingNote(
                    "w:$articleId:$wordId",
                    SRC_ARTICLE,
                    articleId,
                    TARGET_WORD,
                    wordId,
                    title.ifBlank { wordId },
                    excerpt,
                    "",
                    "",
                    0L,
                    kotlin.time.Clock.System.now().toEpochMilliseconds(),
                )
            }
        }
    }

    /**
     * 长按收藏例句。对位 `fav-addManySens`：title、trans、srcId、srcType=210、excerpt。
     * 同一篇同一句只保留一条。
     */
    suspend fun addSentenceReadingNote(articleId: String, title: String, trans: String = "") {
        val text = title.trim().replace(Regex("\\s+"), " ")
        if (articleId.isBlank() || text.isEmpty()) return
        val targetId = stableNoteId(text)
        withContext(Dispatchers.Default) {
            runCatching {
                MojiDb.userData.insertReadingNote(
                    "s:$articleId:$targetId",
                    SRC_ARTICLE,
                    articleId,
                    TARGET_SENTENCE,
                    targetId,
                    text,
                    text,
                    trans.trim(),
                    "",
                    0L,
                    kotlin.time.Clock.System.now().toEpochMilliseconds(),
                )
            }
        }
    }

    /**
     * 单词笔记。对位 `readNote-list`：按 srcType + srcId + targetType，
     * page 从 1 开始，每页 20，直到不足一页。
     */
    suspend fun wordReadingNotes(articleId: String): List<Reading_note> = withContext(Dispatchers.Default) {
        runCatching {
            val all = mutableListOf<Reading_note>()
            var page = 1
            while (true) {
                val batch = MojiDb.userData.selectReadingNotes(
                    SRC_ARTICLE,
                    articleId,
                    TARGET_WORD,
                    NOTE_PAGE_SIZE,
                    (page - 1) * NOTE_PAGE_SIZE,
                ).executeAsList()
                all += batch
                if (batch.size < NOTE_PAGE_SIZE) break
                page++
            }
            all
        }.getOrDefault(emptyList())
    }

    /** 例句笔记。对位 `fav-listSens`：只按文章 id，一次取完。 */
    suspend fun sentenceReadingNotes(articleId: String): List<Reading_note> = withContext(Dispatchers.Default) {
        runCatching {
            MojiDb.userData.selectSentenceNotes(articleId, TARGET_SENTENCE).executeAsList()
        }.getOrDefault(emptyList())
    }

    /** 对位 `readNote-del` / `fav-deleteManySens`：按本地 objectId 删除。 */
    suspend fun deleteReadingNote(objectId: String) {
        if (objectId.isBlank()) return
        withContext(Dispatchers.Default) {
            runCatching { MojiDb.userData.deleteReadingNote(objectId) }
        }
    }

    private fun stableNoteId(text: String): String {
        var hash = 0x811c9dc5.toInt()
        text.forEach { ch ->
            hash = hash xor ch.code
            hash *= 0x01000193
        }
        return hash.toUInt().toString(16)
    }

    /** First launch: seed join state + finished articles from the captured account. */
    suspend fun seedFromServer(serverPlans: List<StudyPlan>, nowMs: Long) {
        if (serverPlans.isEmpty()) return
        if (joinedPlanIds().isNotEmpty()) return
        for (plan in serverPlans) {
            joinPlan(plan.id, plan.title, dailyMinutes = 10, nowMs = nowMs)
            plan.chapters
                .flatMap { it.articles }
                .filter { it.done }
                .forEach { article ->
                    saveProgress(article.id, 0L, 0L, completed = true, nowMs = nowMs)
                }
        }
    }
}
