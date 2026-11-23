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
 @Test fun declaredUriDoesNotFalselyClaimPlaybackCapability(){val c=M3uParser.parse("#EXTINF:-1,X\nfile:///tmp/x","provider").channels.single();assertFalse(supportsHttpPlayback(c.url));assertFalse(supportsHttpPlayback("rtsp://example.test/live"));assertTrue(supportsHttpPlayback("HTTP://example.test/live"));assertTrue(supportsHttpPlayback("https://example.test/no-suffix"))}
 @Test fun timezoneIsAppliedByCurrentXmltvParser(){assertEquals(Instant.parse("2026-11-16T04:00:00Z").toEpochMilli(),XmltvTimeParser().parse("20261116120000 +0800"))}
 @Test fun invalidCalendarAndMalformedTimesAreRejected(){val t=XmltvTimeParser();assertNull(t.parse("20260230120000 +0000"));assertNull(t.parse("bad"));assertNull(t.parse("20261116120000 +2460"))}
 @Test fun strictTimestampAndWhitespace(){val t=XmltvTimeParser();assertEquals(Instant.parse("2026-11-16T06:30:00Z").toEpochMilli(),t.parse("20261116120000   +0530"));assertNull(t.parse("202611161200 +0530"))}
 @Test fun streamMimeChecksSurviveParserCleanup(){assertEquals(HLS_MIME,streamMime("https://example.test/live","application/vnd.apple.mpegurl;charset=UTF-8"));assertEquals(HLS_MIME,streamMime("https://example.test/live","text/plain","#EXTM3U\n"));assertNull(streamMime("https://example.test/live","video/mp2t"))}
}
