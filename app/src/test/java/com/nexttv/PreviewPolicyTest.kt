package com.nexttv
import com.nexttv.core.tv.previewRouteOrder
import com.nexttv.core.tv.previewStartDelay
import com.nexttv.core.data.ChannelRoute
import com.nexttv.core.data.playbackUserAgent
import org.junit.Assert.*
import org.junit.Test
class PreviewPolicyTest {
 @Test fun suppliedHlsNeedsNoExtraProbeAndMainOrderIsUnchanged(){val original=listOf(ChannelRoute("http://host/123","s","1"),ChannelRoute("http://host/live.M3U8?key=x","s","2"));assertEquals(original[1],previewRouteOrder(original)[0]);assertEquals("http://host/123",original[0].url)}
 @Test fun extensionlessOnlySourcesStillRemainPlayable(){val r=ChannelRoute("http://host/live","s","1");assertEquals(listOf(r),previewRouteOrder(listOf(r)))}
 @Test fun previewDoesNotTryUnsupportedTransport(){assertTrue(previewRouteOrder(listOf(ChannelRoute("udp://host","s","1"))).isEmpty())}
 @Test fun newStreamsStartProgressivelyRatherThanInOneBurst(){assertEquals(0L,previewStartDelay(-1));assertEquals(15400L,previewStartDelay(7))}
 @Test fun configuredUserAgentIsCaseInsensitive(){assertEquals("source-agent",playbackUserAgent(mapOf("user-agent" to "source-agent"),"default"));assertEquals("default",playbackUserAgent(emptyMap(),"default"))}
}
