# 资产放置：APK / GitHub Release / NAS

大体积与生成物不进 git 历史。仓库只保留代码、脚本、文档与轻量资源。

## 三层分工

| 层 | 放什么 | 位置 |
| --- | --- | --- |
| **APK（打包）** | 文章 HTML/JSON、发现页、H5、书籍解包树、引擎、`wordbook.db` | `composeResources/files/**`（多为生成物） |
| **GitHub Release** | 新克隆必下的大二进制（词书 db、可选逆向包） | tag `assets-v1` |
| **NAS 静态** | 运行时音频/配图（原 `oss/`） | `https://static.ailuo.fun/lang/` |

```
开发机 reference/moji-api/
├── articles/     → build → APK files/article_detail/
├── find/         → build → APK files/find/
├── reader/       → 按需 → APK files/reader/
├── responses/    → 仅开发机
├── words/        → 仅开发机（词接口缓存）
├── wordbook/     → build → APK wordbook.db（JSON 不进 git）
└── oss/          → rsync → NAS …/res/lang/   ★ 唯一公网媒体根
```

## GitHub Release（`assets-v1`）

本地打包目录：`dist/release/`（gitignore）。上传用：

```bash
tools/package_release_assets.sh   # 生成 zip
# 仓库建好后：
gh release create assets-v1 dist/release/*.zip -t assets-v1 -n "offline assets v1"
```

| 文件 | 内容 | 新克隆 |
| --- | --- | --- |
| `wordbook.db.zip` | 查词库 SQLite（~156MB 原文件） | **必下** → `composeResources/files/wordbook.db` |
| `reverse-apk.zip` | 原版 `base.apk` + split | 可选（逆向） |
| `dict-source.zip` | 旧 `dictionary.db` + Realm 源 | 可选（已不用于运行时） |

环境变量 `MOJI_ASSETS_BASE_URL` 可覆盖下载根（默认见 `tools/fetch_assets.sh`）。

```bash
tools/fetch_assets.sh             # 拉 wordbook.db
python3 tools/unpack_moji_books.py
python3 tools/build_article_assets.py
python3 tools/fetch_find_assets.py
./gradlew :composeApp:assembleDebug
```

## NAS（`lang` 媒体）

| 本机 | NAS 宿主机 | 公网 URL |
| --- | --- | --- |
| `reference/moji-api/oss/**` | `/volume1/docker/caddy/res/lang/**` | `https://static.ailuo.fun/lang/**` |

同步（绿联 NAS 禁用任意路径 rsync，脚本用 **tar-over-ssh**）：

```bash
tools/sync_lang_media_to_nas.sh --apply-caddy   # 首次：打补丁 + 全量同步 oss → lang
tools/sync_lang_media_to_nas.sh                 # 之后只同步
```

App 配置（二选一）：

```properties
# 本地 mock（adb reverse）
MOCK_MEDIA_BASE=http://127.0.0.1:18080

# 远程 NAS
MOCK_MEDIA_BASE=https://static.ailuo.fun/lang
```

**不要**把 `words/`、`wordbook/*.json` 挂到公网 `lang`。

## 已从 git 移除（改 Release / 本机）

| 原路径 | 去向 |
| --- | --- |
| `composeApp/.../files/wordbook.db` | Release `wordbook.db.zip`（本机可留） |
| `content/mojidict/dictionary.db` | Release `dict-source.zip` / 废弃运行时 |
| `reference/dict/*.realm` | Release `dict-source.zip` |
| `reference/moji-read/base.apk` 与 split | Release `reverse-apk.zip` |
| `reference/moji-api/oss/**` | NAS `lang/`（始终 gitignore） |

本地副本可放在 `dist/release-staging/`（gitignore），便于重新打 zip。

## APK 内生成物（gitignore，脚本重建）

| 路径 | 脚本 |
| --- | --- |
| `files/article_detail/`、`files/article_h5/` | `build_article_assets.py` |
| `files/find/` | `fetch_find_assets.py` |
| `files/books/` | `unpack_moji_books.py` |
| `files/wordbook.db` | Release 下载，或 `build_word_book.py` |
| `files/study/` | `build_study_assets.py` |

## 历史瘦身

2026-10-18 已用 `git filter-repo` 从全部历史去掉 APK、Realm、`dictionary.db`。当前包约 8MB，历史里没有超过 5MB 的 blob。不要在工作区有未提交改动时再跑 filter-repo。若体积回升，先确认没有别的 remote 带着旧包，再 `git reflog expire --expire=now --all && git gc --prune=now --aggressive`。
