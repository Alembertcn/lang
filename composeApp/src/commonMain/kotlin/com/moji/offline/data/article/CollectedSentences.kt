package com.moji.offline.data.article

import androidx.compose.runtime.mutableStateListOf

/**
 * Session-scoped 收藏 store for the article action menu. The notebook module
 * (currently out of scope) will persist this; the in-memory list keeps the
 * interaction honest until then.
 */
object CollectedSentences {
    val items = mutableStateListOf<String>()

    fun add(text: String) {
        val value = text.trim()
        if (value.isNotEmpty() && value !in items) items.add(0, value)
    }
}
