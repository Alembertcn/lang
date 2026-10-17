# moji-offline

MOJi阅读（`com.mojidict.read` v3.4.9）的离线高仿：**Kotlin Multiplatform +
Compose Multiplatform** 双端（Android / iOS），定位「离线优先 + 按需联网」，
UI 与资源按逆向原版还原。应用包名 `com.moji.offline`。

## 当前进度

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 逆向资产 | ✅ | 词库（44 万词条）、H5 页面、原版图标、接口数据（19 计划 / 299 文章 / 20 新闻 / 178 音频 / 9 本书） |
| KMP 工程 | ✅ | Kotlin 2.4.21 + CMP 1.11.1 + SQLDelight + Ktor；真机（XQ-AT72）跑通 |
| 数据管线 | ✅ | 抓取 → 转换 → 打包（170 篇真实文章：SRT 时间轴 + 原文 + 中文翻译） |
| 发现页 | ✅ 高仿完成 | 按确认范围只保留原版「专栏推荐」：吸顶发现栏 + 分类条（6 tab）+ 专栏卡列表；数据=本地 find 抓取，图片打包见 `tools/fetch_find_assets.py` |
| 底部 Tab | ✅ | 原版 `ic_home_*` def/sel 双态图标、品牌粉 #E0325C、10sp 文字 |
| 阅读器书架 | ✅ | 原版结构：全部/我的/官方 tabs + 3 列书架网格 + 9 本书真实 OSS 封面 |
| 学习 Tab | ✅ 主体 | 「我的学习」+ 统计 + 计划/订阅；计划目录（19 个）→ 加入计划 → 章节 → 详情；订阅空态 + 推荐专栏。加入状态与播放进度存本地 SQLite（SQLDelight `moji.db`），重启后按上次进度展示/续播 |
| 书籍正文阅读 | ✅ 首版 | **原版路径**：epub 1:1 解包 + 本机 HTTP 书服（Ktor CIO）+ `loadDataWithBaseURL` 直载原版 XHTML；引擎分页 + 原版翻页同步公式；真机对照通过 |
| 媒体 mock | ✅ 基建 | 常驻服务 + adb reverse + 网络安全配置 + 一键脚本；播放器代码待同步 |
| 短文详情页 | ✅ 主体完成 | **原版 H5 路径** + 原生外壳：44dp 顶栏、56dp 吸顶工具条（下滚隐藏）、播放条、SRT 高亮/定位、单词/句子菜单（朗读/复制/查词/AI/收藏）、精听跟读（录音+回放）、播放设置（循环/倍速）、播放队列（计划/专栏文章，可切歌、自动下一首）；退出后底栏上方 64dp 全局面板。资源由 `tools/build_article_assets.py` 生成 |
| 发现页剩余视觉 | ⏳ | 顶部粉色背景、阅读时长/等级信息栏、圈子入口、大卡片 |
| 词库查词 | ⏳ | SQLDelight 接入 `dictionary.db` + 词卡 H5 |
| AI 能力 | ⏳ | 千问：长难句分析 / 核心词汇 / AI 摘要 / TTS 发音 |

阶段表与完成标准见 [PLAN.md](PLAN.md)。

## 快速开始

```bash
# 环境：JDK 17 + Android SDK（compileSdk 36）
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk

# 媒体 mock（听音频需要）：常驻服务 + USB 端口转发
tools/start_media_mock.sh
```

## 目录结构

```text
moji-offline/
├── composeApp/
│   └── src/commonMain/
│       ├── kotlin/com/moji/offline/        # 源码（feature/discover 等）
│       └── composeResources/
│           ├── drawable/                   # 原版图标（ic_find_* / ic_home_*，提取自 APK）
│           └── files/articles/             # 170 篇真实文章 JSON + index（打包进 app）
├── content/                                # 一次性逆向资产（git 管理）
│   ├── mojidict/dictionary.db              # 词库（95,412 词条 / 44.3 万行 / 4 表）
│   └── h5/                                 # H5 页面资产（词卡 / 文章渲染复用）
├── reference/
│   ├── moji-read/                          # 官方 APK（base + split）与参考素材
│   ├── dict/                               # 从 APK 解出的 Realm 词库源
│   └── moji-api/                           # 接口数据工作区（见下）
├── tools/                                  # 工具链（见下）
├── PLAN.md                                 # 计划与进度
├── REVERSE.md                              # 逆向还原 / 页面仿造 / 接口总览
└── env.example                             # 本地配置模板
```

### reference/moji-api（接口数据工作区）

高仿的**数据来源**：每个接口的响应都在这里落盘，模块实现直接读缓存（对照表见
`reference/moji-api/接口说明.md` 文末「本地缓存 ↔ 线上地址」）。

| 路径 | 内容 |
| --- | --- |
| `接口说明.md` | 计划 / 文章 / 录音 / 阅读器 / 发现接口的逆向说明（抓取脚本的规格书） |
| `responses/` | 计划接口原始响应（readPlan list / mine / detail） |
| `articles/{objectId}/` | 每篇文章：`detail.json` + `body.html`（含 SRT 时间轴 / 注音 / 翻译） |
| `reader/` | 阅读器 / 书架 / 专栏响应（`books-incFetch-v2`、`columns/{id}/*` 等） |
| `find/` | 发现页响应（推荐文章/专栏、圈子、分类、新闻列表）+ `find/news/{id}/` 新闻正文 |
| `oss/` | mock 静态服务根，与线上同构（`article/audio/*.mp3` 178 段、`books/*` 9 本）；**不入库** |
| `audio-index.json` | 文章 → 音频文件映射（含真实时长） |
| `catalog.txt` | 计划与文章标题总览 |

