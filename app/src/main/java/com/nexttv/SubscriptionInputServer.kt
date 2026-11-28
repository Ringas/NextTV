package com.nexttv

import java.io.Closeable
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/** A short-lived LAN form. Never serves existing subscription data. */
class SubscriptionInputServer(
    address: InetAddress,
    private val lifetimeMs: Long = 300_000,
    private val epgInput: Boolean = false,
    private val namedInput: Boolean = false,
    private val simpleAddress: Boolean = false,
    private val onSubmitted: (String) -> Unit
) : Closeable {
    private val running = AtomicBoolean(true)
    private val consumed = AtomicBoolean(false)
    private val started = System.nanoTime()
    private val server = ServerSocket(0, 4, address).apply { soTimeout = 1000 }
    @Volatile private var client: Socket? = null
    val url = "http://${address.hostAddress}:${server.localPort}"
    private val formPath = "/"
    init {
        Thread({
            try {
                while (running.get() && !expired()) {
                    try {
                        server.accept().use { socket ->
                            client = socket
                            socket.soTimeout = 2500
                            runCatching { handle(socket) }
                            client = null
                        }
                    } catch (_: java.net.SocketTimeoutException) { /* expiry check */ }
                }
            } catch (_: java.net.SocketException) { /* closed by lifecycle */ }
            finally { close() }
        }, "subscription-input").apply { isDaemon = true; start() }
    }
    private fun expired() = (System.nanoTime() - started) / 1_000_000 >= lifetimeMs
    private fun handle(socket: Socket) {
        val input = socket.getInputStream()
        var total = 0
        fun line(): String {
            val bytes = ArrayList<Byte>()
            while (true) {
                val b = input.read()
                require(b >= 0 && ++total <= 8192)
                if (b == 10) break
                if (b != 13) bytes.add(b.toByte())
            }
            return bytes.toByteArray().toString(Charsets.US_ASCII)
        }
        val request = line().split(' ')
        require(request.size == 3)
        val headers = mutableMapOf<String, String>()
        while (true) {
            val text = line()
            if (text.isEmpty()) break
            val split = text.indexOf(':')
            require(split > 0)
            val name = text.take(split).lowercase()
            require(name !in headers)
            headers[name] = text.drop(split + 1).trim()
        }
        fun reply(code: Int, body: String) {
            val data = body.toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 $code Result\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${data.size}\r\nCache-Control: no-store\r\nReferrer-Policy: same-origin\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply { write(head.toByteArray()); write(data); flush() }
        }
        if (expired() || consumed.get()) { reply(410, page("二维码已失效，请在电视重新打开扫码输入。")); return }
        if (request[1] != formPath) { reply(403, page("请扫描电视上的二维码。")); return }
        if (headers["host"] != URI(url).rawAuthority) { reply(403, page("地址不匹配，请重新扫码。")); return }
        when (request[0]) {
            "GET" -> reply(200, page("手机与电视需连接同一局域网。发送后请在电视确认导入。", true))
            "POST" -> {
                // A missing Origin is a raw/non-browser request, not the form.
                if (headers["origin"] != "http://${URI(url).rawAuthority}") {
                    reply(403, page("请从扫码页面提交。")); return
                }
                val length = headers["content-length"]?.toIntOrNull()
                if (length == null || length !in 1..8192 || "transfer-encoding" in headers ||
                    headers["content-type"]?.substringBefore(';') != "application/x-www-form-urlencoded") {
                    reply(400, page("输入无效，请重新扫码。")); return
                }
                val body = ByteArray(length)
                var offset = 0
                while (offset < length) { val count = input.read(body, offset, length - offset); require(count > 0); offset += count }
                val values = body.toString(Charsets.UTF_8).split('&').map { it.split('=', limit = 2) }
                val value = values.singleOrNull { it[0] == "url" }?.getOrNull(1)
                val source = runCatching { URLDecoder.decode(value.orEmpty(), "UTF-8").let { normalizeAddressInput(it,if(namedInput)AddressInputKind.SUBSCRIPTION else if(epgInput)AddressInputKind.PROGRAMME_GUIDE else AddressInputKind.PLAIN_URL,allowEmpty=false) } }.getOrNull()
                if (source == null) { reply(400, page("请输入完整的 http:// 或 https:// 订阅地址。", true)); return }
                consumed.set(true)
                reply(200, page("地址已发送。请在电视上选择「导入 / 确认」。可关闭此页面。"))
                onSubmitted(source)
            }
            else -> reply(405, page("不支持此操作。"))
        }
    }
    private fun page(message: String, form: Boolean = false): String {
      val markup = """<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>电视订阅输入</title><style>body{margin:0;background:#15161a;color:#f2f2f4;font:18px system-ui;line-height:1.6}main{max-width:480px;margin:8vh auto;padding:24px}h1{font-size:28px}p{color:#c5c7ce}input,button{box-sizing:border-box;width:100%;padding:16px;border-radius:12px;font:inherit;margin:12px 0}input{background:#282a30;color:white;border:1px solid #777}button{background:#fff;color:#20232a;border:0}</style><main><h1>电视订阅输入</h1><p>$message</p>${if (form) """<form method="post" action="$formPath"><label for="url">M3U 订阅地址</label><input id="url" name="url" type="url" maxlength="2048" placeholder="http://…/live.m3u" required autocomplete="off" autocapitalize="none" spellcheck="false"><button type="submit">发送到电视</button></form>""" else ""}</main></html>"""
      val visual=if(simpleAddress)markup.replace("#15161a","#f5f7fb").replace("#f2f2f4","#17243b").replace("#c5c7ce","#566579").replace("#282a30","#ffffff").replace("color:white","color:#17243b").replace("background:#fff;color:#20232a","background:#2f80ed;color:#fff").replace("电视订阅输入","Next TV 地址输入")else markup
      val prepared=if(namedInput)visual.replace("M3U 订阅地址","订阅名称与地址（名称可省略）").replace("type=\"url\"","type=\"text\"").replace("http://…/live.m3u","名称,http://…/live.m3u").replace("导入 / 确认","确认保存订阅") else visual
      return if(epgInput) prepared.replace("电视订阅输入","Next TV 节目单地址").replace("M3U 订阅地址","XMLTV 节目单地址").replace("订阅地址","节目单地址").replace("/live.m3u","/guide.xml").replace("导入 / 确认","确认更新节目单").replace("确认导入","确认更新") else prepared
    }

    override fun close() {
        running.set(false)
        runCatching { server.close() }
        runCatching { client?.close() }
    }
}

fun validateSubscriptionAddress(value: String): String {
    val text = value.trim()
    require(text.length in 1..2048 && text.none { it.isWhitespace() || it.code < 32 })
    val uri = URI(text)
    require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawFragment == null)
    require(uri.port == -1 || uri.port in 1..65535)
    return text
}

fun subscriptionLanAddress(): InetAddress = NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
    .sortedBy { if (it.name.startsWith("eth") || it.name.startsWith("wlan")) 0 else 1 }
    .flatMap { it.inetAddresses.toList() }
    .firstOrNull { it is Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
    ?: error("请先将电视连接到家庭 Wi-Fi 或有线网络")
