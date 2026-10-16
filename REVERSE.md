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
| 发现页 | `item_tab_article` 卡片规格 + 搜索栏/等级筛选 | `feature/discover/DiscoverScreen.kt` | ✅ 主体（顶部粉色背景/时长栏/圈子入口待补） |
| 阅读器书架 | `ReaderLibraryListFragment`（全部/我的/官方 + 3 列网格 + 上传卡位） | `feature/reader/ReaderScreen.kt` | ✅ |
| 书籍正文阅读器 | `ReaderActivity` + `activity_reader` + `ReaderPageFragment`（ViewPager 主轴 + WebView 显示层 + 目录抽屉 + 页码） | `feature/reader/ReaderBookScreen.kt` + `data/reader/*` | ✅ 首版（逐项对齐中，见 INTEGRATION.md 遗留清单） |
| 阅读页 ⋮ 设置 / 「析」/ 右下按钮 | `ReaderWebView2` 接口 + `tv_pageInfo` / 浮动按钮 | 部分（析已开；⋮ 菜单、右下按钮待做） | ⏳ |
| 文章精读 + 播放器 | `ArticleActivity`（见 moji逆.md） | 方案已验证，待移植 | ⏳ |
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

### 2.3 OSS 直链（静态资源）

基址 `https://oss.mojidict.com`，本地镜像树 `reference/moji-api/oss/`：

| 路径 | 内容 | 脚本 |
| --- | --- | --- |
| `/books/{objectId}/{contentId}.zip` | 书籍包（外包 zip → 真 epub） | `fetch_moji_books.py` |
| `/books/{objectId}/{coverId}` | 书籍封面 | `fetch_moji_covers.py` |
| `/article/audio/{audioId}` | 文章音频 | `fetch_moji_audio.py`（audio-index.json 建索引） |

### 2.4 运行时接口（app 内部）

| 接口 | 方向 | 说明 |
| --- | --- | --- |
| `GET /books/{bookId}/{path...}` | WebView → `BookServer` | 本机书服（对位原版 NanoHTTPD `ea.c`）：先查 1:1 解包 epub，未命中按文件名回退引擎资产 |
| `ReaderJsInterface.*`（Android）/ `webkit.messageHandlers.*`（iOS） | 引擎 → native | `onJsLoaded`、`setHorizontalPageCount`、`setCurrentInnerPage`、`clickMOJiArticleContent`、`selectMOJiArticleSentence`、`analysisMOJiArticleParagraph` |
| `Main(...)` / `MOJiArticleDetail*` / `scrollToPageIndex` | native → 引擎 | 初始化、主题/字号、翻页 |
| `CONTENT_BASE_URL`、`QWEN_*`（env） | 规划中 | 内容更新校验、千问分析/TTS（`env.example`） |

### 2.5 本地 mock

```bash
tools/start_media_mock.sh   # :8765 -> reference/moji-api/oss（screen 常驻）
                            # adb reverse 18080 -> 8765（手机端）
# local.properties: MOCK_MEDIA_BASE=http://127.0.0.1:18080
```

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
./gradlew :composeApp:assembleDebug
```
