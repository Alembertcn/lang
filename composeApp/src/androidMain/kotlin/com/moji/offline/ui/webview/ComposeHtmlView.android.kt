package com.moji.offline.ui.webview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.graphics.Color as AndroidColor
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.JavascriptInterface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

actual val platformName: String = "android"

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun ComposeHtmlView(
    html: String,
    baseUrl: String?,
    controller: WebViewController,
    onMessage: (method: String, payload: String) -> Unit,
    modifier: Modifier,
) {
    val messageHandler = rememberUpdatedState(onMessage)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.defaultTextEncodingName = "utf-8"
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                addJavascriptInterface(
                    ReaderJsBridge { method, payload -> messageHandler.value(method, payload) },
                    "ReaderJsInterface",
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d("MojiWeb", "${message.message()} @${message.lineNumber()}")
                        return true
                    }
                }
                controller.evaluate = { script -> evaluateJavascript(script, null) }
                controller.scrollToX = { x -> scrollTo(x.roundToInt(), 0) }
                controller.evaluateWithResult = { script, onResult ->
                    evaluateJavascript(script) { result -> onResult(result.orEmpty()) }
                }
                controller.readScrollX = { scrollX }
                // Original mechanism: content is loaded as a data string, but the
                // base URL points at the loopback book server so the chapter's own
                // relative resources (flow*.css, images, index.css, engine scripts)
                // resolve exactly like the original ReaderWebView2.
                loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            controller.evaluate = { script -> webView.evaluateJavascript(script, null) }
            controller.scrollToX = { x -> webView.scrollTo(x.roundToInt(), 0) }
            controller.evaluateWithResult = { script, onResult ->
                webView.evaluateJavascript(script) { result -> onResult(result.orEmpty()) }
            }
            controller.readScrollX = { webView.scrollX }
        },
    )
}

/** Mirrors the original app's ReaderJsInterface surface used by Bridge.js. */
private class ReaderJsBridge(
    private val onMessage: (String, String) -> Unit,
) {
    // @JavascriptInterface callbacks arrive on the JS bridge thread; WebView
    // APIs (evaluateJavascript / scrollTo) must run on the UI thread.
    private val main = Handler(Looper.getMainLooper())

    private fun dispatch(method: String, payload: String) {
        Log.d("MojiBridge", "$method=$payload")
        main.post { onMessage(method, payload) }
    }

    @JavascriptInterface
    fun onJsLoaded() = dispatch("onJsLoaded", "")

    @JavascriptInterface
    fun setHorizontalPageCount(count: Int) = dispatch("setHorizontalPageCount", count.toString())

    @JavascriptInterface
    fun setCurrentInnerPage(page: Int) = dispatch("setCurrentInnerPage", page.toString())

    @JavascriptInterface
    fun clickMOJiArticleContent(text: String?) = dispatch("clickMOJiArticleContent", text.orEmpty())

    @JavascriptInterface
    fun selectMOJiArticleSentence(payload: String?) = dispatch("selectMOJiArticleSentence", payload.orEmpty())

    @JavascriptInterface
    fun analysisMOJiArticleParagraph(payload: String?) = dispatch("analysisMOJiArticleParagraph", payload.orEmpty())

    @JavascriptInterface
    fun onGotoPage(page: Int) = dispatch("onGotoPage", page.toString())
}
