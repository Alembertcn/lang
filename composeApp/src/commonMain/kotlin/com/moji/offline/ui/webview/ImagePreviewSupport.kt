package com.moji.offline.ui.webview

/**
 * 配图预览需要的三项平台能力（对位原版 `p079q5.b0` 里的 Glide 取图、
 * `save_image_success`／`save_image_error` 的相册保存与 Toast 提示）。
 *
 * 取图地址只允许来自本机 mock（AGENTS.md 零之三），预览层不做任何回落。
 */

/** 取回一张图的原始字节；失败返回 null（原版 rl_load_fail 态）。 */
expect suspend fun fetchImageBytes(url: String): ByteArray?

/** 保存到系统相册，成功返回 true。 */
expect suspend fun saveImageToGallery(bytes: ByteArray, displayName: String): Boolean

/** 系统 Toast（原版保存后弹「保存成功」/「保存失败,请稍后再试」）。 */
expect fun showPlatformToast(message: String)
