package com.moji.offline.data.mine

import com.moji.offline.config.Secrets
import com.moji.offline.data.article.ArticleCatalog
import com.moji.offline.data.db.UserDataRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** 我的页统计：打卡天数 / 连续天数 / 累积阅读秒数（全部本地聚合）。 */
data class MineStats(
    val clockInDays: Int,
    val continuousDays: Int,
    val totalSeconds: Long,
)

/** 一条本地阅读历史（reading_history 行 + 展示用封面来源）。 */
data class MineHistoryItem(
    val articleId: String,
    val title: String,
    val cover: String,
    val lastReadAt: Long,
)

/**
 * 我的页数据（对位原版 MineFragment 的 readTime / clockIn / 历史 ViewModel，
 * 离线版全部来自本地 SQLite，见 AGENTS.md 零之三）。
 */
object MineStore {

    /** 本地单用户资料的 KV key（原版为账号资料，离线版存 user_setting）。 */
    const val KEY_NICK = "mine_nick"
    const val KEY_BIO = "mine_bio"

    suspend fun stats(): MineStats {
        val days = UserDataRepository.checkInDays()
        return MineStats(
            clockInDays = days.size,
            continuousDays = continuousDays(days),
            totalSeconds = UserDataRepository.totalReadingSeconds(),
        )
    }

    suspend fun history(limit: Int = 50): List<MineHistoryItem> =
        UserDataRepository.recentHistory().take(limit).map {
            MineHistoryItem(it.article_id, it.title, it.cover_url.orEmpty(), it.last_read_at)
        }

    suspend fun nick(): String = UserDataRepository.setting(KEY_NICK).orEmpty()

    suspend fun bio(): String = UserDataRepository.setting(KEY_BIO).orEmpty()

    suspend fun saveProfile(nick: String, bio: String) {
        UserDataRepository.putSetting(KEY_NICK, nick)
        UserDataRepository.putSetting(KEY_BIO, bio)
    }

    /**
     * 文章封面来源：打包 find 封面优先，其次本机 mock OSS 镜像；
     * 两者都没有就返回空串（UI 显示占位色），绝不回落原版 CDN。
     */
    suspend fun coverSource(articleId: String): String {
        val meta = ArticleCatalog.meta(articleId) ?: return ""
        val mock = Secrets.MOCK_MEDIA_BASE.trimEnd('/')
        return when {
            meta.coverFile.isNotBlank() -> "files/find/articles/${meta.coverFile}"
            meta.coverId.isNotBlank() && mock.isNotBlank() ->
                "$mock/article/images/${meta.coverId}"
            else -> ""
        }
    }

    /** 连续打卡：从今天（今天没打卡则从昨天）往回数的连续日数。 */
    private fun continuousDays(days: List<String>): Int {
        if (days.isEmpty()) return 0
        val set = days.toSet()
        var cursor = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (key(cursor) !in set) cursor = cursor.minus(1, DateTimeUnit.DAY)
        var count = 0
        while (key(cursor) in set) {
            count++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return count
    }

    private fun key(date: LocalDate): String =
        "${date.year.toString().padStart(4, '0')}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
}
