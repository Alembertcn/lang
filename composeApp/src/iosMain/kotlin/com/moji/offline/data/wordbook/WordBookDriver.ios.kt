package com.moji.offline.data.wordbook

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.moji.offline.wordbook.WordBookDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.Foundation.URLByAppendingPathComponent

@OptIn(ExperimentalForeignApi::class)
actual fun openWordBookDriver(): SqlDriver {
    val manager = NSFileManager.defaultManager
    val docs = manager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val dest = docs?.URLByAppendingPathComponent("wordbook.db")?.path
    if (dest != null && !manager.fileExistsAtPath(dest)) {
        val bundled = NSBundleResourcePath()
        if (bundled != null && manager.fileExistsAtPath(bundled)) {
            manager.copyItemAtPath(bundled, toPath = dest, error = null)
        }
    }
    return NativeSqliteDriver(WordBookDatabase.Schema, "wordbook.db")
}

private fun NSBundleResourcePath(): String? {
    val base = platform.Foundation.NSBundle.mainBundle.resourcePath ?: return null
    return "$base/compose-resources/composeResources/com.moji.offline.resources/files/wordbook.db"
}
