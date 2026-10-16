package com.moji.offline.data.catalog

import com.moji.offline.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One sentence parsed from MOJi's SRT article body (text + translation + timeline). */
@Serializable
data class RealSentence(
    val order: Int = 0,
    val text: String = "",
    val translation: String = "",
    val startMs: Long = 0,
    val endMs: Long = 0,
)

/** Real article data converted from reference/moji-api (see tools/convert_moji_articles.py). */
@Serializable
data class RealArticle(
    val objectId: String = "",
    val title: String = "",
    val excerpt: String = "",
    val levelTags: List<String> = emptyList(),
    val charsNum: Int = 0,
    val audioId: String = "",
    val audioDurationMs: Long = 0,
    val sentences: List<RealSentence> = emptyList(),
)

/** Loads bundled real-article JSON from composeResources/files/articles. */
object RealArticleStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, RealArticle>()

    suspend fun article(objectId: String): RealArticle? {
        cache[objectId]?.let { return it }
        val loaded = runCatching {
            json.decodeFromString<RealArticle>(
                Res.readBytes("files/articles/$objectId.json").decodeToString(),
            )
        }.getOrNull() ?: return null
        cache[objectId] = loaded
        return loaded
    }
}
