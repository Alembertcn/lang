# 逆向还原 · 页面仿造 · 接口总览

> 三条线索的入口文档：**页面仿造**（照着原版做 UI/交互）、**接口**（数据与 mock）、
> **逆向还原**（从 APK 里挖出了什么、怎么挖的）。
>
> 相关文档：`AGENTS.md`（工作规则）、`PLAN.md`（进度表）、`ASSETS.md`（资产外置）、
> `content/engine/INTEGRATION.md`（阅读器机制与验收）、
> `reference/moji-api/接口说明.md`（后台 REST 明细）、
> `reference/moji-read/moji逆.md`（ArticleActivity 逆向笔记）。

## 一、页面仿造（现状与依据）

仿造依据优先级（见 AGENTS.md）：APK 布局/尺寸 → 图标 drawable → 颜色采样 → 行为清单 →
操作序列对照（原版 vs 高仿同一组操作）。

| 页面 | 原版对应 | 本实现 | 状态 |
| --- | --- | --- | --- |
| 底部 4 Tab | `ic_home_*` def/sel 双态图标、品牌粉 #E0325C | `App.kt` | ✅ |
| 发现页 | `FindFragment` → **只保留「专栏推荐」段**（用户确认范围）：吸顶「发现」栏 + 分类条（专栏推荐/考试/时事/生活/文学/娱乐）+ 专栏卡（封面/标题/2 篇最新文章） | `feature/discover/DiscoverScreen.kt` + `data/catalog/FindFeed.kt` | ✅ 高仿完成（头图/圈子/短文推荐/阅读计划/NOW 段已按用户要求移除；数据与图片仍在 `files/find/`，可随时恢复） |
| 阅读器书架 | `ReaderLibraryListFragment`（全部/我的/官方 + 3 列网格 + 上传卡位） | `feature/reader/ReaderScreen.kt` | ✅ |
| 书籍正文阅读器 | `ReaderActivity` + `activity_reader` + `ReaderPageFragment`（ViewPager 主轴 + WebView 显示层 + 目录抽屉 + 页码） | `feature/reader/ReaderBookScreen.kt` + `data/reader/*` | ✅ 首版（逐项对齐中，见 INTEGRATION.md 遗留清单） |
| 阅读页 ⋮ 设置 / 「析」/ 右下按钮 | `ReaderWebView2` 接口 + `tv_pageInfo` / 浮动按钮 | 部分（析已开；⋮ 菜单、右下按钮待做） | ⏳ |
| 短文详情页 | `ArticleActivity` → `ArticleDetailFragment`：`activity_base_detail`（44dp `MojiToolbar` + 专栏名 14sp bold @48dp）+ 原版 H5（`file:///android_asset/web/mojiread/article/android/index.html`）+ `ArticleDetailAudioPlayerView` | `feature/article/ArticleDetailScreen.kt` + `data/article/*`（原版 H5 由 `BookServer` 本机供给） | ✅ 首版（真机验证；遗留见 PLAN P6c） |
| 词卡（查词详情） | `assets/WordDetail` H5 + Bridge | H5 资产已就绪，待接 SQLDelight 词库 | ⏳ |
| 学习 / 我的 | `i5.*` / `q5.*` 等（未展开） | — | ⏳ |

## 二、接口（数据抓取与本地 mock）

### 2.1 后台 REST（计划/账号）

基址 `https://api.mojidict.com/app/mojiread/api`，登录后带 `X-MOJI-TOKEN`。
抓取脚本：`tools/fetch_moji_plans.py` → `reference/moji-api/responses/`；明细见
`reference/moji-api/接口说明.md`。

| 接口 | 说明 |
| --- | --- |
| `POST /v1/account/unifiedLogin` | 账密换 token（不落盘） |
| `GET /v1/readPlan/list` / `mine` | 计划列表 / 我的计划 |
| `GET /v1/readPlan/{planId}/detail` | 计划详情（章节+文章元数据） |

### 2.2 RPC 命令（阅读器/文章/书籍）

基址 `https://api.mojidict.com/app/mojiread/parse/functions/{name}`（POST，JSON）。
抓取脚本：`tools/fetch_moji_articles.py`、`tools/fetch_moji_reader.py`。

