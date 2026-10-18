package com.moji.offline.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.moji.offline.resources.Res
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * 图片加载只允许两个来源（AGENTS.md 零之三）：composeResources 打包文件
 * （`files/...`），或本机 mock（BookServer / MOCK_MEDIA_BASE，均 127.0.0.1）。
 * 其它地址一律占位色，绝不回落原版 CDN。
 */
private val mockImageClient by lazy { HttpClient() }
private val mockImageCache = mutableMapOf<String, ImageBitmap?>()

suspend fun loadMockImage(source: String): ImageBitmap? {
    if (source.isBlank()) return null
    mockImageCache[source]?.let { return it }
    val bitmap = runCatching {
        when {
            source.startsWith("files/") -> Res.readBytes(source).decodeToImageBitmap()
            source.startsWith("http://127.0.0.1") || source.startsWith("http://localhost") ->
                mockImageClient.get(source).readBytes().decodeToImageBitmap()
            else -> null
        }
    }.getOrNull()
    mockImageCache[source] = bitmap
    return bitmap
}

@Composable
fun MockImage(
    source: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = Color(0xFFE3E3E6),
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = mockImageCache[source], source) {
        value = loadMockImage(source)
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(placeholder))
    }
}
