# moji-offline

MOJi阅读（`com.mojidict.read` v3.4.9）的离线高仿项目，目标技术栈
**Kotlin Multiplatform + Compose Multiplatform**（iOS / Android 双端），
定位「离线优先 + 按需联网」。

## 当前进度

### 已完成：逆向与数据资产（2026-10-16）

1. **词库提取**：从 `base.apk` 的 `assets/Database/zh-CN_ja/core-mojiread-sc-20241009.zip`
   逆向出 zip 密码与 Realm 结构，导出为 SQLite：
   - `content/mojidict/dictionary.db`（64 MB，4 张表）：

     | 表 | 行数 | 说明 |
     | --- | --- | --- |
     | wort | 95,412 | 词条：spell 词形 / pron 读音 / accent 声调 / excerpt 释义 |
     | details | 96,290 | 释义分组：part_of_speech 词性 / jita 自他 / katuyou 活用 |
     | subdetails | 155,297 | 逐条释义：title 释义文本 / context 语境 / conjunctions 接续 |
     | example | 96,688 | 例句：title 原文 / src 出处 / notation_title 注音 |

   - 提取工具与完整逆向记录见 `tools/mojidict-export/`。

2. **H5 资产提取**：`content/h5/` 共 73 个文件（1.7 MB）
   - `web/mojiread/`：12 个内容页（article、articleText、sentenceAna、
     question、aiSummary、note 等），词卡与文章渲染复用
   - `WordDetail/`：词卡 H5 的 JSON schema 与示例（word / example 两类页面）
   - `h5hybrid/`：H5 页面依赖的 JS/CSS/图片（Vue、jquery、highlighter 等）
   - `anaRead/`：辅助阅读的示例数据（分词格式 + 重点词汇格式）

3. **逆向证据**：APK 清单、组件表、路由表等分析见 `reference/`（含原始
   APK 与两套 split）。

### 待办（按开发顺序）

1. KMP + Compose Multiplatform 工程骨架、SQLDelight 数据层接入
   `dictionary.db`（预置库方案）
2. 底部四 Tab（发现 / 学习 / 阅读库 / 我的）+ 阅读器 + 词卡 H5 垂直切片
3. 千问 API 接入（长难句分析 / 核心词汇 / AI 摘要 / TTS 发音），
   `env.example` 定义 `QWEN_API_KEY` / `QWEN_MODEL` / `QWEN_TTS_VOICE`
4. 学习统计、收藏、历史、计划、打卡（纯本地 SQLDelight）
5. 内容更新通道（GitHub Pages 静态 JSON + 本地缓存）

## 目录结构

```text
moji-offline/
├── content/                  # 一次性获取的数据资产（git 管理）
│   ├── mojidict/dictionary.db
│   └── h5/                   # 从 APK 提取的 H5 资源
├── reference/                # 逆向输入（APK、split、解出的 realm）
├── tools/mojidict-export/    # 词库导出工具（含逆向记录）
└── README.md
```

## 说明与边界

- 本项目仅用于个人学习与研究，资源不用于商用分发。
- 词库与 H5 资源逆向自官方 APK，版权归 MoJi 及其权利人所有。
