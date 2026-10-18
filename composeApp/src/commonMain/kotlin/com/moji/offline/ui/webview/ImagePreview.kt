package com.moji.offline.ui.webview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.sp
import com.moji.offline.ui.PlatformBackHandler
import com.moji.offline.ui.interaction.clickableNoRipple
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * 正文配图预览层，照搬原版 `p079q5.b0`（Dialog）+
 * `com.mojidict.read.widget.ImagePreview`（base.apk 3.4.9）。
 *
 * 触发链路（逆向证据）：
 *   H5 `MOJiArticleDetail-2bef2f4b.js` 渲染完成后调用 `registerImageClickAction()`，
 *   给每个 img 绑 `onclick = location.href = "image-preview:" + this.src`；
 *   原生 `ArticleWebView$c.shouldOverrideUrlLoading`（ArticleWebView.java:340-400）
 *   命中 `image-preview:` 前缀就剥掉前缀、回调 `imagePreviewCallback.invoke(url)`
 *   并 return true，WebView 从不加载这个自定义 scheme；
 *   `ArticleDetailFragment:1591` 注册的回调最终执行
 *   `q5.b0(ctx).a(0, arrayOf(url)).show()`（ArticleDetailFragment:776）。
 *   阅读器同 scheme：`files/engine/epubReader/MOJiRegisterImageClick.js`。
 *
 * 几何（真机 Honor 1060x2376 @3.0，uiautomator + 像素实测）：
 *   Dialog window width/height = displayMetrics（1060x2376）+ setGravity(CENTER)
 *   + FLAG_FULLSCREEN，再 `ImagePreview.setTranslucentStatus(BarUtils
 *   .getStatusBarHeight())`，于是内容区 = [0,99]-[1060,2475]（底部 99px 被窗口裁掉）。
 *   实测：图片 band [0,989]-[1060,1585]（1920x1080 缩到屏宽，中心 1287 = 内容区中心）；
 *   保存按钮 [790,2247]-[1000,2361]（70x38dp、右 20dp、下 38dp 相对 y=2475）。
 *   弹层期间状态栏图标转白（黑底 #000000 铺满整屏，含状态栏）。
 *   单击任意处关闭（真机实测；原版另有双指缩放/长按/多图翻页，按裁剪清单不做）。
 *
 * 取图只走本机 mock（AGENTS.md 零之三）：正文注入时已把 oss.mojidict.com 改写成
 * `http://127.0.0.1:18080/article/images/...`，失败即显示原版失败层，不回落 CDN。
 */
object ImagePreview {
    /** 当前预览的图片地址；null = 未打开。 */
    var url: String? by mutableStateOf(null)

    /** 由 WebView 拦截器调用（对位原版 imagePreviewCallback）。 */
    fun open(rawUrl: String) {
        if (rawUrl.isNotBlank()) url = rawUrl
    }

    fun close() {
        url = null
    }
}

private enum class Phase { Loading, Ready, Failed }

/** 挂在 App 顶层：任何 WebView 触发 `image-preview:` 都能盖住整页。 */
@Composable
fun ArticleImagePreview() {
    val url = ImagePreview.url ?: return
    PlatformBackHandler(enabled = true) { ImagePreview.close() }

    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var phase by remember(url) { mutableStateOf(Phase.Loading) }
    var retry by remember(url) { mutableIntStateOf(0) }
    var saving by remember(url) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(url, retry) {
        phase = Phase.Loading
        val bytes = fetchImageBytes(url)
        val decoded = bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
        if (decoded != null) {
            bitmap = decoded
            phase = Phase.Ready
        } else {
            phase = Phase.Failed
        }
    }

    val density = LocalDensity.current
    // 内容区顶边落在状态栏之下、高度仍是整屏高（见文件头取证）。
    val statusBarPx = with(density) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx().roundToInt()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickableNoRipple { ImagePreview.close() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, statusBarPx) },
        ) {
            val shown = bitmap
            if (phase == Phase.Ready && shown != null) {
                // 对位 ImageView.ScaleType.CENTER_INSIDE：等比缩小居中，不放大。
                Image(
                    bitmap = shown,
                    contentDescription = null,
                    contentScale = ContentScale.Inside,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (phase != Phase.Ready) {
                // rl_load_fail：#88000000 蒙层 + 居中的 ProgressView / 失败提示。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x88000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (phase == Phase.Loading) {
                        MojiLoadSpinner(Modifier.width(40.dp).height(40.dp))
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("图片加载失败或图片不存在", fontSize = 14.sp, color = Color.White)
                            Text(
                                "点击重试",
                                fontSize = 14.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                                    .clickableNoRipple { retry++ }
                                    .padding(horizontal = 16.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            // tv_save：dialog_preview_imageview.xml line 69，
            // 70x38dp / marginRight 20dp / marginBottom 38dp /
            // save_img_btn = #49535C + 8dp 圆角（color/user_profile_bg_color）。
            if (phase == Phase.Ready) {
                Text(
                    text = "保存",
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 38.dp)
                        .width(70.dp)
                        .height(38.dp)
                        .background(Color(0xFF49535C), RoundedCornerShape(8.dp))
                        .clickableNoRipple(enabled = !saving) {
                            saving = true
                            scope.launch {
                                val bytes = fetchImageBytes(url)
                                val name = url.substringBefore('?')
                                    .substringAfterLast('/').ifBlank { "image.jpg" }
                                val ok = bytes != null && saveImageToGallery(bytes, name)
                                showPlatformToast(if (ok) "保存成功" else "保存失败,请稍后再试")
                                saving = false
                            }
                        },
                )
            }
        }
    }
}

/** 对位 com.mojidict.read.widget.ProgressView：40dp 旋转弧线。 */
@Composable
private fun MojiLoadSpinner(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "preview-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000, easing = LinearEasing),
        ),
        label = "preview-spinner-angle",
    )
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        val side = size.minDimension - stroke
        drawArc(
            color = Color.White.copy(alpha = 0.85f),
            startAngle = angle,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
            size = Size(side, side),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
