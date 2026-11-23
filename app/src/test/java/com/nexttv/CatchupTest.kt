package com.nexttv

import org.junit.Assert.*
import com.nexttv.core.data.*
import org.junit.Test
import java.time.Instant

class CatchupTest {
    private val start=Instant.parse("2026-11-17T12:00:00Z").toEpochMilli()
    private val programme=Program("one","Past","",start,start+1800000)
    private val template="?playbackbegin=\${(b)yyyyMMddHHmmss}&playbackend=\${(e)yyyyMMddHHmmss}"
    private val channel=ChannelRoute("http://localhost/live?token=ok","provider","one",catchup="append",catchupSource=template)
    @Test fun preservesQueryAndUsesGatewayTimezone() {
        assertEquals("http://localhost/live?token=ok&playbackbegin=20261117200000&playbackend=20261117203000",replayAddress(channel,programme,start+3600000))
    }
    @Test fun refusesDisabledFutureExpiredOrUnknownTemplate() {
        assertNull(replayAddress(channel.copy(catchup="none"),programme,start+3600000))
        assertNull(replayAddress(channel,programme,start))
        assertNull(replayAddress(channel.copy(catchupDays=1),programme,start+172800000))
        assertNull(replayAddress(channel.copy(catchupSource="?a={unsupported}"),programme,start+3600000))
    }
    @Test fun parsesPlaybackMetadata() {
        val text="#EXTM3U\n#EXTINF:-1 catchup=\"append\" catchup-source=\"$template\" catchup-days=\"3\" tvg-id=\"one\",One\nhttp://localhost/live"
        val parsed=M3uParser.parse(text,"provider").channels.single().playbackRoutes.single()
        assertEquals("append",parsed.catchup);assertEquals(template,parsed.catchupSource);assertEquals(3,parsed.catchupDays)
    }
}
