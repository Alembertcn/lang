package com.moji.offline.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** "android" / "ios" — what Bridge.js expects as its platform argument. */
expect val platformName: String

/**
 * Hosts a self-contained HTML document (reader pages, later word cards).
 *
 * [onMessage] receives Bridge.js messages using the original method names
 * (`onJsLoaded`, `setHorizontalPageCount`, `setCurrentInnerPage`,
 * `clickMOJiArticleContent`, ...). Recreate the view via `key(html)` when the
 * document changes.
 */
@Composable
expect fun ComposeHtmlView(
    html: String = "",
    /**
     * Base URL for the loaded document. The original reader passes the loopback
     * book-server URL (`http://127.0.0.1:{port}/books/{id}/{chapterDir}`) so the
     * epub's own CSS/images/engine scripts resolve unchanged; null = no base.
     */
    baseUrl: String? = null,
    /**
     * Load this URL instead of [html]. The original article page is a real
     * document (`file:///android_asset/web/mojiread/article/android/index.html`)
     * whose ES-module imports are absolute, so it must be fetched, not inlined.
     */
    url: String? = null,
    /**
     * Expose the original `ArticleJsInterface` surface (article H5 bridge)
     * in addition to the reader bridge.
     */
    articleBridge: Boolean = false,
    controller: WebViewController,
    onMessage: (method: String, payload: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
)
