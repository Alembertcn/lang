# 开发计划与进度

**Lang**（品牌 **MJ**）——原版 MOJi阅读离线高仿（`com.moji.offline`）。本表记录阶段目标与状态，目录/工具/mock
等详细说明见 [README.md](README.md)。

三份配套文档：**[高仿工作流](#高仿工作流每个模块开工前先做这五步)**（下）——
每个模块的固定动作；`reference/moji-api/接口说明.md`——接口与本地缓存的对照
（抓取脚本的规格书）；`REVERSE.md`——页面仿造与逆向结论总览。

## 阶段表

| 阶段 | 目标 | 状态 | 备注 |
| --- | --- | --- | --- |
| P0 | 逆向资产：词库 / H5 / 图标 / 接口数据 | ✅ 完成 | 词库 44 万行导出；299 篇文章（205 计划 + 94 发现）+ 178 段音频 + 9 本书已抓取 |
| P1 | KMP 工程骨架 + env 配置链路 | ✅ 完成 | 真机跑通；CMP 锁定 1.11.1（SDK 36 约束） |
| P2 | 真实文章数据接入 | ✅ 完成 | 170 篇转换（SRT 时间轴 + 原文 + 中文翻译），打包 916KB |
| P3 | 发现页还原（原版结构） | ✅ 主体完成 | 搜索栏 / 等级筛选 / 文章卡片（原版 `item_tab_article` 规格） |
| P4 | 底部 Tab 还原 | ✅ 完成 | `ic_home_*` def/sel 双态图标；品牌粉采样校准 #E0325C |
| P5 | 媒体 mock 基建 | ✅ 完成 | 常驻服务 / adb reverse / 网络安全配置 / 一键脚本 |
| P6 | 阅读器书架页（原版还原） | ✅ 完成 | 9 本书真实封面 + 全部/我的/官方 tabs + 上传卡位；数据来自 reader 接口 |
| P6b | 书籍正文阅读（原版路径） | ✅ 首版 | epub 1:1 解包 + 本机 HTTP 服务 + `loadDataWithBaseURL`；真机与原版同页逐行一致；遗留对齐项（析标记/全书页码/目录抽屉/设置菜单）见 `content/engine/INTEGRATION.md` |
| P6c | 阅读详情 + 播放器 | 🟡 主体完成 | **原版 H5** + 原生外壳；SRT 跟随；词卡不关弹窗；**更多菜单**（精讲/收藏/译文/字号）；**选择题投票**；**迷你条续播与封面**；朗读＝离线声优（七海/恵太/葵/直樹，mock mp3）。遗留：长按句子收藏的 JS 桥、例句里点词、iOS 录音 |
| P7 | 发现页（专栏推荐）高仿 | ✅ 完成 | 专栏推荐段 + 分类条 + **考试等级条**（N5~N4 / N3~N2 / N1 / N1以上，按 `levelTags` 过滤）。数据=`tools/fetch_find_assets.py` |
| P7b | 发现页数据补全 | ✅ 完成 | 首页正文 101 篇（去重后另拉 94 篇）+ 20 条新闻（`find/news/{id}/`：detail / body.html / body-source）+ `tools/fetch_moji_find_articles.py` + `oss/` 本地镜像（非软链）；`MOJI_*` 写入 `.env`，接口文档补「发现」两节 |
| P8 | 词库接入 + 词卡 | 🟡 主体完成 | 运行时查词走 `wordbook.db`（SQLDelight `WordDict`，二进制在 GitHub Release，不进 git）。`dictionary.db` / Realm 只作逆向源，打进 `dict-source.zip`。原版 WordDetail H5 未接 |
| P9a | 学习 Tab | ✅ 主体完成 | 按 `StudyFragment` 还原：「我的学习」栏 + 统计卡 + 计划/订阅两页；计划卡 → 章节弹层 → 详情页；**计划列表**从「全部计划 19 >」进入；**加入计划**（弹层按钮，写本地库）+ **播放进度持久化**（SQLDelight `moji.db`：加入状态/文章毫秒进度，重启后续播、学习页按本地进度展示）；订阅空态 + 推荐专栏；播放队列可切歌。数据：`readStats-articleMonthlyStats` + `readPlan/list|mine` + 专栏列表，`tools/build_study_assets.py` 打包 |
| P9b | 我的 Tab | 🟡 主体完成 | 设置（主题 / 常亮 / 日语声优 / 语速）、收藏与阅读历史写 `moji.db`。阅读库专栏页仍待做（接口缓存在 `reader/`） |
| P10 | AI 能力 | ⏳ 待办 | 千问（长难句分析 / 核心词汇 / 摘要 / TTS）；`QWEN_*` 已在 `.env` 链路里 |

## 高仿工作流（每个模块开工前先做这五步）

> 依据 `AGENTS.md` 的四条纪律与两次事故复盘：**先采事实再下结论**；逆向资产挖三层
> （静态资产 → 运行时下载资产 → 行为规格）；能复用就不自研；卡住先缩最小样例。

1. **定位接口**——在 `reference/moji-read/base.apk` 里找到这个页面真正调用的 REST /
   Cloud Function（jadx + REA），记下 `name` 与参数，不凭印象。
2. **本地拉取并缓存**——给 `tools/fetch_moji_*.py` 补一段，响应落盘到
   `reference/moji-api/` 的对应子目录（`responses/`、`articles/{id}/`、`reader/`、
   `find/`、`oss/`）。脚本必须可重跑、同一次抓取全部落盘。
3. **同步接口文档**——把接口、参数、字段、文件路径补进
   `reference/moji-api/接口说明.md`，并在 `REVERSE.md` §2 登记一行；
   文档与缓存不一致时以缓存为准，**先改文档再写代码**。
4. **按缓存实现模块**——数据只从第 2 步的缓存来，二选一：
   - 纯数据页（发现页、阅读库、文章精读）：`tools/convert_*.py` /
     `tools/fetch_find_assets.py` 打包进 `composeResources`，离线可用；
   - 要跑协议/JS 的页（阅读引擎、词卡 H5）：本机 HTTP 服务兜底——媒体走
     `tools/start_media_mock.sh`（`oss/` 与线上同构），书籍走 `BookServer`
     （对位原版 NanoHTTPD），app 侧 base URL 指向 `127.0.0.1`。
5. **对照验收 + 收尾**——同一本书 / 同一章 / 同一页，原版与高仿跑同一组操作序列
   （截图 + 录屏 + `[MojiBridge]`/`[MojiGeo]` 日志）逐行比对；然后更新本表与
   README，`./gradlew :composeApp:assembleDebug` 通过后提交。

### 待办模块 ↔ 已缓存的接口

接口明细与落盘路径见 `reference/moji-api/接口说明.md`；这里只看「开工时手上有哪些数据」。

| 模块 | 需要的接口 | 缓存位置 | 消费方式 |
| --- | --- | --- | --- |
| 阅读详情 + 播放器（P6c） | `reading-article-detail`、`fetchTargetSrcUrl_v2`、`fav-listSens`、`article-voice-list`、`reading-histories`、`reading-article-everyDayOne` | `articles/{id}/`、`reader/` | 打包（正文/时间轴）+ 本机 HTTP（mp3） |
| 阅读库 / 专栏（P9） | `bookLibs-hot`、`books-incFetch-v2`、`reading-column-list`、`column-mySubscribed`、`reading-column-directory/-content/-audioList` | `reader/`、`reader/columns/{id}/` | 打包 |
| 发现页剩余段（圈子 / 短文推荐 / 新闻） | `circle-simpleList`、`readingCnt-privateRecmArticles`、`nanews/list`、`GET /v1/nanews/{id}` + OSS zip | `find/`、`find/news/{id}/` | 打包（封面用 `fetch_find_assets.py`） |
| 词卡 / 查词（P8） | 词库（非接口）：`dictionary.db` + `assets/WordDetail` H5 | `content/mojidict/`、`content/h5/WordDetail/` | 本机 HTTP（H5 + Bridge） |
| 学习 / 我的（P9） | **未采**——先按第 1 步定位，再抓缓存 | — | 待定 |
| AI 能力（P10） | 千问 `/compatible-mode/v1`、TTS `/api/v1` | 无缓存（实时调用） | `Secrets`（`.env` 注入） |

## 实施依据

- **原型参考**：`出海/Demo/moji-offline`（功能实现更完整，作为移植参考与数据抓取工作区）；
- **权威规格**：官方 APK（`reference/moji-read/base.apk`）逆向——
  布局尺寸取 `res/layout/*`、图标取 `res/drawable-xxhdpi-v4/`、颜色从图标采样；
- **数据来源**：全部来自 `reference/moji-api/` 的本地缓存（`tools/fetch_moji_*.py`
  可重跑），模块实现不去猜字段、不临时造数据；
- **真机验证**：每阶段在 Sony XQ-AT72（adb）安装验证后再更新本表。

## 每阶段完成标准

1. 构建通过（`./gradlew :composeApp:assembleDebug`）；
2. 真机安装 + **交互对照**（同一组操作序列，不只截图）；
3. 接口文档同步（`reference/moji-api/接口说明.md`；有新增接口时同步 `REVERSE.md` §2）；
4. 更新本表与 README「当前进度」；
5. `git` 提交（消息注明验证方式）。
