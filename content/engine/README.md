# MOJi 原版阅读引擎（epuReader）

原版 APK **不打包**阅读引擎：首次进入阅读器时从 CDN 下载 zip 解包到应用缓存
（`epubReader/epubReader/Bridge.js`，见反编译 `C4/C0198t`）。

## 还原路径（可复现）

1. 代码里找到 CDN 基址：`https://libs-biz.mojidict.com/h5hybrid/epuReader/`
   （以及 debug 域 `webh5-debugten.mojidict.com`）；
2. 版本配置：`https://libs-biz.mojidict.com/h5hybrid/static/profile_android.json`
   → `epuReader.version[0].webV = "v1_3_3"`；
3. 下载：`https://libs-biz.mojidict.com/h5hybrid/epuReader/v1_3_3.zip`（211 KB）。

## 内容

| 文件 | 作用 |
| --- | --- |
| `Bridge.js` | 原生 ↔ Web 协议：初始化 `Main(info, platform, lastCfi, sentences)`、主题/字号、**翻页与页数回传**（`setCurrentInnerPage` / `setHorizontalPageCount`）、点击查词（`clickMOJiArticleContent`）、句子选择、段落分析 |
| `readium-cfi.umd.js` | Readium EPUB CFI 定位标准实现（阅读进度、精确跳转） |
| `rangy-*` / `rangefix` / `web-highlighter` | 文本选择与高亮（选中词/句的基础） |
| `collect-selection.js` | 选择收集（查词取词） |
| `index.css` | 阅读排版样式 |
| `jquery` / `jsface` | 引擎依赖 |

## 为什么重要

复用它 = 翻页、分页、CFI 定位、选中高亮、点词回调**全部是原版逻辑**，
不再需要在自研渲染里逐项模仿。App 侧只需实现 Bridge 期望的原生接口。

> 仅用于个人学习与研究，版权归 MoJi / Hugecore 所有。
