package com.moji.offline.data.reader

import com.moji.offline.resources.Res

/** One spine item of the epub, exactly as authored (no rewriting). */
data class ReaderChapter(
    val index: Int,
    val title: String,
    /** Path inside the unpacked epub, e.g. `OEBPS/text00001.html`. */
    val resourcePath: String,
    /** Directory of the chapter inside the epub (trailing slash kept), e.g. `OEBPS/`. */
    val dir: String,
)

data class BookContent(
    val bookId: String,
    val chapters: List<ReaderChapter>,
)

/**
 * Reads the spine straight out of the unpacked epub (container.xml → OPF →
 * manifest/spine) and chapter titles from the NCX. This mirrors what the
 * original `ea.c` server does when it builds its `/(.*)` route table from the
 * publication container; we parse per book open instead of caching a manifest.
 */
object BookPackage {

    private const val BOOK_PREFIX = "files/books/"

    private val itemRe = Regex("<item\\b([^>]*?)/?>", RegexOption.IGNORE_CASE)
    private val itemrefRe = Regex("<itemref\\b([^>]*?)/?>", RegexOption.IGNORE_CASE)
    private val navPointRe = Regex(
        "<navPoint\\b.*?</navPoint>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val labelRe = Regex("<text>([^<]*)</text>", RegexOption.IGNORE_CASE)
    private val srcRe = Regex("<content\\s+src=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val attrRe = Regex("([\\w:.-]+)=\"([^\"]*)\"")

    suspend fun load(bookId: String): BookContent? {
        val container = readText("$BOOK_PREFIX$bookId/META-INF/container.xml") ?: return null
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1) ?: return null
        val opf = readText("$BOOK_PREFIX$bookId/$opfPath") ?: return null
        val opfDir = opfPath.substringBeforeLast('/', "")

        val hrefById = LinkedHashMap<String, String>()
        val mediaTypeById = HashMap<String, String>()
        itemRe.findAll(opf).forEach { match ->
            val attrs = attrs(match.groupValues[1])
            val id = attrs["id"] ?: return@forEach
            hrefById[id] = attrs["href"].orEmpty()
            mediaTypeById[id] = attrs["media-type"].orEmpty()
        }

        val spine = itemrefRe.findAll(opf)
            .mapNotNull { attrs(it.groupValues[1])["idref"] }
            .toList()

        val ncxHref = hrefById.entries.firstOrNull { (id, _) ->
            mediaTypeById[id].orEmpty().contains("dtbncx", ignoreCase = true)
        }?.value
        val titles = ncxHref
            ?.let { readText("$BOOK_PREFIX$bookId/" + joinPath(opfDir, it)) }
            ?.let(::parseTocTitles)
            .orEmpty()

        val chapters = spine.mapNotNull { id ->
            val href = hrefById[id]?.substringBefore('#') ?: return@mapNotNull null
            if (!href.endsWith(".html", true) && !href.endsWith(".xhtml", true)) return@mapNotNull null
            val path = joinPath(opfDir, href)
            val dir = path.substringBeforeLast('/', "") .let { if (it.isEmpty()) "" else "$it/" }
            ReaderChapter(
                index = 0,
                title = titles[href.substringAfterLast('/')] ?: "",
                resourcePath = path,
                dir = dir,
            )
        }.mapIndexed { index, chapter ->
            chapter.copy(
                index = index,
                title = chapter.title.ifBlank { "正文 ${index + 1}" },
            )
        }

        if (chapters.isEmpty()) return null
        return BookContent(bookId, chapters)
    }

    private fun parseTocTitles(ncx: String): Map<String, String> {
        val titles = HashMap<String, String>()
        navPointRe.findAll(ncx).forEach { nav ->
            val block = nav.value
            val label = labelRe.find(block)?.groupValues?.get(1) ?: return@forEach
            val src = srcRe.find(block)?.groupValues?.get(1) ?: return@forEach
            titles[src.substringBefore('#').substringAfterLast('/')] = unescape(label).trim()
        }
        return titles
    }

    private fun attrs(raw: String): Map<String, String> =
        attrRe.findAll(raw).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    private fun joinPath(dir: String, href: String): String =
        if (dir.isEmpty()) href else "$dir/$href"

    private fun unescape(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    private suspend fun readText(path: String): String? =
        runCatching { Res.readBytes(path).decodeToString() }.getOrNull()
}
