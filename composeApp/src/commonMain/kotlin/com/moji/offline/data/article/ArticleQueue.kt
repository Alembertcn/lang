package com.moji.offline.data.article

import com.moji.offline.data.catalog.ReaderCatalog
import com.moji.offline.data.study.StudyCatalog

/**
 * Builds the playback queue for `dialog_audio_player_list`.
 *
 * Prefer an explicit id list from the opener (学习计划章节). When that is
 * missing or only the current track, expand from the study plan that owns
 * the article, then from its column (`PLAY_LIST_TAG_COLUMN`), never from CDN.
 */
object ArticleQueue {

    suspend fun resolve(
        startId: String,
        preferredIds: List<String> = emptyList(),
    ): List<ArticleMeta> {
        val preferred = loadPlayable(preferredIds)
        if (preferred.size > 1 && preferred.any { it.id == startId }) {
            return preferred
        }
        val start = ArticleCatalog.meta(startId)?.takeIf { it.isPlayable() }
            ?: preferred.firstOrNull()
            ?: return preferred

        studyPlanIds(startId)?.let { ids ->
            val items = loadPlayable(ids)
            if (items.size > 1) return items
        }

        if (start.columnId.isNotBlank()) {
            val items = loadPlayable(ReaderCatalog.columnTopicIds(start.columnId))
            if (items.size > 1 && items.any { it.id == startId }) return items
        }

        return listOf(start)
    }

    private suspend fun studyPlanIds(articleId: String): List<String>? {
        val data = StudyCatalog.data()
        val plans = data.plans + data.catalogue
        for (plan in plans) {
            val ids = plan.chapters.flatMap { chapter -> chapter.articles.map { it.id } }
            if (articleId in ids) return ids
        }
        return null
    }

    private suspend fun loadPlayable(ids: List<String>): List<ArticleMeta> {
        val seen = mutableSetOf<String>()
        val out = ArrayList<ArticleMeta>(ids.size)
        for (id in ids) {
            if (!seen.add(id)) continue
            val meta = ArticleCatalog.meta(id) ?: continue
            if (meta.isPlayable()) out.add(meta)
        }
        return out
    }

    private fun ArticleMeta.isPlayable(): Boolean =
        audioId.isNotBlank() && audioId != "null"
}