### tools（工具链）

| 工具 | 用途 |
| --- | --- |
| `fetch_moji_plans.py` | 登录并拉取计划目录（需 `.env` 配置 MOJI 账号） |
| `fetch_moji_articles.py` | 拉取全部文章正文（detail + body.html） |
| `fetch_moji_find.py` | 拉取发现页响应（推荐 / 圈子 / 分类 / 新闻列表 / 打卡） |
| `fetch_moji_find_articles.py` | 拉取发现页首页正文（94 篇）+ 20 条新闻正文（OSS 注音 zip） |
| `fetch_moji_audio.py` | 下载文章录音到 mock OSS 树，生成 `audio-index.json` |
| `fetch_moji_article_images.py` | 把文章封面与正文插图镜像到 mock OSS 树（771 张，约 47 MB），供运行时改写使用 |
| `fetch_moji_covers.py` / `fetch_moji_reader.py` | 书架 / 封面素材 |
| `fetch_moji_books.py` / `unpack_moji_books.py` | 下载书籍 epub 源 / 1:1 解包到 `composeResources/files/books/` |
| `fetch_find_assets.py` | 把发现页数据 + 图片打包成 `composeResources/files/find/`（生成物） |
| `build_article_assets.py` | 原版文章 H5 + 299 篇正文（1:1）+ 字体打包成 `files/article_h5/`、`files/article_detail/`（生成物） |
| `fetch_assets.sh` | 拉取 Release 外置资产（词库 / epub 源），见 `ASSETS.md` |
| `convert_moji_articles.py` | `body.html` → 打包 JSON（SRT 时间轴 + 原文 + 翻译） |
| `start_media_mock.sh` | 启动常驻媒体 mock（screen）+ adb reverse 端口转发 |
| `mojidict-export/` | 词库逆向与导出（zip 密码 / Realm key / 表结构，见其 README） |

## 数据管线（新增文章 / 录音的标准流程）

```bash
# 1) 抓取（.env 配好 MOJI_ACCOUNT / MOJI_PASSWORD）
python3 tools/fetch_moji_plans.py             # 计划目录
python3 tools/fetch_moji_articles.py          # 文章正文
python3 tools/fetch_moji_audio.py             # 录音 → mock OSS 树
python3 tools/fetch_moji_find.py              # 发现页响应
python3 tools/fetch_moji_find_articles.py     # 发现页正文 + 新闻正文

# 2) 转换（生成打包数据）
python3 tools/convert_moji_articles.py # → composeApp/.../files/articles/
python3 tools/fetch_find_assets.py     # → composeApp/.../files/find/

# 3) 重建
./gradlew :composeApp:assembleDebug
```

新增模块的固定动作（定位接口 → 抓取落盘 → 同步接口文档 → 按缓存实现 → 同一页对照）
见 [PLAN.md](PLAN.md)「高仿工作流」。

数据模型对齐原版：**一篇文章 = 一条连续音频 + 逐句 SRT 时间轴**，
播放器按时间轴做句子跟随高亮（与 MOJi 的 SRT 模型一致）。

## 媒体 mock（音频为什么不打包）

完整音频集约 468 MB，不适合进安装包；开发 / 演示由本地 mock 提供
（路径结构与线上 `oss.mojidict.com` 一致）：

```bash
tools/start_media_mock.sh
# 电脑：0.0.0.0:8765 静态服务 reference/moji-api/oss
# 手机：127.0.0.1:18080 → 电脑 8765（adb reverse，走 USB，不受 Wi-Fi / VPN 影响）
```

- app 侧读取 `local.properties` 的 `MOCK_MEDIA_BASE`（构建时注入；
  例：`MOCK_MEDIA_BASE=http://127.0.0.1:18080`）；
- targetSdk 35 默认禁明文 HTTP，已用
  `composeApp/src/androidMain/res/xml/network_security_config.xml`
  仅放行 `127.0.0.1 / localhost / 10.0.2.2 / 局域网地址`；
- mock 服务用 `screen` 常驻：`screen -r moji-mock` 查看日志，
  `screen -S moji-mock -X quit` 停止。

## 本地配置（.env）

```bash
cp env.example .env   # 按需填写
```

| 变量 | 说明 |
| --- | --- |
| `QWEN_*` | 千问（DashScope）AI 能力：分析 / 摘要 / TTS（字段见 env.example） |
| `MOJI_ACCOUNT` / `MOJI_PASSWORD` / `MOJI_COUNTRY_CODE` | 仅重新抓取接口数据时需要 |

构建时 gradle 读取 `.env` 并生成 `com.moji.offline.config.Secrets`。

## 技术栈与版本锁定

Kotlin 2.4.21 · Compose Multiplatform 1.11.1 · AGP 8.13.2 · SQLDelight 2.4.1 ·
Ktor 3.6.0（CIO 引擎）

> 已知约束：CMP 1.12 需要 compileSdk 37 与 AGP 9.1+（本机 SDK 最高 36）；
> CMP 1.11 已停止发布 iosX64 构建，iOS 目标为 iosArm64 / iosSimulatorArm64。

## 说明与边界

- 仅用于个人学习与研究；词库 / H5 / 音频等资源版权归 MoJi 及其权利人所有，
  不用于商用分发；
- UI 规格与图标均提取自官方 APK（`item_tab_article`、`ic_home_*` 等）用于对照还原；
- 原型参考项目：`出海/Demo/moji-offline`（移植来源与数据抓取工作区）。
