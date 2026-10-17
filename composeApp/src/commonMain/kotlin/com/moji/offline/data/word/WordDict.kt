package com.moji.offline.data.word

import com.moji.offline.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One headword of the bundled dictionary subset (`tools/build_word_dict.py`),
 * i.e. exactly the words the offline article corpus can tap.
 */
data class WordEntry(
    val id: String,
    val spell: String,
    val pron: String,
    val accent: String,
    val excerpt: String,
    val definitionJa: String,
) {
    /** Part of speech tag stripped from the excerpt prefix, e.g. `[名]` -> 名. */
    val pos: String get() = POS_TAG.find(excerpt)?.groupValues?.get(1).orEmpty()

    /** Chinese gloss without the leading part-of-speech tag. */
    val meaningZh: String get() = POS_TAG.replaceFirst(excerpt, "").trim()

    private companion object {
        val POS_TAG = Regex("^\\s*\\[([^\\]]+)\\]")
    }
}

/**
 * Offline word-card dictionary. The original app resolves the tapped word
 * against api.mojidict.com; rule 零之三 keeps that online, so the offline build
 * ships the subset of `content/mojidict/dictionary.db` that the bundled articles
 * actually contain and looks it up here (lemma first, then surface form).
 */
object WordDict {

    private const val ASSET = "files/worddict.json"

    private val mutex = Mutex()
    private var bySpell: Map<String, WordEntry>? = null

    suspend fun find(vararg keys: String): WordEntry? {
        val index = load()
        return keys.firstNotNullOfOrNull { index[it] }
    }

    private suspend fun load(): Map<String, WordEntry> = mutex.withLock {
        bySpell?.let { return it }
        val parsed = runCatching {
            val root = Json.parseToJsonElement(Res.readBytes(ASSET).decodeToString())
            root.jsonObject["words"]?.jsonArray?.mapNotNull { element ->
                val o = element.jsonObject
                fun str(key: String) = o[key]?.jsonPrimitive?.contentOrNull.orEmpty()
                val spell = str("s")
                if (spell.isEmpty()) null else WordEntry(
                    id = str("i"),
                    spell = spell,
                    pron = str("p"),
                    accent = str("a"),
                    excerpt = str("x"),
                    definitionJa = str("j"),
                )
            }?.associateBy { it.spell }.orEmpty()
        }.getOrDefault(emptyMap())
        bySpell = parsed
        parsed
    }
}
