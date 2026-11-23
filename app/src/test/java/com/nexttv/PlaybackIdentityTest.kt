package com.nexttv
import com.nexttv.core.tv.playbackIdentityFacts
import org.junit.Assert.*
import org.junit.Test
class PlaybackIdentityTest{
 @Test fun fourOrderedFields(){assertEquals(listOf("1/2","1080P","立体声","0 B/s"),playbackIdentityFacts(2,0,1080,2,0))}
 @Test fun singleRouteStillVisible(){assertEquals("1/1",playbackIdentityFacts(1,0,720,1,1024).first())}
 @Test fun unknownNotInvented(){assertEquals(listOf("1/1","—","—","—"),playbackIdentityFacts(0,-1,0,0,null))}
 @Test fun ultraResolutionAndAudio(){assertEquals(listOf("2/2","4K","6声道","1.5 MB/s"),playbackIdentityFacts(2,1,2160,6,1572864))}
 @Test fun eightK(){assertEquals("8K",playbackIdentityFacts(1,0,4320,2,1)[1])}
}
