package com.moji.offline.data.reader

import com.moji.offline.resources.Res
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.routing.RoutingContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The original reader serves the unpacked epub from a loopback HTTP server
 * (MOJi ships NanoHTTPD; `ea.c` = the book server) and loads each chapter with
 * `loadDataWithBaseURL("http://127.0.0.1:{port}/{bookId}/{chapterDir}", html, "text/html", ...)`.
 *
 * This is the KMP equivalent (`ktor-server-cio`, one implementation for both
 * Android and iOS). Routes, mirroring the original:
 *
 *  - `GET /books/{bookId}/{path...}` → file from the unpacked epub tree;
 *    when the epub itself does not contain the file, the engine asset with the
 *    same file name is served instead (chapters reference `index.css`,
 *    `jquery-3.4.1.min.js`, `Bridge.js`, ... which live in `files/engine/epubReader`).
 *  - `GET /android_asset/web/mojiread/article/android/index.html` and
 *    `GET /android_asset/h5hybrid/assets/{path...}` → the original article H5
 *    bundle (the app loads `file:///android_asset/...` with the same paths;
 *    the H5's absolute `/android_asset/...` module URLs resolve here unchanged).
 *  - `GET /article-cover/{file}` → the cover already bundled for the Discover tab.
 *
 * The epub tree is never rewritten: the server only streams bytes from
 * `composeResources/files/books/{bookId}/...`.
 */
object BookServer {

    private const val BOOK_PREFIX = "files/books/"
    private const val ENGINE_PREFIX = "files/engine/epubReader/"
    private const val ARTICLE_H5_PREFIX = "files/article_h5/"
    private const val FIND_COVERS_PREFIX = "files/find/articles/"

    private val mutex = Mutex()
    private var server: EmbeddedServer<*, *>? = null
    private var boundPort: Int = 0

    /** e.g. `http://127.0.0.1:21001` — valid after [ensureStarted]. */
    val baseUrl: String get() = "http://127.0.0.1:$boundPort"

    val port: Int get() = boundPort

    suspend fun ensureStarted(): Int = mutex.withLock {
        if (server != null) return boundPort

        // Preferred path: let the OS pick a free port (port = 0) and read it back.
        val started = runCatching {
            val s = embeddedServer(CIO, host = "127.0.0.1", port = 0) { installRoutes() }
            s.start(wait = false)
            val p = s.engine.resolvedConnectors().firstOrNull()?.port
                ?: error("no resolved connector")
            s to p
        }.getOrNull()

        if (started != null) {
            server = started.first
            boundPort = started.second
            return boundPort
        }

        // Fallback: scan a private range (the resolved-connectors API is not
        // available on every native engine build).
        for (candidate in 21000..21040) {
            val s = runCatching {
                embeddedServer(CIO, host = "127.0.0.1", port = candidate) { installRoutes() }
            }.getOrNull() ?: continue
            val ok = runCatching { s.start(wait = false) }.isSuccess
            if (ok) {
                server = s
                boundPort = candidate
                return boundPort
            }
            runCatching { s.stop(100, 100) }
        }
        error("BookServer: could not bind a loopback port")
    }

    private fun io.ktor.server.application.Application.installRoutes() {
        routing {
            get("/books/{bookId}/{path...}") {
                serveBookFile()
            }
            get("/android_asset/web/mojiread/article/android/index.html") {
                serveResource("${ARTICLE_H5_PREFIX}index.html")
            }
            get("/android_asset/h5hybrid/assets/{path...}") {
                val relPath = call.parameters.getAll("path").orEmpty().joinToString("/")
                serveResource(ARTICLE_H5_PREFIX + "assets/" + relPath)
            }
            get("/article-cover/{file}") {
                val file = call.parameters["file"].orEmpty()
                serveResource(FIND_COVERS_PREFIX + file)
            }
        }
    }

    private suspend fun RoutingContext.serveResource(path: String) {
        val bytes = readResource(path)
        if (bytes == null) {
            call.respondBytes(ByteArray(0), ContentType.Text.Plain, HttpStatusCode.NotFound)
            return
        }
        call.respondBytes(bytes, contentTypeFor(path))
    }

    private suspend fun RoutingContext.serveBookFile() {
        val bookId = call.parameters["bookId"].orEmpty()
        val relPath = call.parameters.getAll("path").orEmpty().joinToString("/")
        if (bookId.isEmpty() || relPath.isEmpty()) {
            call.respondBytes(ByteArray(0), ContentType.Text.Plain, HttpStatusCode.NotFound)
            return
        }

        val bytes = readResource(BOOK_PREFIX + bookId + "/" + relPath)
            ?: readResource(ENGINE_PREFIX + relPath.substringAfterLast('/'))
        if (bytes == null) {
            call.respondBytes(ByteArray(0), ContentType.Text.Plain, HttpStatusCode.NotFound)
            return
        }
        call.respondBytes(bytes, contentTypeFor(relPath))
    }

    private fun contentTypeFor(path: String): ContentType =
        runCatching { ContentType.defaultForFilePath(path) }.getOrNull()
            ?: ContentType.Application.OctetStream

    /** Compose resources throws for unknown paths; null means "not bundled". */
    private suspend fun readResource(path: String): ByteArray? =
        runCatching { Res.readBytes(path) }.getOrNull()
}
