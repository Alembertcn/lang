# 资产外置方案（GitHub Release 维护）

大体积资产不进 git 历史，改由 **GitHub Release** 发布；仓库只保留代码、
脚本、文档与轻量资源。

## 资产分类

| 资产 | 体积 | 处理 |
| --- | --- | --- |
| 代码 / 脚本 / 文档 / 图标 / 阅读引擎 / 文章 JSON | < 3 MB | **进 git** |
| 词库 `content/mojidict/dictionary.db` | 64 MB | **Release（`assets-v1`）** |
| 书籍解包树（`composeResources/files/books/{id}/**`） | ~0.5 MB | **生成物，不入库**，用 `unpack_moji_books.py` 从 epub 源 1:1 解包（原版路径，无转换） |
| 发现页数据/图片（`composeResources/files/find/**`） | ~6 MB | **生成物，不入库**，用 `tools/fetch_find_assets.py` 重建（圈子/文章/计划/专栏封面 + `feed.json`） |
| 文章详情页资源（`composeResources/files/article_h5/**`、`article_detail/**`） | ~22 MB | **生成物，不入库**，用 `tools/build_article_assets.py` 从 `content/h5` + `reference/moji-api` 1:1 重建（原版 H5 + 299 篇 body.html + Noto 字体） |
| 书籍 epub 源（`reference/moji-api/oss/books/**`） | ~1.2 MB | Release 或本地抓取（`fetch_moji_books.py`） |
| 音频 mp3（`reference/moji-api/oss/article/audio/**`） | 468 MB | 保持本地 mock（.gitignore 已有） |
| 文章图片（`reference/moji-api/oss/article/images/**`） | 47 MB | 本地 mock 镜像（`fetch_moji_article_images.py`，.gitignore 已有） |
| 文章源（`reference/moji-api/articles/**`） | 15 MB | 本地抓取（`fetch_moji_articles.py`） |

## 首次发布（仓库建好后，二选一）

```bash
# 方式 A：gh CLI
cd content/mojidict && zip -q ../mojidict-db.zip dictionary.db && cd ../..
gh release create assets-v1 content/mojidict/../mojidict-db.zip \
  reference/moji-api/oss/books -F "离线资产 v1：词库 + 书籍 epub 源"

# 方式 B：GitHub 网页 → Releases → Draft a new release → tag `assets-v1` → 上传 zip
```

## 新克隆的启动流程

```bash
tools/fetch_assets.sh                 # 1) 下载 Release 资产（词库 / epub 源）
python3 tools/fetch_moji_books.py     # 2)（可选）抓取 epub 源到 reference/（若 Release 已含可跳过）
python3 tools/unpack_moji_books.py    # 3) 1:1 解包 epub → composeResources/files/books/（生成物）
python3 tools/build_article_assets.py # 4) 原版文章 H5 + 正文资源（生成物）
./gradlew :composeApp:assembleDebug   # 5) 构建
```

## 说明

- `tools/fetch_assets.sh` 从 `https://github.com/<owner>/<repo>/releases/download/assets-v1/`
  下载并解包；owner/repo 可用 `MOJI_ASSETS_BASE_URL` 覆盖（默认占位）。
- 本地已有资产的开发者可跳过第 1 步。
- 书籍走**原版路径**：解包树只由 `unpack_moji_books.py` 生成，禁止再引入任何
  HTML/CSS 转换步骤（见 `content/engine/INTEGRATION.md`）。
- 历史提交中的大文件如需彻底清理，可用 `git filter-repo`（见文末备忘），
  操作前先备份；本仓库当前不自动重写历史。

```bash
# 备忘：彻底瘦身（可选，重写历史，操作前备份）
# pip install git-filter-repo
# git filter-repo --path content/mojidict/dictionary.db --path composeApp/src/commonMain/composeResources/files/books --invert-paths
```
