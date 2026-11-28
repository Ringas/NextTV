// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.Closeable
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class StreamResolverRedirectTest {
    private class TinyServer(private val handler: (path: String, port: Int, authorization: String?) -> Pair<Int, String?>) : Closeable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort
        private val worker = thread(isDaemon = true, name = "redirect-test-server") {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        val reader = InputStreamReader(client.getInputStream()).buffered()
                        val request = reader.readLine() ?: return@use
                        val path = request.split(' ').getOrNull(1).orEmpty()
                        var authorization: String? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) break
                        line.trim().substringAfter(':', "").takeIf { line.startsWith("Authorization:", true) }?.trim()?.let { authorization = it }
                        }
                        val (status, location) = handler(path, port, authorization)
                        val body = "hello"
                        PrintWriter(client.getOutputStream()).use { out ->
                            out.write("HTTP/1.1 $status Test\r\n")
                            out.write("Content-Length: ${body.length}\r\n")
                            location?.let { out.write("Location: $it\r\n") }
                            out.write("Connection: close\r\n\r\n")
                            out.write(body)
                            out.flush()
                        }
                    }
                } catch (_: Exception) {
                    if (!socket.isClosed) continue
                }
            }
        }

        override fun close() {
            socket.close()
            worker.join(1_000)
        }
    }

    @Test fun crossOriginRedirectDoesNotForwardAuthorization() = runBlocking {
        val targetAuth = AtomicReference<String?>()
        val target = TinyServer { _, _, authorization -> targetAuth.set(authorization); 200 to null }
        val source = TinyServer { _, _, _ -> 302 to "http://127.0.0.1:${target.port}/target" }
        try {
            val resolved = resolveStream(
                "http://127.0.0.1:${source.port}/start",
                mapOf("Authorization" to "Bearer secret", "Referer" to "https://private.test/"),
                probe = true,
            )
            assertEquals("http://127.0.0.1:${target.port}/target", resolved.url)
            assertNull(targetAuth.get())
        } finally {
            source.close()
            target.close()
        }
    }

    @Test fun sameOriginRedirectKeepsAuthorization() = runBlocking {
        val auth = AtomicReference<String?>()
        val server = TinyServer { path, port, authorization ->
            if (path == "/start") 302 to "http://127.0.0.1:$port/target" else {
                auth.set(authorization)
                200 to null
            }
        }
        try {
            val resolved = resolveStream(
                "http://127.0.0.1:${server.port}/start",
                mapOf("Authorization" to "Bearer secret"),
                probe = true,
            )
            assertEquals("http://127.0.0.1:${server.port}/target", resolved.url)
            assertEquals("Bearer secret", auth.get())
        } finally {
            server.close()
        }
    }
}
