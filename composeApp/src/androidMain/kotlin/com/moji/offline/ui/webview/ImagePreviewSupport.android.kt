package com.moji.offline.ui.webview

import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import com.moji.offline.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun fetchImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode in 200..299) {
                connection.inputStream.use { it.readBytes() }
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** 按扩展名给 MIME，MediaStore 缺省会补 .jpg，别把 png/webp 存成 jpeg 名。 */
private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}

/**
 * MediaStore 图片入库（Android 10+ 无需存储权限）。原版走 Glide
 * `downloadOnly` + `MediaStore`，这里直接用已经在内存里的字节。
 */
actual suspend fun saveImageToGallery(bytes: ByteArray, displayName: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = AppContext.context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeOf(displayName))
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lang")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrDefault(false)
    }

actual fun showPlatformToast(message: String) {
    Toast.makeText(AppContext.context, message, Toast.LENGTH_SHORT).show()
}