| 命令 | 用途 | 落盘位置 |
| --- | --- | --- |
| `reading-article-detail` | 文章详情（含正文 URL、音频 id 等） | `articles/{id}/detail.json` |
| `fetchTargetSrcUrl_v2` | 正文 zip 下载地址（html 条目） | `articles/{id}/body.html` |
| `fav-listSens` | 文章句子（精读/时间轴单位） | `articles/{id}/sentences.json` |
| `article-voice-list` | 文章配音列表 | `articles/{id}/voice-list.json` |
| `books-incFetch-v2` | 书架书籍（objectId/contentId/coverId/title） | `reader/books-incFetch-v2.json` |
| `bookLibs-hot` | 热门书库 | `reader/bookLibs-hot.json` |
| `reading-column-list` / `column-mySubscribed` | 专栏列表 / 我的订阅 | `reader/*.json` |
| `reading-column-directory` / `-content` / `-audioList` | 专栏目录/内容/音频 | `reader/columns/{id}/*.json` |
| `reading-histories` | 阅读历史 | `reader/reading-histories.json` |
| `reading-article-everyDayOne` | 每日一文 | `reader/reading-article-everyDayOne.json` |
| `tts-voice-actors-fetch` | TTS 音色列表 | `reader/tts-voice-actors-fetch.json` |
| `circle-simpleList` | 发现页圈子（5 个 + 图标路径） | `find/circle-simpleList.json` |
| `readingCnt-privateRecmArticles` / `-Columns` | 发现页推荐（短文推荐 / 专栏推荐） | `find/*.json` |
| `homepage-articles` | 首页列表项 → 本地文件的编排（发现页正文共用 `articles/{id}/`） | `find/homepage-articles.json` |
| `nanews/list`（REST `/v1/nanews/list`） | 新闻列表 | `find/nanews.list.json` |
| `GET /v1/nanews/{objectId}?isNlt=true`（REST） | 新闻详情；`isPolling=true` 时要轮询到 `notationHtmlLevels` 出现 | `find/news/{id}/detail.json` |
| `clockInDaily`（REST `/v1/clockInDaily`） | 打卡/阅读时长 | `find/clockInDaily.json` |
| `readingCnt-category` | 专栏推荐分类 tab（考试/时事/生活/文学/娱乐） | `find/category/*.json` |

### 2.3 OSS 直链（静态资源）

基址 `https://oss.mojidict.com`，本地镜像树 `reference/moji-api/oss/`：

| 路径 | 内容 | 脚本 |
| --- | --- | --- |
| `/books/{objectId}/{contentId}.zip` | 书籍包（外包 zip → 真 epub） | `fetch_moji_books.py` |
| `/books/{objectId}/{coverId}` | 书籍封面 | `fetch_moji_covers.py` |
| `/article/audio/{audioId}` | 文章音频 | `fetch_moji_audio.py`（audio-index.json 建索引） |
| `/nanews/{objectId}-notation-{level}.zip` | 新闻注音正文（解出 `body.html`；`level` 优先 `n3n4n5`） | `fetch_moji_find_articles.py` |

### 2.4 运行时接口（app 内部）

| 接口 | 方向 | 说明 |
| --- | --- | --- |
| `GET /books/{bookId}/{path...}` | WebView → `BookServer` | 本机书服（对位原版 NanoHTTPD `ea.c`）：先查 1:1 解包 epub，未命中按文件名回退引擎资产 |
| `ReaderJsInterface.*`（Android）/ `webkit.messageHandlers.*`（iOS） | 引擎 → native | `onJsLoaded`、`setHorizontalPageCount`、`setCurrentInnerPage`、`clickMOJiArticleContent`、`selectMOJiArticleSentence`、`analysisMOJiArticleParagraph` |
| `Main(...)` / `MOJiArticleDetail*` / `scrollToPageIndex` | native → 引擎 | 初始化、主题/字号、翻页 |
| `CONTENT_BASE_URL`、`QWEN_*`（env） | 规划中 | 内容更新校验、千问分析/TTS（`env.example`） |

### 2.5 本地 mock

