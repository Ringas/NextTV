package com.bolttv.compose.core.data

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * 局域网扫码加源服务
 *
 * 手机扫 TV 上显示的二维码 → 打开简单网页 → 提交 M3U URL → TV 端收到并加载。
 * 参考 Bolt-IPTV 的 SourceWebServer 思路。
 */
class SourceWebServer(
    private val port: Int = 8765
) : NanoHTTPD(port) {

    private val TAG = "SourceWebServer"

    data class SubmittedSource(
        val url: String,
        val name: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _lastSubmitted = MutableStateFlow<SubmittedSource?>(null)
    val lastSubmitted: StateFlow<SubmittedSource?> = _lastSubmitted.asStateFlow()

    /** 本地访问地址，如 http://192.168.1.8:8765 */
    private val _accessUrl = MutableStateFlow<String?>(null)
    val accessUrl: StateFlow<String?> = _accessUrl.asStateFlow()

    private val pendingCallback = AtomicReference<((SubmittedSource) -> Unit)?>(null)

    fun setOnSourceSubmitted(callback: (SubmittedSource) -> Unit) {
        pendingCallback.set(callback)
    }

    fun startServer(context: Context): Boolean {
        return try {
            if (!_running.value) {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            }
            val ip = getLocalIpAddress(context)
            val url = "http://$ip:$port"
            _accessUrl.value = url
            _running.value = true
            Log.d(TAG, "Server started at $url")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            _running.value = false
            false
        }
    }

    fun stopServer() {
        try {
            stop()
        } catch (_: Exception) {
        }
        _running.value = false
        _accessUrl.value = null
        Log.d(TAG, "Server stopped")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        return when {
            session.method == Method.POST && uri == "/add" -> handleAdd(session)
            uri == "/" || uri == "/index.html" -> newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                HTML_PAGE
            )
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    private fun handleAdd(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            val params = session.parms
            val url = params["url"]?.trim().orEmpty()
            val name = params["name"]?.trim()?.ifBlank { "扫码源" } ?: "扫码源"

            if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "text/html; charset=utf-8",
                    "<h3>无效的 URL，请以 http/https 开头</h3><a href='/'>返回</a>"
                )
            }

            val submitted = SubmittedSource(url = url, name = name)
            _lastSubmitted.value = submitted
            pendingCallback.get()?.invoke(submitted)

            newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                """
                <html><body style="font-family:sans-serif;padding:24px;">
                <h2>✅ 已提交</h2>
                <p>名称：$name</p>
                <p>地址：$url</p>
                <p>请回到电视查看加载结果。</p>
                <a href="/">继续添加</a>
                </body></html>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "handleAdd error", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: ${e.message}"
            )
        }
    }

    companion object {
        private fun getLocalIpAddress(context: Context): String {
            return try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val ip = wm.connectionInfo.ipAddress
                String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            } catch (_: Exception) {
                "0.0.0.0"
            }
        }

        private val HTML_PAGE = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>Bolt Compose TV - 添加播放源</title>
  <style>
    body { font-family: -apple-system, sans-serif; max-width: 480px; margin: 40px auto; padding: 0 16px; background: #0d1117; color: #e6edf3; }
    h1 { font-size: 1.4rem; }
    label { display: block; margin-top: 16px; color: #8b949e; font-size: 0.9rem; }
    input { width: 100%; padding: 12px; margin-top: 6px; border-radius: 8px; border: 1px solid #30363d; background: #161b22; color: #e6edf3; box-sizing: border-box; }
    button { margin-top: 24px; width: 100%; padding: 14px; border: none; border-radius: 8px; background: #4fc3f7; color: #003544; font-weight: 600; font-size: 1rem; }
  </style>
</head>
<body>
  <h1>📺 添加 M3U 播放源</h1>
  <p style="color:#8b949e">提交后电视端会自动加载</p>
  <form method="POST" action="/add">
    <label>播放源名称（可选）</label>
    <input name="name" placeholder="我的直播源" />
    <label>M3U 地址 *</label>
    <input name="url" required placeholder="https://example.com/playlist.m3u" />
    <button type="submit">提交到电视</button>
  </form>
</body>
</html>
        """.trimIndent()
    }
}
