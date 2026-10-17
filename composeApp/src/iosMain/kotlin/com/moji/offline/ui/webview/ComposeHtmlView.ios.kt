package com.moji.offline.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGPointMake
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSNumber
import platform.UIKit.UIColor
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.darwin.NSObject

actual val platformName: String = "ios"

/** Message names registered both here and in Bridge.js (iOS branch). */
private val bridgeMethods = listOf(
    "onJsLoaded",
    "setHorizontalPageCount",
    "setCurrentInnerPage",
    "clickMOJiArticleContent",
    "selectMOJiArticleSentence",
    "analysisMOJiArticleParagraph",
    "onGotoPage",
)

private class ScriptMessageHandler(
    private val onMessage: (String, String) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val name = didReceiveScriptMessage.name
        val payload = when (val body = didReceiveScriptMessage.body) {
            is String -> body
            is NSNumber -> body.stringValue
            else -> body?.toString().orEmpty()
        }
        onMessage(name, payload)
    }
}

@OptIn(ExperimentalForeignApi::class)
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
    UIKitView(
        modifier = modifier,
        factory = {
            val contentController = WKUserContentController()
            bridgeMethods.forEach { name ->
                contentController.addScriptMessageHandler(
                    ScriptMessageHandler { method, payload -> messageHandler.value(method, payload) },
                    name,
                )
            }
            if (articleBridge) {
                contentController.addScriptMessageHandler(
                    ScriptMessageHandler { _, payload -> messageHandler.value("articleBridge", payload) },
                    "articleBridge",
                )
                // index-fd51caf4.js uses the iOS UA branch on WKWebView.
                contentController.addScriptMessageHandler(
                    ScriptMessageHandler { _, _ -> messageHandler.value("onJsLoaded", "") },
                    "webCoreDidLoadFinish",
                )
                // The original article bundle targets Android and calls
                // window.ArticleJsInterface.* directly; bridge it to WKWebView.
                contentController.addUserScript(
                    WKUserScript(
                        source = ARTICLE_BRIDGE_SHIM,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = true,
                    ),
                )
            }
            val configuration = WKWebViewConfiguration().apply {
                userContentController = contentController
            }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                scrollView.scrollEnabled = false
                controller.evaluate = { script -> evaluateJavaScript(script, null) }
                controller.scrollToX = { x ->
                    scrollView.setContentOffset(CGPointMake(x.toDouble(), 0.0), false)
                }
                controller.evaluateWithResult = { script, onResult ->
                    evaluateJavaScript(script) { result, _ -> onResult(result?.toString().orEmpty()) }
                }
                controller.readScrollX = {
                    scrollView.contentOffset.useContents { x.toInt() }
                }
                if (url != null) {
                    loadRequest(NSURLRequest(uRL = NSURL(string = url)))
                } else {
                    // Same as the Android side: string content, loopback base URL.
                    loadHTMLString(html, baseURL = baseUrl?.let { NSURL(string = it) })
                }
            }
        },
        update = { webView ->
            controller.evaluate = { script -> webView.evaluateJavaScript(script, null) }
        },
    )
}

private val ARTICLE_BRIDGE_SHIM = """
    (function () {
      if (window.ArticleJsInterface) return;
      var names = [
        "MOJiReadingArticleDetailVCClickComment","MOJiReadingArticleDetailVCClickUnlock",
        "MOJiReadingArticleDetailVCClickUnlockArticle","actVote","articleToolboxPosition",
        "btnClickTrigger","buttonAiClick","changeAudioStatus","changeMoveEventState",
        "clickArticleNote","clickContent","clickShowTranslation","finishReading",
        "getSRTData","getVoteStatus","getWords","goExercise","goNotebook","gotoLogin",
        "isNeedNotation","onClickSRT","openMember","playVoice","scrollToPosition",
        "sentenceAnalysisPos","showFoldStateTipsView","smoothScrollTo","titleMaxY",
        "toTrans","viewUserInfo","voteErrorTips","getAudioUrlFromOSS",
        "clickWord","clickComment","clickSRT","setTransSwitchBtnHidden","showMenu","articleScroll"
      ];
      var bridge = {};
      names.forEach(function (name) {
        bridge[name] = function () {
          try {
            window.webkit.messageHandlers.articleBridge.postMessage(
              JSON.stringify({ name: name, args: Array.prototype.slice.call(arguments) })
            );
          } catch (e) {}
          if (name === "toTrans") return arguments[0] || "";
          if (name === "isNeedNotation") return 0;
          return "";
        };
      });
      window.ArticleJsInterface = bridge;
    })();
""".trimIndent()
