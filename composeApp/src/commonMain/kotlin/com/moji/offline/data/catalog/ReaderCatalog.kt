package com.moji.offline.data.catalog

import com.moji.offline.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PLAN_ID_READER = "reader"

@Serializable
data class ReaderArticleRef(
    val id: String = "",
    val title: String = "",
    val excerpt: String = "",
    val hasLocal: Boolean = false,
    val planId: String? = null,
)

@Serializable
data class ReaderHistoryItem(
    val id: String = "",
    val title: String = "",
    val columnId: String? = null,
    val updatedAt: String? = null,
    val hasLocal: Boolean = false,
    val planId: String? = null,
)

@Serializable
data class ReaderColumn(
    val id: String = "",
    val title: String = "",
    val brief: String = "",
    val articleNum: Int = 0,
    val subscribedNum: Int = 0,
    val type: String = "",
    val articles: List<ReaderArticleRef> = emptyList(),
)

@Serializable
data class ReaderBook(
    val id: String = "",
    val title: String = "",
    val language: String = "",
    val contentQty: Int = 0,
    /** MOJi book type: `office` = 官方, others treated as 我的. */
    val type: String = "office",
    val coverId: String = "",
)

@Serializable
data class ReaderBookLib(
    val id: String = "",
    val title: String = "",
    val authorName: String = "",
    val usedNum: Int = 0,
)

@Serializable
data class ReaderEveryDay(
    val id: String = "",
    val title: String = "",
    val hasLocal: Boolean = false,
    val planId: String? = null,
    val viewedNum: Int = 0,
)

@Serializable
data class ReaderCatalogData(
    val everyDay: ReaderEveryDay = ReaderEveryDay(),
    val histories: List<ReaderHistoryItem> = emptyList(),
    val columns: List<ReaderColumn> = emptyList(),
    val books: List<ReaderBook> = emptyList(),
    val bookLibs: List<ReaderBookLib> = emptyList(),
    val articleToPlan: Map<String, String> = emptyMap(),
)

/** Offline reader home + column data from reference/moji-api/reader. */
object ReaderCatalog {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: ReaderCatalogData? = null

    suspend fun load(): ReaderCatalogData {
        cached?.let { return it }
        val data = json.decodeFromString<ReaderCatalogData>(
            Res.readBytes("files/reader/catalog.json").decodeToString(),
        )
        cached = data
        return data
    }

    suspend fun column(columnId: String): ReaderColumn? =
        load().columns.firstOrNull { it.id == columnId }

    /** Neighbor topic ids within a column (or empty for free-standing reader articles). */
    suspend fun columnTopicIds(columnId: String): List<String> =
        column(columnId)?.articles?.map { it.id }.orEmpty()

    suspend fun resolvePlanId(articleId: String, preferred: String? = null): String {
        if (!preferred.isNullOrBlank() && preferred != PLAN_ID_READER) return preferred
        val data = load()
        data.articleToPlan[articleId]?.let { return it }
        data.columns.firstOrNull { col -> col.articles.any { it.id == articleId } }?.let { return it.id }
        return PLAN_ID_READER
    }
}
