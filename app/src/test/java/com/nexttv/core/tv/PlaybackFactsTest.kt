package com.nexttv.core.tv
import org.junit.Assert.*
import org.junit.Test
class PlaybackFactsTest {
 @Test fun routeResolutionCodecAudioRateHaveOneOwner(){val facts=playbackFacts(2,1,1080,"video/hevc",2,2097152);assertEquals(listOf("线路 2/2","1080P","HEVC","立体声","2.0 MB/s"),facts);assertEquals(facts.size,facts.toSet().size)}
 @Test fun unknownFactsAndSingleRouteAreHidden(){assertEquals(emptyList<String>(),playbackFacts(1,0,0,null,0,null))}
 @Test fun fourKAndZeroDownloadRemainHonest(){assertEquals(listOf("4K","AVC","6声道","0 KB/s"),playbackFacts(1,0,2160,"video/avc",6,0))}
 @Test fun eightKAndOtherResolutionsKeepUnits(){assertTrue(playbackFacts(1,0,4320,null,0,null).contains("8K"));assertTrue(playbackFacts(1,0,720,null,0,null).contains("720P"))}
}
