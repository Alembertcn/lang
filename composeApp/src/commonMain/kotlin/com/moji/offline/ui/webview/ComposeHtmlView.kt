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
    html: String,
    /**
     * Base URL for the loaded document. The original reader passes the loopback
     * book-server URL (`http://127.0.0.1:{port}/books/{id}/{chapterDir}`) so the
     * epub's own CSS/images/engine scripts resolve unchanged; null = no base.
     */
    baseUrl: String? = null,
    controller: WebViewController,
    onMessage: (method: String, payload: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
)
