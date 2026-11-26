package com.nexttv

import com.nexttv.core.tv.*
import com.nexttv.core.data.*
import com.nexttv.core.ui.ProgrammeTimeLabels
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class CompatibilityRefactorTest {
 @Test fun inputAliasesKeepNativeTextAndTabOutsideSemanticRouting(){
  for(k in listOf(23,66,160,62))assertEquals(InputAction.CONFIRM,TvInput.action(k))
  assertFalse(TvInput.confirm(62,includeSpace=false));assertTrue(TvInput.back(4));assertTrue(TvInput.back(111))
  assertNull(TvInput.action(61));assertNull(TvInput.action(29))
  assertEquals(0,TvInput.digit(7));assertEquals(9,TvInput.digit(16));assertEquals(0,TvInput.digit(144));assertEquals(9,TvInput.digit(153));assertNull(TvInput.digit(66))
 }
 @Test fun browserMustPassThroughChannelOnlyInBothDirections(){
  val groups=BrowserNavigation.Groups;assertEquals(BrowserMove.CHANNELS,groups.horizontal(1,false))
  val channels=groups.focusChannels(closeAdjacent=true);assertEquals(BrowserPane.CHANNELS,channels.pane);assertEquals(BrowserMove.PROGRAMMES,channels.horizontal(1,false))
  assertEquals(BrowserMove.GROUPS,BrowserNavigation().horizontal(-1,false))
  assertEquals(BrowserMove.NONE,BrowserNavigation.Programmes.horizontal(-1,false))
  assertEquals(BrowserMove.NONE,BrowserNavigation.Programmes.horizontal(1,false))
  assertEquals(BrowserMove.NONE,BrowserNavigation.Dates.horizontal(-1,false))
  assertEquals(BrowserMove.NONE,BrowserNavigation.Dates.horizontal(-1,true))
  assertEquals(BrowserMove.NONE,BrowserNavigation.Dates.horizontal(1,false))
 }
 @Test fun impossiblePaneRegionCombinationsCannotBeConstructed(){
  for(pair in listOf(BrowserPane.CHANNELS to BrowserRegion.GROUPS,BrowserPane.GROUPS to BrowserRegion.PROGRAMMES,BrowserPane.PROGRAMMES to BrowserRegion.GROUPS))assertTrue(runCatching{BrowserNavigation(pair.first,pair.second)}.isFailure)
 }
 @Test fun addAndEditShareRulesWithoutDiscardingMetadataOrIdentity(){
  val original=Source("kept-id","家庭","http://a.test/list",headers="Token: secret",enabled=false,epgUrl="http://a.test/epg")
  assertEquals(original,subscriptionFromAddress("http://a.test/list",listOf(original)){error("must not create duplicate source")})
  val edit=subscriptionFromAddress("新名称，http://b.test/list",listOf(original),editing=original)
  assertEquals("kept-id",edit.id);assertEquals("新名称",edit.name);assertEquals(original.headers,edit.headers);assertFalse(edit.enabled);assertEquals(original.epgUrl,edit.epgUrl)
  assertEquals("家庭",subscriptionFromAddress("http://b.test/list",listOf(original),editing=original).name)
  assertEquals("订阅 2",subscriptionFromAddress("http://b.test/list",listOf(original),newId={"new"}).name)
 }
 @Test fun addressValidationIsSharedButEpgResetRequiresExplicitAllowance(){
  assertEquals("家庭,http://a.test/a,b",normalizeAddressInput(" 家庭， http://a.test/a,b ",AddressInputKind.SUBSCRIPTION))
  assertEquals("",normalizeAddressInput("  ",AddressInputKind.PROGRAMME_GUIDE))
  assertTrue(runCatching{normalizeAddressInput("",AddressInputKind.PROGRAMME_GUIDE,allowEmpty=false)}.isFailure)
  assertTrue(runCatching{normalizeAddressInput("",AddressInputKind.SUBSCRIPTION)}.isFailure)
  assertEquals("保存并刷新",addressInputLabels(AddressInputKind.SUBSCRIPTION,true).confirm)
 }
 @Test fun headersHaveSamePrecedenceAndNoDuplicateCaseVariants(){
  val source=Source("id","test","http://a.test",userAgent="configured",referer="http://a.test",headers="x-token: old\nuser-agent: custom")
  val h=playbackHeaders(source,"fallback","",mapOf("X-Token" to "new","USER-AGENT" to "route"))
  assertEquals(1,h.keys.count{it.equals("user-agent",true)});assertEquals("route",h["USER-AGENT"]);assertEquals("new",h["X-Token"]);assertEquals("http://a.test",h["Referer"])
 }
 @Test fun reusedTimeFormattersFollowTimeZoneChanges(){
  val old=TimeZone.getDefault();val locale=Locale.getDefault()
  try{Locale.setDefault(Locale.US);for(zone in listOf("UTC","Asia/Shanghai","America/New_York")){TimeZone.setDefault(TimeZone.getTimeZone(zone));for(time in listOf(0L,1770000000000L)){assertEquals(java.text.SimpleDateFormat("HH:mm",Locale.US).format(Date(time)),ProgrammeTimeLabels.time(time))}}}finally{TimeZone.setDefault(old);Locale.setDefault(locale)}
 }
 @Test fun digitCapacityIsSharedAndLongChannelNumbersAreNotTruncated(){
  assertEquals(3,channelDigitCapacity(9));assertEquals(5,channelDigitCapacity(10542));assertEquals("10542",appendChannelDigit("1054",2,5));assertEquals("542",appendChannelDigit("1054",2,3))
 }
 @Test fun programmeAndReplayProgressHandleUnknownAndMalformedTimes(){
  assertEquals(0f,programmeProgress(null,500),0f);assertEquals(0f,programmeProgress(Program("c","t","",1000,500),2000),0f)
  assertEquals(.5f,programmeProgress(Program("c","t","",1000,2000),1500),0f);assertEquals(0f,playbackFraction(-10,100),0f);assertEquals(1f,playbackFraction(110,100),0f)
 }

 @Test fun pauseDuringBufferingUsesPlayIntentRatherThanVisibleFrames(){
  var wants=true
  val player=java.lang.reflect.Proxy.newProxyInstance(androidx.media3.common.Player::class.java.classLoader,arrayOf(androidx.media3.common.Player::class.java)){_,method,_->when(method.name){"getPlayWhenReady"->wants;"isPlaying"->false;"pause"->{wants=false;null};"play"->{wants=true;null};else->null}}as androidx.media3.common.Player
  assertFalse(player.togglePlaybackIntent());assertFalse(wants);assertTrue(player.togglePlaybackIntent());assertTrue(wants)
 }

 @Test fun forcedVideoFallbackBudgetRespectsPixelsRateAndEncryption(){
  assertTrue(softwareVideoBudget("video/avc",1920,1080,30f,192,2,false))
  assertTrue(softwareVideoBudget("video/hevc",1920,1080,30f,192,4,false))
  assertFalse(softwareVideoBudget("video/hevc",1920,1080,60f,192,4,false))
  assertFalse(softwareVideoBudget("video/hevc",3840,2160,30f,512,8,false))
  assertFalse(softwareVideoBudget("video/avc",1920,1080,30f,192,4,true))
  assertFalse(softwareVideoBudget("video/av01",1920,1080,30f,192,4,false))
  assertTrue(softwareVideoBudget("video/av01",1920,1080,30f,256,8,false))
 }

}
