package com.nexttv

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SubscriptionInputTest {
    companion object { init { System.setProperty("sun.net.http.allowRestrictedHeaders", "true") } }
    private fun request(url:String, data:String?=null):Pair<Int,String> {
        val c=URL(url).openConnection() as HttpURLConnection
        c.connectTimeout=2000;c.readTimeout=2000
        try {
            if(data!=null) {
                c.requestMethod="POST";c.doOutput=true
                c.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
                c.setRequestProperty("Origin","http://"+URL(url).authority)
                c.outputStream.use {it.write(data.toByteArray())}
            }
            val code=c.responseCode
            return code to (if(code<400)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
        } finally {c.disconnect()}
    }
    @Test fun validUrlPreservesQuery() {assertEquals("http://192.0.2.115:8788/live.m3u?a=1&b=2",validateSubscriptionAddress(" http://192.0.2.115:8788/live.m3u?a=1&b=2 "))}
    @Test fun invalidAddressesRejected() {
        listOf("file:///etc/passwd","javascript:alert(1)","http://","https://example.com/a\nb","http://example.com:99999/a","http://example.com/#x").forEach {assertTrue(runCatching{validateSubscriptionAddress(it)}.isFailure)}
    }
    @Test fun mobileSubmissionIsDeliveredOnce() {
        val received=mutableListOf<String>();val latch=CountDownLatch(1)
        SubscriptionInputServer(InetAddress.getLoopbackAddress()) {received.add(it);latch.countDown()}.use {server ->
            assertTrue(request(server.url).second.contains("发送到电视"))
            val source="http://192.0.2.115:8788/live.m3u?a=1&b=2"
            assertEquals(200,request(server.url,"url="+URLEncoder.encode(source,"UTF-8")).first)
            assertTrue(latch.await(2,TimeUnit.SECONDS));assertEquals(listOf(source),received)
            assertEquals(410,request(server.url,"url="+URLEncoder.encode(source,"UTF-8")).first)
        }
    }
    @Test fun wrongTokenAndInvalidUrlDoNotDeliver() {
        var count=0
        SubscriptionInputServer(InetAddress.getLoopbackAddress()) {count++}.use {s ->
            assertEquals(403,request(s.url.substringBeforeLast('/')+"/bad").first)
            assertEquals(400,request(s.url,"url=file%3A%2F%2F%2Ftmp%2Fx").first)
            assertEquals(0,count)
            assertEquals(200,request(s.url).first)
        }
    }
    private fun browserPost(url:String,origin:String):Int {
        val u=URL(url)
        val body="url="+URLEncoder.encode("http://192.0.2.115:8788/live.m3u","UTF-8")
        return java.net.Socket(u.host,u.port).use { socket ->
            socket.soTimeout=2000
            val request="POST ${u.path} HTTP/1.1\r\nHost: ${u.authority}\r\nOrigin: $origin\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
            socket.getOutputStream().write(request.toByteArray())
            socket.getInputStream().bufferedReader().readLine().split(' ')[1].toInt()
        }
    }
    @Test fun browserPolicyPreservesSameOriginSubmission() {
        val latch=CountDownLatch(1)
        SubscriptionInputServer(InetAddress.getLoopbackAddress()) {latch.countDown()}.use {server ->
            val c=URL(server.url).openConnection() as HttpURLConnection
            try {assertEquals("same-origin",c.getHeaderField("Referrer-Policy"));c.inputStream.close()} finally {c.disconnect()}
            assertEquals(200,browserPost(server.url,server.url.substringBeforeLast('/')))
            assertTrue(latch.await(2,TimeUnit.SECONDS))
        }
    }
    @Test fun crossOriginAndNullOriginRejectedWithoutConsumingSession() {
        val latch=CountDownLatch(1)
        SubscriptionInputServer(InetAddress.getLoopbackAddress()) {latch.countDown()}.use {s ->
            assertEquals(403,browserPost(s.url,"null"))
            assertEquals(403,browserPost(s.url,"https://unrelated.example"))
            assertEquals(1L,latch.count)
            assertEquals(200,browserPost(s.url,s.url.substringBeforeLast('/')))
            assertTrue(latch.await(2,TimeUnit.SECONDS))
        }
    }
    @Test fun closeStopsListener() {
        val s=SubscriptionInputServer(InetAddress.getLoopbackAddress()) {}
        s.close();assertTrue(runCatching {request(s.url)}.isFailure)
    }
    @Test fun expiresWithoutSubmission() {
        val s=SubscriptionInputServer(InetAddress.getLoopbackAddress(),lifetimeMs=10) {fail("Unexpected submission")}
        s.use {Thread.sleep(40);assertTrue(runCatching{request(s.url).first==410}.getOrDefault(true))}
    }
    @Test fun epgFormLabelsAndConfirmationUseXmltv() {
        SubscriptionInputServer(InetAddress.getLoopbackAddress(),epgInput=true) {}.use { server ->
            val page=request(server.url).second
            assertTrue(page.contains("XMLTV 节目单地址"));assertTrue(page.contains("guide.xml"));assertFalse(page.contains("M3U 订阅地址"))
            val response=request(server.url,"url="+URLEncoder.encode("http://example.test/guide.xml.gz?key=abc","UTF-8"))
            assertEquals(200,response.first);assertTrue(response.second.contains("确认更新节目单"))
        }
    }
    @Test fun shortLanAddressKeepsCapabilityTokenInPath() {
        val received=java.util.concurrent.LinkedBlockingQueue<String>()
        SubscriptionInputServer(InetAddress.getLoopbackAddress(),simpleAddress=true,namedInput=true){received.add(it)}.use { server ->
            val path=java.net.URI(server.url).path
            val token=path.removePrefix("/")
            assertEquals(32, token.length)
            assertTrue(token.matches(Regex("[0-9a-f]{32}")))
            assertTrue(request(server.url).second.contains("name=\"url\""))
            assertFalse(request(server.url).second.contains("name=\"session\""))
            assertEquals(403,request(server.url.substringBeforeLast('/')+"/bad","url="+URLEncoder.encode("http://example.test/live.m3u","UTF-8")).first)
            assertEquals(200,request(server.url,"url="+URLEncoder.encode("家庭，http://example.test/live.m3u","UTF-8")).first)
            assertEquals("家庭,http://example.test/live.m3u",received.poll(2,TimeUnit.SECONDS))
        }
    }
}