**接口数据**已经全量落盘在 `reference/moji-api/`（每个接口 ↔ 一份响应的对照表见
`reference/moji-api/接口说明.md` 文末「本地缓存 ↔ 线上地址」）。模块实现按类型二选一：

- **纯数据**（发现页、阅读库、文章精读）：`convert_moji_articles.py` /
  `fetch_find_assets.py` 打包进 `composeResources`，离线可用；
- **要跑协议 / JS**（阅读引擎、词卡 H5、音频）：本机 HTTP 服务兜底，app 侧 base URL
  指向 `127.0.0.1`（书籍走 `BookServer`，媒体走下面的静态服务）。

**静态资源**（`oss/` 与线上同构，472 MB，gitignore）：

```bash
tools/start_media_mock.sh   # :8765 -> reference/moji-api/oss（screen 常驻）
                            # adb reverse 18080 -> 8765（手机端）
# local.properties: MOCK_MEDIA_BASE=http://127.0.0.1:18080
```

新增模块的固定动作见 `PLAN.md`「高仿工作流」：先定位接口 → 抓取落盘 → 同步接口文档
→ 按缓存实现 → 同一页对照验收。

### 2.6 短文详情页的数据/资源路径（P6c）

原版正文不是原生排版，而是**原版 H5** 在 WebView 里渲染：

| 环节 | 原版证据 | 本实现 |
| --- | --- | --- |
| 页面入口 | `ArticleActivity.onCreate` → `setDefaultContentView(false)` → 装入 `ArticleFragment`/`ArticleDetailFragment` | `App.kt` 的 `openArticle` 全屏路由（系统返回键回到发现页） |
| 页面骨架 | `res/layout/activity_base_detail.xml`：`MojiToolbar` 44dp、`ll_article_detail_column_container` 48dp 边距、`ll_toolbox` 56dp、`ArticleDetailAudioPlayerView` | `ArticleTopBar` + 吸顶工具条 + `ArticleAudioBar` |
| 正文加载 | `ArticleWebView` 加载 `file:///android_asset/web/mojiread/article/android/index.html`；`index-fd51caf4.js` 回调 `CommonJsInterface.onJsLoaded` | `BookServer` 在回环 HTTP 上提供同路径文档（H5 的绝对 `/android_asset/...` 资源原样解析） |
| 正文注入 | `ArticleDetailFragment` 构造 `ArticleJsParams`，`evaluateJavascript("MOJiArticleDetailDidSetHTML(json, body, underlines)")` | 同样调用；`body` = `reference/moji-api/articles/{id}/body.html` 的 1:1 副本 |
| 工具按钮 | `ArticleWebView.z(i)` → `MOJiArticleToolsCtrl(i,'true')`（0 JLPT / 1 辅助阅读 / 3 笔记） | 注入后显示 1（辅助）与 3（笔记）；滚动过工具行后原生 56dp 吸顶条出现（`coordinateToolbox`） |
| 播放条 | `layout_audio_player_manager`：14sp 时间、2dp seek、70x38 精听胶囊、36/40/36 传输键、24dp 六边形设置 + 速度角标 | `ArticleAudioBar`（同尺寸）；音频来自 `MOCK_MEDIA_BASE/article/audio/{audioId}` |
| 全局播放面板 | `activity_main.xml`：`AudioPlayerControllerView`（64dp、16dp 上圆角、白/#1C1C1E、translationZ 20dp）位于 `fl_main_container` 与 55dp `tb_main_bottom_bar` 之间（`layout_above` 链，内容区被顶起）；`layout_audio_player_controller`：关闭 44dp / 封面 44dp r8 / 标题 16sp / 24dp 环形进度+播放键 / 列表 44dp | `App.kt` 的 Scaffold bottomBar（`GlobalMiniPlayer`）+ `ArticlePlayer` 应用级会话；点面板回到文章、播放位置保持 |

资源由 `tools/build_article_assets.py` 生成（gitignore）：
`files/article_h5/`（原版 H5 + Noto Sans JP）、`files/article_detail/{id}.{json,html}`（299 篇）。

## 三、逆向还原（资产与机制）

### 3.1 资产清单

