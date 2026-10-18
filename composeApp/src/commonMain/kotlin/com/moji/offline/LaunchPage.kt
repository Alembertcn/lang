package com.moji.offline

/**
 * 启动时要打开的页面。由 Android Intent extra `page` 或 iOS 进程参数 `--page`
 * 在首帧前写入。空字符串停在发现页。
 *
 * 取值：discover、reader、study、mine、settings、history、favorites。
 */
object LaunchPage {
    var raw: String = ""
}
