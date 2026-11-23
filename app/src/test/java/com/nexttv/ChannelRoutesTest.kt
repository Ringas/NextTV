package com.nexttv
import com.nexttv.core.data.*
import com.nexttv.core.data.Channel
import org.junit.Assert.*
import org.junit.Test
class ChannelRoutesTest {
 private fun c(id:String,name:String="News",tvg:String="news",url:String="https://example.test/$id",source:String="a")=Channel(id,name,tvg,"","新闻",url,source)
 @Test fun identityMergesRoutesInOrderWithoutChangingFirstId(){val a=c("first");val b=c("second");val m=mergeChannelRoutes(listOf(a,b)).single();assertEquals("first",m.id);assertEquals(listOf(a.url,b.url),m.playbackRoutes.map{it.url});assertEquals(m,mergeChannelRoutes(listOf(m)).single())}
 @Test fun distinctIdsNeverMergeBySharedName(){assertEquals(2,mergeChannelRoutes(listOf(c("a",tvg="east"),c("b",tvg="west"))).size)}
 @Test fun absentIdUsesNormalizedNameButPreservesQualityAndPlus(){assertEquals(1,mergeChannelRoutes(listOf(c("a",name=" News ",tvg=""),c("b",name="news",tvg=""))).size);assertEquals(3,mergeChannelRoutes(listOf(c("a",tvg=""),c("b",name="News4K",tvg=""),c("c",name="News+",tvg=""))).size)}
 @Test fun deduplicateAddressButPreserveSourceConfiguration(){val a=c("a");assertEquals(1,mergeChannelRoutes(listOf(a,a)).single().playbackRoutes.size);assertEquals(2,mergeChannelRoutes(listOf(a,c("b",url=a.url,source="other"))).size)}
 @Test fun crossSourceMergeUsesTvgIdAndPreservesEachRoute(){
  val first=c("first",tvg=" News ")
  val second=c("second",name="News Live",tvg="NEWS",url=first.url,source="b")
   .copy(headers=mapOf("Referer" to "https://example.test/"),catchup="append")
  assertEquals(2,mergeChannelRoutes(listOf(first,second)).size)
  val merged=mergeChannelRoutes(listOf(first,second),acrossSources=true).single()
  assertEquals(first.id,merged.id)
  assertEquals(listOf(first.playbackRoutes.single(),second.playbackRoutes.single()),merged.playbackRoutes)
 }
 @Test fun uniqueNameCanMatchMissingIdWithoutDroppingTheAlternateId(){
  val first=c("first",name=" News HD ",tvg="")
  val second=c("second",name="News HD",tvg="hd",source="b")
  val third=c("third",name="News 4K",tvg="",source="c")
  val merged=mergeChannelRoutes(listOf(first,second,third),acrossSources=true)
  assertEquals(listOf("first","third"),merged.map{it.id})
  assertEquals(listOf("","hd"),merged.first().playbackRoutes.map{it.tvgId})
 }
 @Test fun conflictingIdsBlockNameFallbackAndIdsRemainSourceScopedByDefault(){
  val east=c("east",tvg="east")
  val west=c("west",tvg="west",source="b")
  val missing=c("missing",tvg="",source="c")
  assertEquals(listOf("east","west","missing"),mergeChannelRoutes(listOf(east,west,missing),true).map{it.id})
  assertEquals(2,mergeChannelRoutes(listOf(east,missing.copy(sourceId="a")),true).size)
 }
 @Test fun nameOnlyMatchesAcrossSourcesButKeepsPlusAndResolutionSuffixes(){
  val channels=listOf(c("a",tvg=""),c("b",name=" NEWS ",tvg="",source="b"),
   c("plus",name="News+",tvg="",source="b"),c("uhd",name="News 4K",tvg="",source="b"))
  assertEquals(listOf("a","plus","uhd"),mergeChannelRoutes(channels,true).map{it.id})
 }
 @Test fun soleRouteRetriesExactlyOnce(){val r=RouteRecovery();r.reset(0);assertEquals(0,r.next(1,0));assertNull(r.next(1,0));r.reset(0);assertEquals(0,r.next(1,0))}
 @Test fun alternatesTryEachOnceAndManualChangeResets(){val r=RouteRecovery();r.reset(1);assertEquals(2,r.next(3,1));assertEquals(0,r.next(3,2));assertNull(r.next(3,0));r.reset(2);assertEquals(0,r.next(3,2))}
}
