package com.moji.offline.data.word

import com.moji.offline.data.wordbook.openWordBookDriver
import com.moji.offline.wordbook.WordBookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * One token in an example's `notationTitle`. [lemma] is what the original
 * `callWordAlert` sends (rt@lemma, else surface).
 */
data class WordToken(val surface: String, val lemma: String) {
    val lookupKey: String get() = lemma.ifBlank { surface }
}

/**
 * One example under a sense. [id] is the Japanese example id
 * (`readObject.exampleID` / `Example.getPk()`), played as tarType 103.
 * [tokens] come from notationTitle so each word can open the word card.
 */
data class WordExample(
    val ja: String,
    val zh: String,
    val id: String = "",
    val tokens: List<WordToken> = emptyList(),
)

/** One sense on the original word-detail page: Chinese gloss, Japanese gloss, examples. */
data class WordSense(
    val zh: String,
    val ja: String,
    val examples: List<WordExample>,
)

/** One word inside an 关联词 row. [extra] is the reading, or the Chinese gloss for 话题词. */
data class RelatedWord(
    val spell: String,
    val extra: String,
    val id: String,
)

/**
 * One 关联词 group. [kind] matches the original H5 field order:
 * i 自动词, t 他动词, a 同词位, p 多音词, y 近义词, n 反义词, s 话题词.
 */
data class RelatedGroup(
    val kind: String,
    val words: List<RelatedWord>,
)

data class WordEntry(
    val id: String,
    val spell: String,
    val pron: String,
    val accent: String,
    val romaji: String,
    val excerpt: String,
    val definitionJa: String,
) {
    /** Part of speech tag stripped from the excerpt prefix, e.g. `[名]` -> 名. */
    val pos: String get() = POS_TAG.find(excerpt)?.groupValues?.get(1).orEmpty()

    /** Chinese gloss without the leading part-of-speech tag. */
    val meaningZh: String get() = POS_TAG.replaceFirst(excerpt, "").trim()

    /** Kana reading when the mock mp3 is missing, so 海 is spoken as わたつみ. */
    fun spokenOr(fallback: String): String = pron.ifBlank { fallback }

    private companion object {
        val POS_TAG = Regex("^\\s*\\[([^\\]]+)\\]")
    }
}

/**
 * Offline word book. The original detail call returns one word, including its
 * `relatedWord`. This looks up that one row in the local database.
 */
object WordDict {

    private val mutex = Mutex()
    private var book: WordBookDatabase? = null

    /** Copy the bundled database into the app sandbox before the first lookup. */
    suspend fun prepare() {
        withBook { }
    }

    /**
     * 对位原版 `clickContent`：只收到词面。在线先请求 `v1/word/clickDetail`，
     * 失败才走 `searchFromDB`。本地对汉字是 `Wort.spell` 精确匹配，Realm
     * `findAll` 的第一条得分最高。本库同形词的第一条就是离线词库那条（母 → はは）。
     */
    suspend fun find(vararg keys: String): WordEntry? {
        val spellKeys = keys.filter { it.isNotBlank() }
        return withBook { db ->
            spellKeys.firstNotNullOfOrNull { key ->
                db.wordBookQueries.selectBySpell(key).executeAsOneOrNull()?.toEntry()
            }
        }
    }

    /** Headword by the original object id, so 关联词 can open that exact record. */
    suspend fun findById(id: String): WordEntry? {
        if (id.isBlank()) return null
        return withBook { db ->
            db.wordBookQueries.selectById(id).executeAsOneOrNull()?.toEntry()
        }
    }

    /** 关联词 for this headword. Empty when this word's record has none. */
    suspend fun related(id: String): List<RelatedGroup> {
        if (id.isBlank()) return emptyList()
        return withBook { db ->
            db.wordBookQueries.selectRelated(id).executeAsList().mapNotNull { row ->
                val words = parseRelated(row.words_json)
                if (row.kind.isEmpty() || words.isEmpty()) null else RelatedGroup(row.kind, words)
            }
        }
    }

    /** Senses and examples for the original detail page, keyed by wort id. */
    suspend fun detail(id: String): List<WordSense> {
        if (id.isBlank()) return emptyList()
        return withBook { db ->
            db.wordBookQueries.selectSenses(id).executeAsList().map { row ->
                WordSense(
                    zh = row.zh,
                    ja = row.ja,
                    examples = parseExamples(row.examples_json),
                )
            }
        }
    }

    private suspend fun <T> withBook(block: (WordBookDatabase) -> T): T = withContext(Dispatchers.Default) {
        val db = mutex.withLock {
            book ?: WordBookDatabase(openWordBookDriver()).also { book = it }
        }
        block(db)
    }
}

private fun com.moji.offline.wordbook.Headword.toEntry() = WordEntry(
    id = id,
    spell = spell,
    pron = pron,
    accent = accent,
    romaji = romaji,
    excerpt = excerpt,
    definitionJa = definition_ja,
)

private fun parseExamples(raw: String): List<WordExample> {
    val arr = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
    return arr.map { example ->
        val pair = example.jsonArray
        WordExample(
            ja = pair.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty(),
            zh = pair.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty(),
            id = pair.getOrNull(2)?.jsonPrimitive?.contentOrNull.orEmpty(),
            tokens = parseTokens(pair.getOrNull(3)),
        )
    }.filter { it.ja.isNotBlank() || it.zh.isNotBlank() }
}

private fun parseRelated(raw: String): List<RelatedWord> {
    val arr = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
    return arr.mapNotNull { item ->
        val fields = item.jsonArray
        val spell = fields.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty()
        if (spell.isEmpty()) return@mapNotNull null
        RelatedWord(
            spell = spell,
            extra = fields.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty(),
            id = fields.getOrNull(2)?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}

/** Example field 3: `"年"` or `["休み","休む"]`. */
private fun parseTokens(raw: kotlinx.serialization.json.JsonElement?): List<WordToken> {
    val arr = raw as? JsonArray ?: return emptyList()
    return arr.mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> {
                val surface = item.contentOrNull.orEmpty()
                if (surface.isEmpty()) null else WordToken(surface, surface)
            }
            is JsonArray -> {
                val surface = item.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty()
                val lemma = item.getOrNull(1)?.jsonPrimitive?.contentOrNull
                    .orEmpty().ifBlank { surface }
                if (surface.isEmpty()) null else WordToken(surface, lemma)
            }
            else -> null
        }
    }
}