| 资产 | 来源 | 提取物 | 工具/位置 |
| --- | --- | --- | --- |
| 词库 | `assets/Database/zh-CN_ja/*.zip`（zip 密码 + Realm key） | 44 万词条 SQLite | `tools/mojidict-export` → `content/mojidict/dictionary.db` |
| H5 页面 | `assets/WordDetail`、`assets/web/mojiread`、h5hybrid 包 | 词卡/文章渲染/题目/分析 | `content/h5/` |
| 图标/图形 | `res/drawable-xxhdpi-v4` 等 | `ic_home_*`、`ic_find_*` 等 | `composeResources/drawable/` |
| 阅读引擎 | CDN `libs-biz.mojidict.com/h5hybrid/epuReader/v1_3_3.zip`（profile_android.json 确认版本） | Bridge.js + Readium CFI + 高亮/取词 | `content/engine/epuReader-v1_3_3.zip`；运行时副本 `composeResources/files/engine/epubReader/` |
| 书籍 | OSS 书籍包 → epub | 9 本原版 epub | `fetch_moji_books.py` → `unpack_moji_books.py`（1:1 解包） |
| 文章 | RPC + 正文 zip + SRT | 170 篇文章（正文/时间轴/翻译/句子/配音表） | `convert_moji_articles.py` → `composeResources/files/articles/` |
| 音频 | OSS | 178 段 mp3 + 索引 | `fetch_moji_audio.py` |
| 发现页数据/图片 | RPC + OSS（圈子图标、文章/计划/专栏封面） | `feed.json` + 4 组图片目录 | `tools/fetch_find_assets.py`（生成物 gitignore） |
| 发现页正文 / 新闻 | RPC + 正文 zip + OSS 注音 zip | 94 篇首页文章 + 20 条新闻正文 | `tools/fetch_moji_find_articles.py` → `articles/{id}/`、`find/news/{id}/` |

### 3.2 阅读器机制（关键结论，证据均来自 base.apk）

1. `ReaderActivity.l()` → `http://127.0.0.1:{port}/{bookId}/`（**本机 HTTP 服务**）；
   `ea.c extends org.nanohttpd.protocols.http.NanoHTTPD` 提供容器路由
   （`/manifest.json`、`/search`、`/media-overlay`、`/scripts|styles|fonts/*`、catch-all）。
2. `ReaderPageFragment.setHtml()`：
   `loadDataWithBaseURL(本机URL + 章节目录, 章节原文, "text/html")` —— **零转换直载**。
3. epub 章节自带 `index.css` + 10 个引擎脚本引用 + `moji-id`；引擎文件由服务兜底供给。
4. 翻页同步 = `ReaderWebView2.onLayout`（`w=ceil(视宽/density)*density`）+ `X4.a.onPageScrolled`
   （`scrollTo(ceil(page*w)+offsetPx)`）；实现中另加整网格 snap 修正 Chrome 列拉伸
   （详见 INTEGRATION.md）。
5. `Bridge.js` 分页模型：`preInit` 设列宽/列距（= body padding），`postInit` 用
   `scrollWidth/clientWidth` 得页数并回调 `setHorizontalPageCount`。

### 3.3 逆向工作流（怎么挖的）

- 工具：REA + jadx-headless（`reference/moji-read/base.apk`），类/方法搜索 → 方法体 →
  引用追踪；结论在 `moji逆.md` / `INTEGRATION.md` 附证据。
- 三层挖掘（缺一不可）：**静态资产**（assets/res）→ **运行时下载资产**（CDN profile/zip，
  如阅读引擎）→ **行为规格**（交互/桥协议/时序，真机对照）。

### 3.4 复现命令

```bash
cp env.example .env            # 填 MOJI_ACCOUNT/MOJI_PASSWORD（重抓接口）、QWEN_*（AI）
tools/fetch_assets.sh          # 词库等外置资产
python3 tools/fetch_moji_books.py && python3 tools/unpack_moji_books.py
python3 tools/fetch_moji_find.py && python3 tools/fetch_find_assets.py   # 发现页数据与图片
python3 tools/fetch_moji_find_articles.py                               # 发现页正文 + 新闻
./gradlew :composeApp:assembleDebug
```
