# mojidict-export

把 MOJi 离线词库（`core-mojiread-sc-20241009.realm`）导出为 SQLite。

## 数据来源（逆向自 base.apk）

- 包内路径：`assets/Database/zh-CN_ja/core-mojiread-sc-20241009.zip`
- zip 密码由 `A5/g.java` 生成：源串 `WM8\LG\XxzNq|` + U+0080 + `+`，
  逐字符 -5，第 3 个字符再额外 -7 → **`RH,WGBWSsuIlw{&`**
- 解出的 realm 文件本身**未加密**（app 运行时会用 Base64 key 写成
  `encrypt.realm`；导出不需要它，但 key 记录如下供参考）

### Realm 加密 key（`O3/k.java`，用于 app 运行时生成的 encrypt.realm）

源串 `Cri03vZ9+YUvqZgMxK/UkhojeJRnrSwYn/Bz4s/P2jJZ47Ut+dGTlgZtuDrn/csJlEcy4YaTZlpnSBbPsWej+A==`
第 8 个字符（下标 7）`9` → `3`，Base64 解码得 64 字节 key：

```
0ab8b4def677f9852fa9980cc4afd4921a23789467ad2c189ff073e2cfcfda32
59e3b52df9d19396066db83ae7fdcb09944732e18693665a674816cfb167a3f8
```

### Realm schema

文件内 schemaVersion = 11；4 个类均以 `objectId` 为主键，字段全部 optional，
`objectId` / `wordId` / `subdetailsId`（及 `detailsId`）带索引。

## 安装依赖（重要：realm 的坑）

1. 用 **Node 18**（realm 12 的原生模块在新版 Node 上会卡死加载）。
2. `npm install`
3. realm 12.15.0 的 npm 包不带 Node 预编译二进制，需要手动补：

```bash
cd node_modules/realm
mkdir -p prebuilds/node
curl -L -o /tmp/realm12.tar.gz \
  "https://static.realm.io/realm-js-prebuilds/12.15.0/realm-v12.15.0-napi-v6-darwin-arm64.tar.gz"
tar -xzf /tmp/realm12.tar.gz -C prebuilds/node/
mv prebuilds/node/prebuilds/node/realm.node prebuilds/node/realm.node
rmdir prebuilds/node/prebuilds/node prebuilds/node/prebuilds 2>/dev/null || true
```

## 运行

```bash
node export_mojidict.js <input.realm> <output.db>
# 例：
node export_mojidict.js ../../reference/moji-read/core-mojiread-sc-20241009.realm \
  ../../content/mojidict/dictionary.db
```

脚本流程：在副本上探测 schema（含主键与 optional 标记）→ 读取源 schema
版本（当前源码包为 11）→ 用精确 schema 打开工作副本（realm 自动升级文件格式）
→ 全量写入 SQLite。输入文件不会被修改。

## 导出结果

SQLite 四张表（snake_case 重命名，语义不变）：

| 表 | 对应 Realm 类 | 说明 |
| --- | --- | --- |
| `wort` | Wort | 词条：spell 词形 / pron 读音 / accent 声调 / excerpt 释义 / tags 标签 |
| `details` | Details | 释义分组：part_of_speech 词性列表(JSON) / jita 自他 / katuyou 活用 |
| `subdetails` | Subdetails | 逐条释义：title 释义文本 / context 语境 / conjunctions 接续 |
| `example` | Example | 例句：title 原文 / src 出处 / notation_title 注音 |

关系：`details.word_id → wort.object_id`，
`subdetails.details_id → details.object_id`（或 word 级），
`example.subdetails_id → subdetails.object_id`。
