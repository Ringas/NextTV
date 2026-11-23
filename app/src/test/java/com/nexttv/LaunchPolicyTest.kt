package com.nexttv
import com.nexttv.core.tv.LaunchPolicy
import com.nexttv.core.tv.initialPlaybackChannel
import com.nexttv.core.data.Channel
import com.nexttv.core.data.mergeChannelRoutes
import org.junit.Assert.*
import org.junit.Test
class LaunchPolicyTest {
 @Test fun startupFindsFormerChannelIdAfterCrossSourceMerge(){
  val first=Channel("first","News","news","","","https://example.test/first","a")
  val alternate=first.copy(id="alternate",url="https://example.test/second",sourceId="b")
  val other=first.copy(id="other",name="Sports",tvgId="sports")
  val merged=mergeChannelRoutes(listOf(other,first,alternate),acrossSources=true)
  assertEquals("first",initialPlaybackChannel(merged,true,"alternate")?.id)
 }
 @Test fun realTvAndNonTouchBoxesResetOnReturn(){assertTrue(LaunchPolicy.classify(true,true,false,"mt5879","TCL Q10K","tcl"));assertTrue(LaunchPolicy.classify(false,false,false,"mt5879","TCL","tcl"))}
 @Test fun emulatorKeepsBackgroundEvenWithTvFeatures(){assertFalse(LaunchPolicy.classify(true,true,false,"ranchu","Android SDK built for x86","sdk_gphone_x86"));assertFalse(LaunchPolicy.classify(true,true,false,"mumu","MuMu","mumu"))}
 @Test fun phoneKeepsBackgroundState(){assertFalse(LaunchPolicy.classify(false,false,true,"gs101","Pixel","oriole"))}
}
