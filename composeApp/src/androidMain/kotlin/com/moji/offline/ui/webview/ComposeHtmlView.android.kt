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
    url: String?,
    articleBridge: Boolean,
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
                if (articleBridge) {
                    addJavascriptInterface(
                        ArticleJsBridge { method, payload -> messageHandler.value(method, payload) },
                        "ArticleJsInterface",
                    )
                    // index-fd51caf4.js reports readiness through CommonJsInterface.
                    addJavascriptInterface(
                        CommonJsBridge { method, payload -> messageHandler.value(method, payload) },
                        "CommonJsInterface",
                    )
                }
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
                if (url != null) {
                    // Original article page: a real document with ES-module imports.
                    loadUrl(url)
                } else {
                    // Original reader mechanism: content is loaded as a data string,
                    // but the base URL points at the loopback book server so the
                    // chapter's own relative resources (flow*.css, images,
                    // index.css, engine scripts) resolve exactly like ReaderWebView2.
                    loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
                }
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

/** `com.mojitec.hcbase` CommonJsInterface surface used by the H5 shells. */
private class CommonJsBridge(
    private val onMessage: (String, String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    private fun dispatch(method: String, payload: String) {
        main.post { onMessage(method, payload) }
    }

    @JavascriptInterface
    fun onJsLoaded() = dispatch("onJsLoaded", "")

    @JavascriptInterface
    fun trackEvent(payload: String?) = dispatch("trackEvent", payload.orEmpty())

    @JavascriptInterface
    fun callNative(payload: String?) = dispatch("callNative", payload.orEmpty())
}

/**
 * Mirrors `com.mojidict.read.widget.ArticleWebView.b` (base.apk 3.4.9).
 *
 * Every method used by the original H5 bundle is present so the page never
 * hits `undefined`; arguments are forwarded to the screen as
 * `method\u0001arg1\u0001arg2` (payloads may contain commas/quotes).
 */
private class ArticleJsBridge(
    private val onMessage: (String, String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    private fun dispatch(method: String, vararg args: Any?) {
        val payload = args.joinToString("\u0001") { it?.toString().orEmpty() }
        Log.d("MojiArticle", "$method=$payload")
        main.post { onMessage(method, payload) }
    }

    @JavascriptInterface fun MOJiReadingArticleDetailVCClickComment() = dispatch("clickComment")
    @JavascriptInterface fun MOJiReadingArticleDetailVCClickUnlock() = dispatch("clickUnlock")
    @JavascriptInterface fun MOJiReadingArticleDetailVCClickUnlockArticle() = dispatch("clickUnlockArticle")
    @JavascriptInterface fun actVote(params: String?) = dispatch("actVote", params)
    @JavascriptInterface fun articleToolboxPosition(json: String?) = dispatch("articleToolboxPosition", json)
    @JavascriptInterface fun articleScroll(top: String?) = dispatch("articleScroll", top)
    @JavascriptInterface fun btnClickTrigger(index: Int) = dispatch("btnClickTrigger", index)
    @JavascriptInterface fun buttonAiClick(content: String?) = dispatch("buttonAiClick", content)
    @JavascriptInterface fun changeAudioStatus(playing: Boolean) = dispatch("changeAudioStatus", playing)
    @JavascriptInterface fun changeMoveEventState(enabled: Boolean) = dispatch("changeMoveEventState", enabled)
    @JavascriptInterface fun clickArticleNote(title: String?, text: String?) = dispatch("clickArticleNote", title, text)
    @JavascriptInterface fun clickContent(content: String?) = dispatch("clickContent", content)
    @JavascriptInterface fun clickShowTranslation() = dispatch("clickShowTranslation")
    @JavascriptInterface fun finishReading() = dispatch("finishReading")
    @JavascriptInterface fun getSRTData(json: String?) = dispatch("getSRTData", json)
    // H5 `voterProcess` 第二个参数是 id 数组（`ArticleJsInterface.getVoteStatus(e, i)`）。
    // 收成 String 时 WebView 对不上重载，状态回不去，选项一直绑在 gotoLogin 上。
    @JavascriptInterface fun getVoteStatus(targetId: String?, voteIds: Array<String>?) =
        dispatch("getVoteStatus", targetId, voteIds?.joinToString(",").orEmpty())
    @JavascriptInterface fun getWords(html: String?): String = ""
    @JavascriptInterface fun goExercise() = dispatch("goExercise")
    @JavascriptInterface fun goNotebook() = dispatch("goNotebook")
    @JavascriptInterface fun gotoLogin() = dispatch("gotoLogin")
    @JavascriptInterface fun isNeedNotation(): Int = 0
    @JavascriptInterface fun onClickSRT(index: Int) = dispatch("onClickSRT", index)
    @JavascriptInterface fun openMember(type: String?) = dispatch("openMember", type)
    @JavascriptInterface fun playVoice(voiceId: String?, text: String?, type: String?) =
        dispatch("playVoice", voiceId, text, type)
    @JavascriptInterface fun scrollToPosition(offsetY: String?) = dispatch("scrollToPosition", offsetY)
    @JavascriptInterface fun sentenceAnalysisPos(pos: String?) = dispatch("sentenceAnalysisPos", pos)
    @JavascriptInterface fun showFoldStateTipsView() = dispatch("showFoldStateTipsView")
    @JavascriptInterface fun smoothScrollTo(offsetY: String?) = dispatch("smoothScrollTo", offsetY)
    @JavascriptInterface fun titleMaxY(value: Int) = dispatch("titleMaxY", value)
    @JavascriptInterface fun toTrans(text: String?): String = text.orEmpty()
    @JavascriptInterface fun viewUserInfo(userId: String?) = dispatch("viewUserInfo", userId)
    @JavascriptInterface fun voteErrorTips(tips: String?) = dispatch("voteErrorTips", tips)
    @JavascriptInterface fun getAudioUrlFromOSS(audioId: String?): String = ""

    // The H5 forwards these through registerReq() as well.
    @JavascriptInterface fun clickWord(text: String?) = dispatch("clickWord", text)
    @JavascriptInterface fun clickComment() = dispatch("clickComment")
    @JavascriptInterface fun clickSRT(index: Int) = dispatch("clickSRT", index)
    @JavascriptInterface fun setTransSwitchBtnHidden(hidden: Boolean) = dispatch("setTransSwitchBtnHidden", hidden)
    @JavascriptInterface fun showMenu(text: String?, rect: String?) = dispatch("showMenu", text, rect)
    @JavascriptInterface fun debugLog(message: String?) {
        Log.d("MojiArticle", "diag=$message")
    }
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
