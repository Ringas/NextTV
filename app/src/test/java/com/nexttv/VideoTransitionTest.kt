package com.nexttv
import com.nexttv.core.tv.videoTransitionNeedsCover
import org.junit.Assert.*
import org.junit.Test
class VideoTransitionTest {
 @Test fun capturingMustNotBlackOutWorkingOldVideo(){assertFalse(videoTransitionNeedsCover(true,false,true,false))}
 @Test fun committedSnapshotDoesNotNeedBlackUnderlay(){assertFalse(videoTransitionNeedsCover(true,true,false,false));assertFalse(videoTransitionNeedsCover(true,true,true,true))}
 @Test fun unavailableSurfaceCanUseFallback(){assertTrue(videoTransitionNeedsCover(true,false,false,false))}
 @Test fun newFirstFrameWithoutSnapshotRevealsGradually(){assertTrue(videoTransitionNeedsCover(true,false,true,true))}
 @Test fun retentionOffCanUseRevealLayer(){assertTrue(videoTransitionNeedsCover(false,false,true,false));assertTrue(videoTransitionNeedsCover(false,false,true,true))}
}
