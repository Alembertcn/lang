package com.moji.offline.data.audio

import com.moji.offline.config.Secrets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Word pronunciation through Qwen TTS (DashScope), approved by the user on
 * 2026-10-17 as the AI replacement for the phone's missing Japanese TTS voice.
 *
 * Order of use in the UI: this service first (real Japanese audio), then the
 * device TTS, then a "缺少日语语音" hint. Results are memoised per word for the
 * session; the signed URL is valid for ~30 days.
 */
object QwenWordAudio {

    private const val LANGUAGE = "Japanese"

    private val json = Json { ignoreUnknownKeys = true }
    private val player = createAudioPlayer()
    private val urls = mutableMapOf<String, String>()

    val available: Boolean get() = Secrets.QWEN_API_KEY.isNotBlank()

    suspend fun speak(text: String): Boolean {
        val word = text.trim()
        if (word.isEmpty() || !available) return false
        // DashScope hands back an http:// OSS link; the app only allows
        // cleartext for the loopback mock, so play the https variant.
        val url = (urls[word] ?: fetch(word)?.also { urls[word] = it } ?: return false)
            .replaceFirst("http://", "https://")
        val duration = runCatching { player.load(url) }.onFailure {
        }.getOrNull() ?: return false
        if (duration <= 0) return false
        player.play()
        return true
    }

    fun stop() {
        runCatching { player.pause() }
    }

    private suspend fun fetch(text: String): String? = runCatching {
        val body = buildJsonObject {
            put("model", Secrets.QWEN_TTS_MODEL.ifBlank { "qwen3-tts-flash" })
            putJsonObject("input") {
                put("text", text)
                put("voice", Secrets.QWEN_TTS_VOICE.ifBlank { "Cherry" })
                put("language_type", LANGUAGE)
            }
        }
        val raw = postJson(
            url = "${Secrets.QWEN_TTS_BASE_URL.trimEnd('/')}/services/aigc/multimodal-generation/generation",
            headers = mapOf(
                "Authorization" to "Bearer ${Secrets.QWEN_API_KEY}",
                "Content-Type" to "application/json",
            ),
            body = body.toString(),
        ) ?: return null
        val payload = json.parseToJsonElement(raw).jsonObject
        payload["output"]?.jsonObject?.get("audio")?.jsonObject?.get("url")?.jsonPrimitive?.content
    }.onFailure { println("MojiWordAudio: fetch failed $it") }.getOrNull()
}

/**
 * Plain HTTPS POST. Android uses HttpURLConnection (system trust store +
 * network security config); iOS uses Ktor Darwin. Ktor CIO on Android fails
 * hostname verification under a domain-specific network security config.
 */
expect suspend fun postJson(url: String, headers: Map<String, String>, body: String): String?
