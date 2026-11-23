package com.nexttv
import com.nexttv.core.data.M3uParser
import com.nexttv.core.data.replayAddress
import com.nexttv.core.data.Program
import org.junit.Assert.*
import org.junit.Test
class ActiveReplayTest {
 @Test fun activeParserCarriesRouteReplayAndUsesExistingGatewayRules(){val parsed=M3uParser.parse("""#EXTM3U
#EXTINF:-1 tvg-id="one" catchup="append" catchup-source="?begin={utc}&end={utcend}" catchup-days="7",One
http://example.test/live
""","s").channels.single();val p=Program("one","Past","",1000000,2000000);assertEquals("http://example.test/live?begin=1000&end=2000",replayAddress(parsed.playbackRoutes.single(),p,3000000));assertNull(replayAddress(parsed.playbackRoutes.single(),p,1500000));assertNull(replayAddress(parsed.playbackRoutes.single().copy(catchup="none"),p,3000000))}
 @Test fun routeBadgeSharesThePlaybackTemplateRules() {
  val valid = com.nexttv.core.data.ChannelRoute("https://example.test/live", "source", "channel", "append", "?start={utc}", 2)
  val unsupported = listOf(valid.copy(catchup = "none"), valid.copy(catchupSource = "?start={invalid}"), valid.copy(catchupDays = 0))
  assertTrue(com.nexttv.core.data.supportsReplay(valid))
  for (route in unsupported) {
   assertFalse(com.nexttv.core.data.supportsReplay(route))
   assertNull(replayAddress(route, Program("one", "Past", "", 1000, 2000), 3000))
  }
 }
}
