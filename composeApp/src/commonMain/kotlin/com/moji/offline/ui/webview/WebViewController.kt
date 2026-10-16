package com.moji.offline.ui.webview

/** Native handle for the hosted page: run JS and pixel-scroll the content. */
class WebViewController {
    internal var evaluate: ((String) -> Unit)? = null
    internal var scrollToX: ((Float) -> Unit)? = null
    internal var evaluateWithResult: ((String, (String) -> Unit) -> Unit)? = null
    internal var readScrollX: (() -> Int)? = null

    fun evaluateJs(script: String) {
        evaluate?.invoke(script)
    }

    /** Pixel-level horizontal scroll, driven by the pager (mirrors the original). */
    fun scrollWebViewTo(x: Float) {
        scrollToX?.invoke(x)
    }

    /** Run a JS expression and receive its result as a string (on the UI thread). */
    fun evaluateJsWithResult(script: String, onResult: (String) -> Unit) {
        evaluateWithResult?.invoke(script, onResult)
    }

    /** Current horizontal scroll of the WebView in physical pixels. */
    fun currentScrollX(): Int = readScrollX?.invoke() ?: 0
}
