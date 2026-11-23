package com.nexttv
import com.nexttv.core.data.*
import org.junit.Assert.*
import org.junit.Test
class AuditRegressionTest {
 @Test fun tokenRotationKeepsLogicalIdentity(){
  fun parse(token:String)=M3uParser.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"one\",News\nhttps://example.test/live?token=$token", "provider").channels.single()
  assertEquals(parse("a").id,parse("b").id)
 }
 @Test fun sourceNamespacesCannotCollide(){
  val text="#EXTM3U\n#EXTINF:-1 tvg-id=\"1\",News\nhttps://example.test/live"
  assertEquals(2,mergeChannelRoutes(M3uParser.parse(text,"a").channels+M3uParser.parse(text,"b").channels).size)
 }
 @Test fun quoteAwareDelimiterAndRelativeAddressAndHeaders(){
  val text="#EXTM3U\n#EXTINF:-1 tvg-logo=\"https://example.test/logo,a.png\",News\n#EXTVLCOPT:http-user-agent=Special TV\n#EXTVLCOPT:http-referrer=https://example.test/\nstreams/one.m3u8"
  val channel=M3uParser.parse(text,"a","https://example.test/list/live.m3u").channels.single()
  assertEquals("News",channel.name);assertEquals("https://example.test/list/streams/one.m3u8",channel.url)
  assertEquals("Special TV",channel.playbackRoutes.single().headers["User-Agent"])
  assertEquals("https://example.test/",channel.playbackRoutes.single().headers["Referer"])
 }
 @Test fun finiteRecoveryWithoutPrematureReset(){val r=RouteRecovery();r.reset(0);assertEquals(1,r.next(3,0));assertEquals(2,r.next(3,1));assertNull(r.next(3,2));assertNull(r.next(3,2))}
}
