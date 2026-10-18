package com.moji.offline.data.settings

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual object UserCache {
    private fun dir(): String {
        val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        return paths.firstOrNull() as? String ?: ""
    }

    actual fun sizeBytes(): Long {
        val root = dir()
        if (root.isEmpty()) return 0L
        val fm = NSFileManager.defaultManager
        val enumerator = fm.enumeratorAtPath(root) ?: return 0L
        var total = 0L
        while (true) {
            val name = enumerator.nextObject() as? String ?: break
            val attrs = fm.attributesOfItemAtPath("$root/$name", null) ?: continue
            val size = attrs[NSFileSize] as? NSNumber
            total += size?.longLongValue ?: 0L
        }
        return total
    }

    actual fun clear() {
        val root = dir()
        if (root.isEmpty()) return
        val fm = NSFileManager.defaultManager
        val names = fm.contentsOfDirectoryAtPath(root, null) ?: return
        for (name in names) {
            fm.removeItemAtPath("$root/$name", null)
        }
    }
}
