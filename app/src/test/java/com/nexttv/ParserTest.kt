package com.nexttv
import com.nexttv.core.data.M3uParser
import com.nexttv.core.data.XmltvTimeParser
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
/** Production parser and time parsing cases for Next TV. */
class ParserTest {
 @Test fun emptyPlaylist(){assertTrue(M3uParser.parse("","provider").channels.isEmpty())}
 @Test fun attributesGroupsCommasAndRelativeMedia(){val c=M3uParser.parse("""#EXTM3U
#EXTINF:-1 tvg-id="news" group-title="News, HD",News, live
stream.m3u8
#EXTINF:-1,Other
https://example.org/other
""","provider","https://example.org/list/index.m3u").channels;assertEquals(2,c.size);assertEquals("News, HD",c[0].group);assertEquals("News, live",c[0].name);assertEquals("news",c[0].tvgId);assertEquals("https://example.org/list/stream.m3u8",c[0].url);assertEquals("未分组",c[1].groupName)}
 @Test fun invalidStreamIgnored(){assertTrue(M3uParser.parse("#EXTINF:-1,X\nnot-a-url","provider").channels.isEmpty())}
 @Test fun declaredUriDoesNotFalselyClaimPlaybackCapability(){
  val skipped=M3uParser.parse("#EXTINF:-1,X\nfile:///tmp/x\n#EXTINF:-1,Y\nrtsp://example.test/live","provider")
  assertTrue(skipped.channels.isEmpty());assertEquals(2,skipped.skippedUnsupported)
  assertFalse(supportsHttpPlayback("file:///tmp/x"));assertFalse(supportsHttpPlayback("rtsp://example.test/live"))
  assertTrue(supportsHttpPlayback("HTTP://example.test/live"));assertTrue(supportsHttpPlayback("https://example.test/no-suffix"))
 }
 @Test fun unquotedSingleQuotedAndExtendedAttributesAreAccepted(){
  val c=M3uParser.parse("""#EXTM3U url-tvg="http://guide"
#EXTINF:-1 tvg-id=news tvg-name='News HD' tvg-logo='http://logo.test/news.png' tvg-chno=42 group-title=News,News
#EXTGRP:News Extra
#EXTHTTP:{"User-Agent":"ExtUA","Referer":"http://ref.test"}
#KODIPROP:http-user-agent=KodiUA
#KODIPROP:inputstream.adaptive.stream_headers=Cookie=session%3D1&X-Token=t
http://stream.test/live|User-Agent=PipeUA&Referer=http://pipe.test
""","provider").channels.single()
  assertEquals("news",c.tvgId);assertEquals("News",c.name);assertEquals("http://logo.test/news.png",c.logo)
  assertEquals("News Extra",c.group);assertEquals(42,c.number);assertEquals("http://stream.test/live",c.url)
  assertEquals("PipeUA",c.headers["User-Agent"]);assertEquals("http://pipe.test",c.headers["Referer"])
  assertEquals("session%3D1",c.headers["Cookie"]);assertEquals("t",c.headers["X-Token"])
 }
 @Test fun pipeHeaderSemicolonIsCookieDataNotSeparator(){
  val c=M3uParser.parse("#EXTINF:-1,Cookie\nhttp://stream.test/live|Cookie=a=b; c=d&X-Token=t","provider").channels.single()
  assertEquals("a=b; c=d",c.headers["Cookie"]);assertEquals("t",c.headers["X-Token"])
 }
 @Test fun pipeHeaderCountIsCappedBeforeCacheWrite(){
  val pairs=(1..120).joinToString("&"){"X-"+it+"=v"+it}
  val c=M3uParser.parse("#EXTINF:-1,Many\nhttp://stream.test/live|"+pairs,"provider").channels.single()
  assertEquals(M3uParser.MAX_HEADERS,c.headers.size)
 }
 @Test fun catchupDefaultAndShiftModesAreAccepted(){
  val base=com.nexttv.core.data.ChannelRoute("http://host/live","p","1",catchup="default",catchupSource="")
  assertEquals("http://host/live?utc=1000&lutc=3000",com.nexttv.core.data.replayAddress(base,com.nexttv.core.data.Program("1","p","",1000000,2000000),3000000))
  val shift=base.copy(catchup="shift",catchupSource="?start={start}&end={end}")
  assertEquals("http://host/live?start=1000&end=2000",com.nexttv.core.data.replayAddress(shift,com.nexttv.core.data.Program("1","p","",1000000,2000000),3000000))
 }
 @Test fun timezoneIsAppliedByCurrentXmltvParser(){assertEquals(Instant.parse("2026-11-16T04:00:00Z").toEpochMilli(),XmltvTimeParser().parse("20261116120000 +0800"))}
 @Test fun invalidCalendarAndMalformedTimesAreRejected(){val t=XmltvTimeParser();assertNull(t.parse("20260230120000 +0000"));assertNull(t.parse("bad"));assertNull(t.parse("20261116120000 +2460"))}
 @Test fun strictTimestampAndWhitespace(){val t=XmltvTimeParser();assertEquals(Instant.parse("2026-11-16T06:30:00Z").toEpochMilli(),t.parse("20261116120000   +0530"));assertNull(t.parse("202611161200 +0530"))}
 @Test fun streamMimeChecksSurviveParserCleanup(){assertEquals(HLS_MIME,streamMime("https://example.test/live","application/vnd.apple.mpegurl;charset=UTF-8"));assertEquals(HLS_MIME,streamMime("https://example.test/live","text/plain","#EXTM3U\n"));assertNull(streamMime("https://example.test/live","video/mp2t"))}
}
