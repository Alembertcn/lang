package com.moji.offline.data.audio

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText

actual suspend fun postJson(url: String, headers: Map<String, String>, body: String): String? =
    runCatching {
        val client = HttpClient()
        try {
            client.post(url) {
                headers.forEach { (key, value) -> header(key, value) }
                setBody(body)
            }.bodyAsText()
        } finally {
            client.close()
        }
    }.onFailure { println("postJson failed: $it") }.getOrNull()
