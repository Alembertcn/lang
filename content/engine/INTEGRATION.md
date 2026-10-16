# 阅读器对接（原版路径）：机制、实现、验收

> 2026-10-16 重写。上一版方案（epub → 自造分页 HTML、内联引擎、丢原版 CSS）
> 已废弃并完整保留在 git 分支 `archive/converted-html-reader`；废弃原因见文末附录。
> 现行实现严格照抄原版：**原文件零改动 + 本机 HTTP 服务 + `loadDataWithBaseURL`**。

## 一、原版机制（APK 证据，全部来自 `reference/moji-read/base.apk`）

1. **本机 HTTP 服务**：`ReaderActivity.l()` 返回
   `"http://127.0.0.1:{port}/{bookId}/"`；服务本体 `ea.c extends
   org.nanohttpd.protocols.http.NanoHTTPD`（路由：`/manifest.json`、`/search`、
   `/media-overlay`、`/scripts|styles|fonts/*`、catch-all `/(.*)`）。
2. **章节加载**：`ReaderPageFragment.setHtml()` 调
   `webView.loadDataWithBaseURL(baseUrl + 章节目录, htmlString, "text/html", "UTF-8", null)`
   —— 章节 HTML 原文直载，资源（flow*.css、图片、index.css、引擎脚本）经 baseURL
   从本机服务解析。
3. **章节 HTML 自带引擎引用**：OSS 的 epub 章节 `<head>` 里有
   `<link rel="stylesheet" href="index.css">`，`<body>` 末尾有 10 个引擎脚本
   （jquery → jsface → readium-cfi → rangy* → web-highlighter → collect-selection
   → MOJiRegisterImageClick → Bridge.js），正文元素带 `moji-id`。**这些引擎文件
   不在 epub 内**，由服务端按文件名兜底供给。
4. **翻页同步**（精确移植的两个公式）：
   - `ReaderWebView2.onLayout`：`w = ceil(viewWidthPx / density) * density`
     （视宽对齐到整 CSS 像素）；
   - `X4.a.onPageScrolled(page, offset, offsetPx)`：
     `webView.scrollTo(ceil(page * w) + offsetPx, 0)`。
5. **分页引擎**：`Bridge.js` 的 `preInitHorizontalDirection` 用 body padding 设
   `columnWidth = clientWidth - 2*padding`、`columnGap = 2*padding`；
   `postInitHorizontalDirection` 由 `scrollWidth / clientWidth` 得页数并经
   `setHorizontalPageCount` 回调 native 驱动 ViewPager 页数。

## 二、本仓库实现映射

| 原版 | 本实现（KMP，双端一套） |
| --- | --- |
| NanoHTTPD 本机服务 | `data/reader/BookServer.kt`（ktor-server-cio，127.0.0.1 随机端口） |
| `ea.c` 容器路由 | `GET /books/{bookId}/{path...}` → 先查解包 epub，未命中按文件名回退到引擎资产 |
| epub 解包目录 | `composeApp/src/commonMain/composeResources/files/books/{bookId}/**`（1:1，由 `tools/unpack_moji_books.py` 生成，gitignore） |
| 引擎资产（index.css / 脚本） | `composeApp/src/commonMain/composeResources/files/engine/epubReader/**`（源自 `epuReader-v1_3_3.zip`） |
| `loadDataWithBaseURL` | `ComposeHtmlView(html, baseUrl, ...)`：Android `loadDataWithBaseURL`；iOS `loadHTMLString(html, baseURL)` |
| ViewPager + WebView 像素同步 | `ReaderBookScreen.kt`：外层章节 Pager + 内层页 Pager + 原版公式的 `scrollTo` |
| 章节清单 | `data/reader/BookPackage.kt`：运行时解析 `META-INF/container.xml → OPF → spine` + `toc.ncx` 标题 |

**注意**：引擎资产运行时副本在 composeResources（上面表格）；`content/engine/`
保留 `epuReader-v1_3_3.zip`（原始下载包）与本文档。

## 三、验收记录

- 2026-10-16 真机（Sony XQ-AT72）：
  - 书架 → 刺青：扉页、正文按原版 CSS 渲染；`ruby` 注音、`moji-id` 完整；
  - 引擎回调：`onJsLoaded → Main(...)`、`setHorizontalPageCount=17`（第 2 章）；
  - 翻页：前进 4 页 / 后退 2 页，页边界与列边界一致（原版公式）；
  - 目录：跳「正文 3」成功（1/4 页）；
  - 与原版 App 同书同页逐行比对：断行、注音、行距一致。
- 实现细节：引擎离屏测量时会回调 `Int.MAX_VALUE`，native 侧忽略 `1..2000`
  之外的值。

## 四、遗留差异清单（逐项对齐中）

1. `analysisParagraphButton`：原版显示「析」段落标记，我们暂传 `false`；
2. 底部页码：原版是**全书级**（如 2/21），我们显示章内页（x/17）；
3. 目录样式：原版是左侧抽屉（`dl_left` DrawerLayout），我们是居中弹窗；
4. ⋮ 更多菜单（字号四档 / 主题）未实现（引擎接口 `MOJiArticleDetailSetFontSize` /
   `DidSetTheme` 已具备）；
5. 阅读页右下角圆形按钮（笔记/精读入口）未实现；
6. 暗色主题配色需与原版逐项采样核对；
7. iOS 壳工程未建；建好后需在 Info.plist 放行 loopback（ATS
   `NSAllowsLocalNetworking`）。

## 附录：上一版方案为什么废弃

旧方案把 epub 转成自造 HTML（丢 `flow*.css`、`moji-id`、脚本引用，另写分页
CSS），再把引擎内联进生成物，用 `loadDataWithBaseURL(null, ...)` 加载。与原版
的差异是结构性的：字体/缩进/断行/注音样式全部改变，且 HTML 里没有 `moji-id`
导致后续取词/选中链路无从谈起。真机逐行对照后确认无法靠"修修补补"对齐，
遂整体回退到原版路径。旧实现（含转换器与当时的高仿阅读页）保留在分支
`archive/converted-html-reader`（tip `f8bdada`）。
