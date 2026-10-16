# moji-offline

MOJi阅读（`com.mojidict.read` v3.4.9）的离线高仿：**Kotlin Multiplatform +
Compose Multiplatform** 双端（Android / iOS），定位「离线优先 + 按需联网」，
UI 与资源按逆向原版还原。应用包名 `com.moji.offline`。

## 当前进度

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 逆向资产 | ✅ | 词库（44 万词条）、H5 页面、原版图标、接口数据（19 计划 / 205 文章 / 178 音频） |
| KMP 工程 | ✅ | Kotlin 2.4.21 + CMP 1.11.1 + SQLDelight + Ktor；真机（XQ-AT72）跑通 |
| 数据管线 | ✅ | 抓取 → 转换 → 打包（170 篇真实文章：SRT 时间轴 + 原文 + 中文翻译） |
| 发现页 | ✅ 主体 | 原版结构：搜索栏 / 等级筛选 / 文章卡片（原版 `item_tab_article` 规格）+ 真实数据 |
| 底部 Tab | ✅ | 原版 `ic_home_*` def/sel 双态图标、品牌粉 #E0325C、10sp 文字 |
| 媒体 mock | ✅ 基建 | 常驻服务 + adb reverse + 网络安全配置 + 一键脚本；播放器代码待同步 |
| 阅读页 + 播放器 | ⏳ | 待从原型项目移植（"下载一次缓存后本地播放"方案已验证） |
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
└── env.example                             # 本地配置模板
```

### reference/moji-api（接口数据工作区）

| 路径 | 内容 |
| --- | --- |
| `接口说明.md` | MOJi 阅读计划 / 文章 / 音频接口的逆向说明（抓取脚本的依据） |
| `responses/` | 计划接口原始响应（readPlan list / mine / detail） |
| `articles/{objectId}/` | 每篇文章：`detail.json` + `body.html`（含 SRT 时间轴 / 注音 / 翻译） |
| `oss/` | mock 静态服务根（`article/audio/*.mp3`）；**软链指向抓取工作区，不入库** |
| `audio-index.json` | 文章 → 音频文件映射（含真实时长） |
| `catalog.txt` | 计划与文章标题总览 |

### tools（工具链）

| 工具 | 用途 |
| --- | --- |
| `fetch_moji_plans.py` | 登录并拉取计划目录（需 `.env` 配置 MOJI 账号） |
| `fetch_moji_articles.py` | 拉取全部文章正文（detail + body.html） |
| `fetch_moji_audio.py` | 下载文章录音到 mock OSS 树，生成 `audio-index.json` |
| `fetch_moji_covers.py` / `fetch_moji_reader.py` | 书架 / 封面素材（可选） |
| `convert_moji_articles.py` | `body.html` → 打包 JSON（SRT 时间轴 + 原文 + 翻译） |
| `start_media_mock.sh` | 启动常驻媒体 mock（screen）+ adb reverse 端口转发 |
| `mojidict-export/` | 词库逆向与导出（zip 密码 / Realm key / 表结构，见其 README） |

## 数据管线（新增文章 / 录音的标准流程）

```bash
# 1) 抓取（.env 配好 MOJI_ACCOUNT / MOJI_PASSWORD）
python3 tools/fetch_moji_plans.py      # 计划目录
python3 tools/fetch_moji_articles.py   # 文章正文
python3 tools/fetch_moji_audio.py      # 录音 → mock OSS 树

# 2) 转换（生成打包数据）
python3 tools/convert_moji_articles.py # → composeApp/.../files/articles/

# 3) 重建
./gradlew :composeApp:assembleDebug
```

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
