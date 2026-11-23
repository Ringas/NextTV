package com.nexttv
import com.nexttv.core.data.Channel
import com.nexttv.core.tv.initialPlaybackChannel
import org.junit.Assert.*
import org.junit.Test
class StartupRouteTest {
 private val channels=listOf(Channel("a","A","","","","http://example.test/a","source"),Channel("b","B","","","","http://example.test/b","source"))
 @Test fun liveResolvesSavedIdentityBeforeComposition(){assertEquals(channels[1],initialPlaybackChannel(channels,true,"b"))}
 @Test fun missingSavedChannelFallsBackToFirst(){assertEquals(channels[0],initialPlaybackChannel(channels,true,"removed"))}
 @Test fun homeOrEmptyCacheDoesNotCreatePlayer(){assertNull(initialPlaybackChannel(channels,false,"b"));assertNull(initialPlaybackChannel(emptyList(),true,"b"))}
}
