package com.moji.offline.data.settings

import com.moji.offline.AppContext
import java.io.File

actual object UserCache {
    private fun dir(): File = AppContext.context.cacheDir

    actual fun sizeBytes(): Long =
        dir().walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    actual fun clear() {
        dir().listFiles()?.forEach { it.deleteRecursively() }
    }
}
