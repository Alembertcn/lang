package com.moji.offline.data.catalog

import com.moji.offline.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One row of composeResources/files/articles/index.json (article meta without sentences). */
@Serializable
data class RealArticleIndexEntry(
    val objectId: String = "",
    val title: String = "",
    val audioId: String = "",
    val audioDurationMs: Long = 0,
    val sentenceCount: Int = 0,
)

@Serializable
private data class RealArticleIndexFile(val articles: List<RealArticleIndexEntry> = emptyList())

/** Loads the bundled article index once and serves it by objectId. */
object RealArticleIndex {
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: Map<String, RealArticleIndexEntry>? = null

    suspend fun entries(): Map<String, RealArticleIndexEntry> {
        cache?.let { return it }
        val loaded = runCatching {
            json.decodeFromString<RealArticleIndexFile>(
                Res.readBytes("files/articles/index.json").decodeToString(),
            ).articles.associateBy { it.objectId }
        }.getOrDefault(emptyMap())
        cache = loaded
        return loaded
    }
}
