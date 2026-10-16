package com.moji.offline.data.catalog

import androidx.compose.ui.graphics.Color
import com.moji.offline.ui.i18n.AppStrings

/** One article row of the Discover feed: the real MOJi article plus its plan context. */
data class FeedArticle(
    val planId: String,
    val topicId: String,
    val title: String,
    val excerpt: String,
    val level: String,
    val lengthLabel: String,
    val durationMs: Long,
    val cover: List<Color>,
)

/**
 * Flattens the generated plan catalog into the Discover article feed and joins the
 * bundled real-article index for audio duration. Mirrors MOJi's find tab list.
 */
suspend fun loadDiscoverFeed(strings: AppStrings): List<FeedArticle> {
    val index = RealArticleIndex.entries()
    return GeneratedMojiPlans.plans.flatMap { plan ->
        plan.sections.flatMap { section ->
            section.topics.map { topic ->
                FeedArticle(
                    planId = plan.id,
                    topicId = topic.id,
                    title = topic.title,
                    excerpt = topic.excerpt,
                    level = plan.primaryLevel,
                    lengthLabel = lengthLabel(topic.lengthKey, strings),
                    durationMs = index[topic.id]?.audioDurationMs ?: 0L,
                    cover = listOf(Color(plan.cover0), Color(plan.cover1)),
                )
            }
        }
    }
}

private fun lengthLabel(key: String, strings: AppStrings): String = when (key) {
    "short" -> strings.planLengthShort
    "long" -> strings.planLengthLong
    else -> strings.planLengthMedium
}
