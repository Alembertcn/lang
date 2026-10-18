package com.moji.offline.ui.webview

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

actual suspend fun fetchImageBytes(url: String): ByteArray? = runCatching {
    val client = HttpClient()
    try {
        client.get(url).body<ByteArray>()
    } finally {
        client.close()
    }
}.getOrNull()

// iOS 不在本轮验收范围内（真机对照都在 Android 上做），相册保存与系统提示先留空；
// 需要时换成 UIImageWriteToSavedPhotosAlbum + 自绘提示。
actual suspend fun saveImageToGallery(bytes: ByteArray, displayName: String): Boolean = false

actual fun showPlatformToast(message: String) {
    println("toast: ${'$'}message")
}
