package com.moji.offline.data.catalog

import com.moji.offline.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Discover-tab data bundled by `tools/fetch_find_assets.py` from the captured
 * MOJi find APIs (reference/moji-api/find). Images live next to feed.json:
 * `files/find/{circles,articles,plans,columns}/...`.
 */
@Serializable
data class FindCircle(
    val id: String = "",
    val title: String = "",
    val light: String = "",
    val dark: String = "",
)

@Serializable
data class FindShortArticle(
    val id: String = "",
    val title: String = "",
    val cover: String = "",
    val level: String = "",
    val tag: String = "",
    /** short | medium | long */
    val length: String = "",
)

@Serializable
data class FindNews(
    val id: String = "",
    val title: String = "",
    val topic: String = "",
    val dateMs: Long = 0,
)

@Serializable
data class FindColumnArticle(
    val id: String = "",
    val title: String = "",
    val cover: String = "",
    val timeMs: Long = 0,
)

@Serializable
data class FindColumn(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val cover: String = "",
    /** JLPT tags from `readingCnt-category` (`N1`…`N5`, `n1up`); used by 考试 Tab chips. */
    val levelTags: List<String> = emptyList(),
    val articles: List<FindColumnArticle> = emptyList(),
)

@Serializable
data class FindCategory(
    val tab: String = "",
    val columns: List<FindColumn> = emptyList(),
)

@Serializable
data class FindFeed(
    val readMinutes: Int = 0,
    val checkedIn: Boolean = false,
    val circles: List<FindCircle> = emptyList(),
    val shortArticles: List<FindShortArticle> = emptyList(),
    val news: List<FindNews> = emptyList(),
    val columns: List<FindColumn> = emptyList(),
    val categories: List<FindCategory> = emptyList(),
)

object FindFeedStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: FindFeed? = null

    suspend fun feed(): FindFeed {
        cache?.let { return it }
        val loaded = runCatching {
            json.decodeFromString<FindFeed>(
                Res.readBytes("files/find/feed.json").decodeToString(),
            )
        }.getOrDefault(FindFeed())
        cache = loaded
        return loaded
    }
}

/** Reading-plan card model for Discover; reuses the generated plan catalog. */
data class DiscoverPlanCard(
    val id: String,
    val title: String,
    val level: String,
    val artNum: Int,
    val participants: Int,
    /** chapter rows: title to lengthKey (short/medium/long) */
    val topics: List<Pair<String, String>>,
)

/** `files/find/plans/{id}.jpg` — bundled by tools/fetch_find_assets.py. */
fun planCoverPath(planId: String): String = "files/find/plans/$planId.jpg"

fun discoverPlanCards(): List<DiscoverPlanCard> = GeneratedMojiPlans.plans.map { plan ->
    DiscoverPlanCard(
        id = plan.id,
        title = plan.title,
        level = plan.levels.reversed().joinToString("~"),
        artNum = plan.artNum,
        participants = plan.participants,
        topics = plan.sections.flatMap { section -> section.topics.map { it.title to it.lengthKey } },
    )
}
