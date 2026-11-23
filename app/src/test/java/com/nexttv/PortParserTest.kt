package com.nexttv

import com.nexttv.core.data.M3uParser
import org.junit.Assert.*
import org.junit.Test

/** Exercises the production M3U parser used by Next TV. */
class PortParserTest {
 @Test fun metadataAndExtensionlessAddressSurvivePort(){
  val parsed=M3uParser.parse("""#EXTM3U x-tvg-url="https://example.test/epg.xml.gz"
#EXTINF:-1 tvg-id="news.id" tvg-logo="https://example.test/logo.png" group-title="新闻",News HD
https://example.test/channel/123?token=a
""","source-a")
  assertEquals("https://example.test/epg.xml.gz",parsed.epgUrl)
  val channel=parsed.channels.single()
  assertEquals("news.id",channel.tvgId);assertEquals("https://example.test/logo.png",channel.logo)
  assertEquals("https://example.test/channel/123?token=a",channel.url);assertEquals("source-a",channel.sourceId)
 }
 @Test fun duplicateEntriesAndDistinctRoutesFollowParserRules(){
  val parsed=M3uParser.parse("""#EXTM3U
#EXTINF:-1,News
https://example.test/a
#EXTINF:-1,News
https://example.test/a
#EXTINF:-1,News
https://example.test/b
""","source-a")
  assertEquals(2,parsed.channels.size);assertEquals(parsed.channels[0].id,parsed.channels[1].id)
  assertEquals(parsed.channels,M3uParser.parse("""#EXTM3U
#EXTINF:-1,News
https://example.test/a
#EXTINF:-1,News
https://example.test/b
""","source-a").channels)
 }
 @Test fun malformedInputDoesNotCreatePlayableChannels(){
  assertTrue(M3uParser.parse("#EXTM3U\n#EXTINF:-1,Bad\nnot-a-url\n", "source").channels.isEmpty())
 }
}
