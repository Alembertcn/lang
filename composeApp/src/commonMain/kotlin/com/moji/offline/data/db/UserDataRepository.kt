package com.moji.offline.data.db

import com.moji.offline.db.Article_progress
import com.moji.offline.data.study.StudyPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin repository over [MojiDb] for the user-related state the offline build
 * keeps locally: joined plans and per-article playback progress.
 */
object UserDataRepository {

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

    suspend fun progress(articleId: String): Article_progress? = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.selectProgress(articleId).executeAsOneOrNull() }.getOrNull()
    }

    suspend fun allProgress(): List<Article_progress> = withContext(Dispatchers.Default) {
        runCatching { MojiDb.userData.allProgress().executeAsList() }.getOrDefault(emptyList())
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
