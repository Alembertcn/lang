package com.moji.offline.data.settings

/** 设置里的「清除缓存」：只动应用缓存目录，不动本地库。 */
expect object UserCache {
    fun sizeBytes(): Long
    fun clear()
}

fun formatCacheSize(bytes: Long): String {
    val n = bytes.coerceAtLeast(0L)
    return when {
        n < 1024L -> "${n}B"
        n < 1024L * 1024L -> "${n / 1024L}KB"
        else -> {
            val mb = n / (1024.0 * 1024.0)
            val whole = mb.toInt()
            val frac = ((mb - whole) * 10).toInt()
            "$whole.$frac MB"
        }
    }
}
