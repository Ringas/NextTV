package com.nexttv.core.tv
import org.junit.Test
import org.junit.Assert.*
class OverlayGeometryTest {
 @Test fun guideAlwaysLeavesVideoVisible(){for(w in listOf(288f,608f,1106f,1248f,2528f)){val p=browserWidths(w,true);assertTrue(p.guideTotal<=w*.771f);assertTrue(p.channel>0&&p.programme>0&&p.date>0)}}
 @Test fun tvGuideUsesSeventyPercentWithWiderChannels(){assertEquals(1248f*.70f,browserWidths(1248f,true).guideTotal,.01f)}
 @Test fun channelHasReadableShareAcrossWindowSizes(){for(w in listOf(608f,960f,1248f,2528f)){val c=browserWidths(w,true).channel;assertTrue(c>=w*.319f&&c<=w*.351f)}}
 @Test fun logoToggleKeepsTextWidth(){for(w in listOf(608f,1248f)){val a=browserWidths(w,true);val b=browserWidths(w,false);assertEquals(a.logo+10,a.channel-b.channel,.01f);assertEquals(a.programme,b.programme,0f)}}
 @Test fun groupHasRoomForAllChannelsLabel(){for(w in listOf(608f,1106f,1248f)){assertTrue(browserWidths(w,true).group>=140f);assertTrue(browserWidths(w,true).group<=w*.24f)}}
 @Test fun largerFontGivesGroupsRoomWithoutFillingWindow(){for(w in listOf(608f,928f,1106f)){val normal=browserWidths(w,true);val large=browserWidths(w,true,1.3f);assertTrue(large.group>normal.group);assertTrue(large.group>=4*14*1.3f+84);assertTrue(large.group<=w*.30f)}}
 @Test fun bottomPanelAdaptsWithoutGrowingToFullWidth(){assertEquals(.78f,playerPanelFraction(1280f),.001f);assertTrue(playerPanelFraction(640f)>.78f);assertEquals(.82f,playerPanelFraction(320f),.001f)}
}
