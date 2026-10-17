package com.moji.offline.data.study

import com.moji.offline.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Study tab (学习) data, bundled by `tools/build_study_assets.py` from the
 * captured APIs:
 *   统计卡  <- reader/readStats-articleMonthlyStats.json
 *   计划    <- responses/readPlan.mine.json + readPlan.detail
 *   订阅    <- reader/column-mySubscribed.json
 */
@Serializable
data class StudyStats(
    val days: Int = 0,
    val durSec: Int = 0,
    val num: Int = 0,
) {
    val hours: Float get() = durSec / 60f / 60f
}

@Serializable
data class StudyArticle(
    val id: String = "",
    val title: String = "",
    val done: Boolean = false,
)

@Serializable
data class StudyChapter(
    val title: String = "",
    val articles: List<StudyArticle> = emptyList(),
)

@Serializable
data class StudyPlan(
    val id: String = "",
    val title: String = "",
    val desc: String = "",
    val levels: List<String> = emptyList(),
    val participants: Int = 0,
    /** True for plans already joined (catalogue badge). */
    val joined: Boolean = false,
    /** Relative to `files/find/`, e.g. `plans/68b7a2....jpg`. */
    val coverFile: String = "",
    val artNum: Int = 0,
    val done: Int = 0,
    val daysLeft: Int = 0,
    val chapters: List<StudyChapter> = emptyList(),
) {
    val progress: Float get() = if (artNum <= 0) 0f else (done.toFloat() / artNum).coerceIn(0f, 1f)
}

@Serializable
data class StudyColumn(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val articleNum: Int = 0,
    val coverId: String = "",
)

@Serializable
data class StudyData(
    val stats: StudyStats = StudyStats(),
    val plans: List<StudyPlan> = emptyList(),
    /** All plans (ReadPlanListActivity catalogue). */
    val catalogue: List<StudyPlan> = emptyList(),
    val subscribed: List<StudyColumn> = emptyList(),
)

object StudyCatalog {
    private const val PATH = "files/study/study.json"
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: StudyData? = null

    suspend fun data(): StudyData {
        cache?.let { return it }
        val loaded = runCatching {
            json.decodeFromString<StudyData>(Res.readBytes(PATH).decodeToString())
        }.getOrDefault(StudyData())
        cache = loaded
        return loaded
    }
}
